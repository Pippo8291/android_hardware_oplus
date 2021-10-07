/*
 * Copyright (C) 2021 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioSystem
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.KeyEvent
import com.android.internal.os.DeviceKeyHandler

class KeyHandler(context: Context) : DeviceKeyHandler {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val packageContext = context.createPackageContext(
        KeyHandler::class.java.getPackage()!!.name, 0
    )
    private val sharedPreferences
        get() = packageContext.getSharedPreferences(
            packageContext.packageName + "_preferences",
            Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS
        )

    private var wasMuted = false
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val stream = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1)
            val state = intent.getBooleanExtra(AudioManager.EXTRA_STREAM_VOLUME_MUTED, false)
            if (stream == AudioSystem.STREAM_MUSIC && state == false) {
                wasMuted = false
            }
        }
    }

    init {
        context.registerReceiver(
            broadcastReceiver,
            IntentFilter(AudioManager.STREAM_MUTE_CHANGED_ACTION)
        )
    }

    override fun handleKeyEvent(event: KeyEvent): KeyEvent {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.scanCode) {
                POSITION_TOP -> {
                    handleMode(sharedPreferences.getString(ALERT_SLIDER_TOP_KEY, "0")!!.toInt())
                }
                POSITION_MIDDLE -> {
                    handleMode(sharedPreferences.getString(ALERT_SLIDER_MIDDLE_KEY, "1")!!.toInt())
                }
                POSITION_BOTTOM -> {
                    handleMode(sharedPreferences.getString(ALERT_SLIDER_BOTTOM_KEY, "2")!!.toInt())
                }
            }
        }
        return event
    }

    private fun vibrateIfNeeded(mode: Int) {
        when (mode) {
            AudioManager.RINGER_MODE_VIBRATE -> vibrator.vibrate(MODE_VIBRATION_EFFECT)
            AudioManager.RINGER_MODE_NORMAL -> vibrator.vibrate(MODE_NORMAL_EFFECT)
        }
    }

    private fun handleMode(mode: Int) {
        val muteMedia = sharedPreferences.getBoolean(MUTE_MEDIA_WITH_SILENT, false)

        when (mode) {
            AudioManager.RINGER_MODE_SILENT -> {
                notificationManager.setZenMode(Settings.Global.ZEN_MODE_OFF, null, TAG)
                audioManager.setRingerModeInternal(mode)
                if (muteMedia) {
                    audioManager.adjustVolume(AudioManager.ADJUST_MUTE, 0)
                    wasMuted = true
                }
            }
            AudioManager.RINGER_MODE_VIBRATE, AudioManager.RINGER_MODE_NORMAL -> {
                notificationManager.setZenMode(Settings.Global.ZEN_MODE_OFF, null, TAG)
                audioManager.setRingerModeInternal(mode)
                if (muteMedia && wasMuted) {
                    audioManager.adjustVolume(AudioManager.ADJUST_UNMUTE, 0)
                }
            }
            ZEN_PRIORITY_ONLY, ZEN_TOTAL_SILENCE, ZEN_ALARMS_ONLY -> {
                audioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL)
                notificationManager.setZenMode(mode - ZEN_OFFSET, null, TAG)
                if (muteMedia && wasMuted) {
                    audioManager.adjustVolume(AudioManager.ADJUST_UNMUTE, 0)
                }
            }
        }
        vibrateIfNeeded(mode)
    }

    companion object {
        private const val TAG = "KeyHandler"

        // Slider key codes
        private const val POSITION_TOP = 601
        private const val POSITION_MIDDLE = 602
        private const val POSITION_BOTTOM = 603

        // Preference keys
        private const val ALERT_SLIDER_TOP_KEY = "config_top_position"
        private const val ALERT_SLIDER_MIDDLE_KEY = "config_middle_position"
        private const val ALERT_SLIDER_BOTTOM_KEY = "config_bottom_position"
        private const val MUTE_MEDIA_WITH_SILENT = "config_mute_media"

        // ZEN constants
        private const val ZEN_OFFSET = 2
        private const val ZEN_PRIORITY_ONLY = 3
        private const val ZEN_TOTAL_SILENCE = 4
        private const val ZEN_ALARMS_ONLY = 5

        // Vibration effects
        private val MODE_NORMAL_EFFECT = VibrationEffect.get(VibrationEffect.EFFECT_HEAVY_CLICK)
        private val MODE_VIBRATION_EFFECT = VibrationEffect.get(VibrationEffect.EFFECT_DOUBLE_CLICK)
    }
}
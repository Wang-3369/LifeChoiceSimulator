package com.example.lifechoicesimulator.util

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.lifechoicesimulator.model.WorldBgm

class SoundManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentBgmWorldview: String = "main_menu"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // 👉 補上這兩個音量變數，ViewModel 才能把數值傳過來
    var bgmVolume: Float = 0.5f
        set(value) {
            field = value
            // 當音量改變時，立刻套用到正在播放的音樂上
            mediaPlayer?.setVolume(value, value)
        }

    var sfxVolume: Float = 0.5f

    var isBgmEnabled = true
        set(value) {
            field = value
            if (value) playBgm(currentBgmWorldview) else pauseBgm()
        }

    var isSfxEnabled = true
    var isVibrationEnabled = true

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // ===== 背景音樂 (BGM) 區塊 =====
    fun playBgm(worldview: String = currentBgmWorldview) {
        if (!isBgmEnabled) return

        if (mediaPlayer != null && currentBgmWorldview == worldview) {
            if (mediaPlayer?.isPlaying == false) mediaPlayer?.start()
            return
        }

        mediaPlayer?.stop()
        mediaPlayer?.release()

        currentBgmWorldview = worldview
        val bgmData = WorldBgm.fromWorldview(worldview)

        println("Now Playing: ${bgmData.title}")

        mediaPlayer = MediaPlayer.create(context, bgmData.resId)?.apply {
            isLooping = true
            // 👉 這裡就不會再報錯了，因為上面已經宣告了 bgmVolume
            setVolume(bgmVolume, bgmVolume)
            start()
        }
    }

    fun pauseBgm() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }

    fun stopBgm() {
        mediaPlayer?.run {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    // ===== 遊戲音效 (SFX) 區塊 =====
    fun playClickSound() {
        if (!isSfxEnabled) return
        // 👉 這裡也不會報錯了，系統點擊音效會套用 sfxVolume 的音量
        audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, sfxVolume)
    }

    // ===== 震動回饋 區塊 =====
    fun vibrate() {
        if (!isVibrationEnabled) return
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
    }

    fun release() {
        stopBgm()
    }
}

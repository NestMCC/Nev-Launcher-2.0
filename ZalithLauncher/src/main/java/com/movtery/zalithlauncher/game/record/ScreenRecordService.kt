/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.game.record

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioAttributes
import android.media.AudioRecord
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.notification.NOTIFICATION_ID_RECORD_SERVICE
import com.movtery.zalithlauncher.notification.NotificationChannelData
import com.movtery.zalithlauncher.notification.NotificationManager
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private const val TAG = "ScreenRecordService"

/** 录像保存到 Movies 下的子目录名 */
private const val FOLDER_NAME = "NEV Launcher"

/** 录制画面的最长边上限，超出时等比缩小，避免部分硬编码器不支持过大的分辨率 */
private const val MAX_LONG_SIDE = 1920

/**
 * 游戏录屏服务
 *
 * 通过 [MediaProjection] 捕获屏幕画面与游戏声音（Android 10+，可选叠加麦克风），
 * 由 [RecordingPipeline] 编码并合成 MP4，在 Android 10 及以上通过 MediaStore 保存到 `Movies/NEV Launcher`。
 * 支持暂停与继续，暂停期间的内容不会写入文件。
 *
 * Android 14 起 MediaProjection 必须在前台服务（类型 mediaProjection）内使用，
 * 并且授权结果只能使用一次，因此每次录制都需要重新向用户申请授权。
 *
 * 与游戏界面同处 `:game` 进程，状态通过 [ScreenRecordState] 共享。
 */
class ScreenRecordService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    private var projection: MediaProjection? = null
    private var pipeline: RecordingPipeline? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var output: OutputTarget? = null

    /** 系统（如状态栏的录屏胶囊）主动终止了投屏授权 */
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            finishRecording()
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationManager.createNotificationChannel(this, NotificationChannelData.RECORD_SERVICE_CHANNEL)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> {
                finishRecording()
                stopSelf()
            }
            ACTION_PAUSE -> setPaused(true)
            ACTION_RESUME -> setPaused(false)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        finishRecording()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun handleStart(intent: Intent) {
        //游戏声音（播放捕获）与麦克风都需要 RECORD_AUDIO 权限；播放捕获还要求 Android 10+
        val micGranted = hasMicPermission()
        var wantMic = intent.getBooleanExtra(EXTRA_RECORD_MIC, false) && micGranted
        var wantGame = intent.getBooleanExtra(EXTRA_RECORD_GAME_AUDIO, false) &&
                micGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        //必须尽快进入前台，否则系统会终止服务；并且 Android 14 要求先进入前台再获取 MediaProjection
        try {
            startForegroundWith(wantMic || wantGame)
        } catch (e: SecurityException) {
            if (!wantMic && !wantGame) return fail(e)
            //麦克风前台类型被拒绝时，退回仅录制画面
            Logger.warning(TAG, "Foreground service with microphone type was denied, recording without audio", e)
            wantMic = false
            wantGame = false
            try {
                startForegroundWith(false)
            } catch (e2: Exception) {
                return fail(e2)
            }
        } catch (e: Exception) {
            return fail(e)
        }

        if (ScreenRecordState.isRecording) return

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
        if (resultCode != Activity.RESULT_OK || data == null) {
            return fail(IllegalStateException("Screen capture permission was not granted"))
        }

        val bitrateMbps = intent.getIntExtra(EXTRA_BITRATE_MBPS, 12).coerceIn(1, 100)
        val fps = if (intent.getBooleanExtra(EXTRA_HIGH_FPS, false)) 60 else 30

        try {
            startRecording(resultCode, data, wantGame, wantMic, bitrateMbps * 1_000_000, fps)
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun startRecording(
        resultCode: Int,
        data: Intent,
        wantGame: Boolean,
        wantMic: Boolean,
        videoBitrate: Int,
        fps: Int
    ) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val mp = manager.getMediaProjection(resultCode, data)
            ?: throw IllegalStateException("MediaProjection is unavailable")
        projection = mp
        //Android 14 起，创建 VirtualDisplay 之前必须先注册回调
        mp.registerCallback(projectionCallback, handler)

        val (width, height, dpi) = captureSize()
        val target = createOutput()
        output = target

        var audio = createAudioSource(mp, wantGame, wantMic)
        var created: RecordingPipeline? = null
        var surface: Surface? = null
        while (created == null) {
            val muxer = try {
                createMuxer(target)
            } catch (e: Exception) {
                audio?.release()
                throw e
            }
            val candidate = RecordingPipeline(
                width, height, fps, videoBitrate, muxer, audio,
                originUs = System.nanoTime() / 1000
            ) { e -> handler.post { onPipelineError(e) } }
            try {
                surface = candidate.prepare()
                created = candidate
            } catch (e: Exception) {
                runCatching { muxer.release() }
                if (audio == null) throw e
                //音频编码器不可用等情况：退回仅录制画面
                Logger.warning(TAG, "Failed to prepare recording with audio, retrying without audio", e)
                audio.release()
                audio = null
            }
        }
        val readySurface = requireNotNull(surface)
        val ready = requireNotNull(created)
        pipeline = ready

        virtualDisplay = mp.createVirtualDisplay(
            "NevScreenRecord",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            readySurface,
            null,
            handler
        )

        ready.start()
        ScreenRecordState.markStarted(SystemClock.elapsedRealtime())
        Logger.info(
            TAG,
            "Recording started: ${width}x$height @${fps}fps, ${videoBitrate / 1_000_000}Mbps, " +
                    "audio=${audio != null} (game=$wantGame, mic=$wantMic)"
        )
    }

    /**
     * 暂停 / 继续录制。暂停期间的画面和声音都不会写入文件
     */
    private fun setPaused(paused: Boolean) {
        val p = pipeline ?: return
        val now = SystemClock.elapsedRealtime()
        if (paused) {
            p.pause()
            ScreenRecordState.markPaused(now)
        } else {
            p.resume()
            ScreenRecordState.markResumed(now)
        }
        refreshNotification()
    }

    private fun onPipelineError(e: Throwable) {
        if (pipeline == null) return
        Logger.error(TAG, "Recording stopped because of an error", e)
        //已经录到的内容仍会保存
        finishRecording()
        stopSelf()
    }

    /**
     * 结束录制并保存文件。可重复调用，已结束时无任何效果
     */
    private fun finishRecording() {
        val p = pipeline
        pipeline = null

        //先停止画面输入，再收尾编码，最后才终止投屏（否则播放捕获会提前报错）
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        var saved = false
        if (p != null) {
            saved = try {
                p.stop()
            } catch (e: Exception) {
                Logger.warning(TAG, "Failed to finish the recording", e)
                false
            }
        }

        projection?.let { mp ->
            runCatching { mp.unregisterCallback(projectionCallback) }
            runCatching { mp.stop() }
        }
        projection = null

        val target = output
        output = null
        if (target != null) {
            finalizeOutput(target, saved)
            if (p != null) {
                showToast(
                    if (saved) getString(R.string.record_toast_saved, target.locationLabel)
                    else getString(R.string.record_toast_too_short)
                )
            }
        }

        ScreenRecordState.markStopped()
    }

    private fun fail(e: Throwable) {
        Logger.error(TAG, "Failed to start screen recording", e)
        val target = output
        output = null
        pipeline?.let { runCatching { it.abort() } }
        pipeline = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        projection?.let { mp ->
            runCatching { mp.unregisterCallback(projectionCallback) }
            runCatching { mp.stop() }
        }
        projection = null
        if (target != null) finalizeOutput(target, false)

        ScreenRecordState.markStopped()
        showToast(getString(R.string.record_toast_failed, e.message ?: e.javaClass.simpleName))
        stopSelf()
    }

    private fun createMuxer(target: OutputTarget): MediaMuxer {
        val pfd = target.pfd
        return if (pfd != null) {
            MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } else {
            MediaMuxer(requireNotNull(target.file).absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }
    }

    /**
     * 创建音频输入：游戏声音（播放捕获）和/或麦克风。全部不可用时返回 null，仅录制画面
     */
    @SuppressLint("MissingPermission")
    private fun createAudioSource(mp: MediaProjection, wantGame: Boolean, wantMic: Boolean): PcmSource? {
        if (!hasMicPermission()) return null

        val bufferSize = (AudioRecord.getMinBufferSize(
            RECORD_SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
        ) * 4).coerceAtLeast(16_384)

        var game: AudioRecord? = null
        if (wantGame && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val record = runCatching {
                val config = AudioPlaybackCaptureConfiguration.Builder(mp)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    //只录本应用（游戏）的声音，不混入其他应用的通知音、音乐等
                    .addMatchingUid(Process.myUid())
                    .build()
                AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RECORD_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .build()
            }.onFailure {
                Logger.warning(TAG, "Failed to capture game audio", it)
            }.getOrNull()
            if (record != null) {
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    game = record
                } else {
                    Logger.warning(TAG, "Game audio capture is unavailable")
                    record.release()
                }
            }
        }

        var mic: AudioRecord? = null
        if (wantMic) {
            val record = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    RECORD_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }.onFailure {
                Logger.warning(TAG, "Failed to open the microphone", it)
            }.getOrNull()
            if (record != null) {
                if (record.state == AudioRecord.STATE_INITIALIZED) mic = record else record.release()
            }
        }

        if (game == null && mic == null) return null
        return PcmSource(game, mic)
    }

    /**
     * 计算录制尺寸：取真实屏幕大小，最长边超过 [MAX_LONG_SIDE] 时等比缩小，宽高均取偶数
     * @return 宽、高、屏幕密度
     */
    @Suppress("DEPRECATION")
    private fun captureSize(): Triple<Int, Int, Int> {
        val metrics = DisplayMetrics()
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        display.getRealMetrics(metrics)

        var width = metrics.widthPixels
        var height = metrics.heightPixels
        val longSide = max(width, height)
        if (longSide > MAX_LONG_SIDE) {
            val scale = MAX_LONG_SIDE.toFloat() / longSide
            width = (width * scale).toInt()
            height = (height * scale).toInt()
        }
        width = width and 1.inv()
        height = height and 1.inv()
        return Triple(width.coerceAtLeast(2), height.coerceAtLeast(2), metrics.densityDpi)
    }

    private class OutputTarget(
        /** Android 10 及以上：MediaStore 条目 */
        val uri: Uri?,
        val pfd: ParcelFileDescriptor?,
        /** Android 9 及以下：直接写入的文件 */
        val file: File?,
        /** 提示给用户的保存位置 */
        val locationLabel: String
    )

    private fun createOutput(): OutputTarget {
        val name = "NEV_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$FOLDER_NAME")
                //写入完成前对其他应用隐藏
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Failed to create a MediaStore entry")
            val pfd = try {
                contentResolver.openFileDescriptor(uri, "rw")
            } catch (e: Exception) {
                contentResolver.delete(uri, null, null)
                throw e
            }
            if (pfd == null) {
                contentResolver.delete(uri, null, null)
                throw IOException("Failed to open the MediaStore entry for writing")
            }
            return OutputTarget(uri, pfd, null, "${Environment.DIRECTORY_MOVIES}/$FOLDER_NAME")
        }

        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), FOLDER_NAME)
        val dir = if ((publicDir.isDirectory || publicDir.mkdirs()) && publicDir.canWrite()) {
            publicDir
        } else {
            //没有公共目录的写入权限时，退回应用专属目录
            File(getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir, FOLDER_NAME).also { it.mkdirs() }
        }
        return OutputTarget(null, null, File(dir, name), dir.absolutePath)
    }

    private fun finalizeOutput(target: OutputTarget, keep: Boolean) {
        runCatching { target.pfd?.close() }

        val uri = target.uri
        val file = target.file
        when {
            uri != null -> runCatching {
                if (keep) {
                    val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                    contentResolver.update(uri, values, null, null)
                } else {
                    contentResolver.delete(uri, null, null)
                }
            }.onFailure { Logger.warning(TAG, "Failed to finalize the MediaStore entry", it) }

            file != null -> {
                if (keep) {
                    MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("video/mp4"), null)
                } else {
                    file.delete()
                }
            }
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startForegroundWith(withMicrophone: Boolean) {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (withMicrophone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID_RECORD_SERVICE, notification, type)
        } else {
            startForeground(NOTIFICATION_ID_RECORD_SERVICE, notification)
        }
    }

    private fun buildNotification(): Notification {
        val state = ScreenRecordState.state.value
        val paused = state.paused
        val stopIntent = servicePendingIntent(ACTION_STOP, 0)
        val toggleIntent = servicePendingIntent(if (paused) ACTION_RESUME else ACTION_PAUSE, 1)
        //计时器基准：当前时刻减去已录制时长，暂停时不显示计时
        val elapsed = state.elapsedMs(SystemClock.elapsedRealtime())

        return NotificationCompat.Builder(this, NotificationChannelData.RECORD_SERVICE_CHANNEL.channelId)
            .setContentTitle(getString(R.string.notification_record_title))
            .setContentText(if (paused) getString(R.string.notification_record_paused) else null)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(!paused)
            .setWhen(System.currentTimeMillis() - elapsed)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                getString(if (paused) R.string.notification_record_resume else R.string.notification_record_pause),
                toggleIntent
            )
            .addAction(0, getString(R.string.notification_record_stop), stopIntent)
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, ScreenRecordService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun refreshNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID_RECORD_SERVICE, buildNotification())
    }

    private fun showToast(text: String) {
        handler.post {
            Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val ACTION_START = "com.movtery.zalithlauncher.record.START"
        private const val ACTION_STOP = "com.movtery.zalithlauncher.record.STOP"
        private const val ACTION_PAUSE = "com.movtery.zalithlauncher.record.PAUSE"
        private const val ACTION_RESUME = "com.movtery.zalithlauncher.record.RESUME"

        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val EXTRA_RECORD_MIC = "record_mic"
        private const val EXTRA_RECORD_GAME_AUDIO = "record_game_audio"
        private const val EXTRA_BITRATE_MBPS = "bitrate_mbps"
        private const val EXTRA_HIGH_FPS = "high_fps"

        /**
         * 开始录制
         * @param resultCode 屏幕捕获授权的 resultCode
         * @param data 屏幕捕获授权返回的 Intent
         * @param recordMic 是否录制麦克风（需要已经授予 RECORD_AUDIO）
         * @param recordGameAudio 是否录制游戏声音（Android 10+，需要已经授予 RECORD_AUDIO）
         */
        fun start(
            context: Context,
            resultCode: Int,
            data: Intent,
            recordMic: Boolean,
            recordGameAudio: Boolean,
            bitrateMbps: Int,
            highFps: Boolean
        ) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                putExtra(EXTRA_RECORD_MIC, recordMic)
                putExtra(EXTRA_RECORD_GAME_AUDIO, recordGameAudio)
                putExtra(EXTRA_BITRATE_MBPS, bitrateMbps)
                putExtra(EXTRA_HIGH_FPS, highFps)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * 暂停录制（已经暂停时无效果）
         */
        fun pause(context: Context) {
            context.startService(Intent(context, ScreenRecordService::class.java).setAction(ACTION_PAUSE))
        }

        /**
         * 继续录制
         */
        fun resume(context: Context) {
            context.startService(Intent(context, ScreenRecordService::class.java).setAction(ACTION_RESUME))
        }

        /**
         * 停止录制并保存文件
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenRecordService::class.java))
        }
    }
}

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
import android.os.SystemClock
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Display
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

private const val AUDIO_BITRATE = 128_000
private const val AUDIO_SAMPLE_RATE = 44_100

/**
 * 游戏录屏服务
 *
 * 通过 [MediaProjection] 捕获屏幕，使用 [MediaRecorder] 编码为 MP4，
 * 在 Android 10 及以上通过 MediaStore 保存到 `Movies/NEV Launcher`。
 *
 * Android 14 起 MediaProjection 必须在前台服务（类型 mediaProjection）内使用，
 * 并且授权结果只能使用一次，因此每次录制都需要重新向用户申请授权。
 *
 * 与游戏界面同处 `:game` 进程，状态通过 [ScreenRecordState] 共享。
 */
class ScreenRecordService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
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
        var recordAudio = intent.getBooleanExtra(EXTRA_RECORD_AUDIO, false) && hasMicPermission()

        //必须尽快进入前台，否则系统会终止服务；并且 Android 14 要求先进入前台再获取 MediaProjection
        try {
            startForegroundWith(recordAudio)
        } catch (e: SecurityException) {
            if (!recordAudio) return fail(e)
            //麦克风前台类型被拒绝时，退回仅录制画面
            Logger.warning(TAG, "Foreground service with microphone type was denied, recording without audio", e)
            recordAudio = false
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
            startRecording(resultCode, data, recordAudio, bitrateMbps * 1_000_000, fps)
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun startRecording(
        resultCode: Int,
        data: Intent,
        recordAudio: Boolean,
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

        val r = try {
            buildRecorder(target, width, height, videoBitrate, fps, recordAudio)
        } catch (e: Exception) {
            if (!recordAudio) throw e
            //设备没有可用的麦克风等情况：退回仅录制画面
            Logger.warning(TAG, "Failed to prepare recorder with audio, retrying without audio", e)
            buildRecorder(target, width, height, videoBitrate, fps, false)
        }
        recorder = r

        virtualDisplay = mp.createVirtualDisplay(
            "NevScreenRecord",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface,
            null,
            handler
        )

        r.start()
        ScreenRecordState.markStarted(SystemClock.elapsedRealtime())
        Logger.info(TAG, "Recording started: ${width}x$height @${fps}fps, ${videoBitrate / 1_000_000}Mbps, audio=$recordAudio")
    }

    /**
     * 结束录制并保存文件。可重复调用，已结束时无任何效果
     */
    private fun finishRecording() {
        val r = recorder
        recorder = null

        var saved = false
        if (r != null) {
            try {
                r.stop()
                saved = true
            } catch (e: RuntimeException) {
                //录制时间过短、没有任何有效画面时 stop 会抛异常，此时的文件不可用
                Logger.warning(TAG, "MediaRecorder.stop() failed, the recording is discarded", e)
            }
            runCatching { r.reset() }
            runCatching { r.release() }
        }

        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        projection?.let { mp ->
            runCatching { mp.unregisterCallback(projectionCallback) }
            runCatching { mp.stop() }
        }
        projection = null

        val target = output
        output = null
        if (target != null) {
            finalizeOutput(target, saved)
            if (r != null) {
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
        recorder?.let { r ->
            runCatching { r.reset() }
            runCatching { r.release() }
        }
        recorder = null
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

    private fun buildRecorder(
        target: OutputTarget,
        width: Int,
        height: Int,
        videoBitrate: Int,
        fps: Int,
        withAudio: Boolean
    ): MediaRecorder {
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            if (withAudio) r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            val pfd = target.pfd
            if (pfd != null) {
                r.setOutputFile(pfd.fileDescriptor)
            } else {
                r.setOutputFile(requireNotNull(target.file).absolutePath)
            }

            r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            r.setVideoSize(width, height)
            r.setVideoFrameRate(fps)
            r.setVideoEncodingBitRate(videoBitrate)
            if (withAudio) {
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioEncodingBitRate(AUDIO_BITRATE)
                r.setAudioSamplingRate(AUDIO_SAMPLE_RATE)
            }
            r.setOnErrorListener { _, what, extra ->
                Logger.error(TAG, "MediaRecorder error: what=$what, extra=$extra")
                handler.post {
                    finishRecording()
                    stopSelf()
                }
            }
            r.prepare()
        } catch (e: Exception) {
            runCatching { r.reset() }
            runCatching { r.release() }
            throw e
        }
        return r
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
                contentResolver.openFileDescriptor(uri, "w")
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
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationChannelData.RECORD_SERVICE_CHANNEL.channelId)
            .setContentTitle(getString(R.string.notification_record_title))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.notification_record_stop), stopIntent)
            .build()
    }

    private fun showToast(text: String) {
        handler.post {
            Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val ACTION_START = "com.movtery.zalithlauncher.record.START"
        private const val ACTION_STOP = "com.movtery.zalithlauncher.record.STOP"

        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val EXTRA_RECORD_AUDIO = "record_audio"
        private const val EXTRA_BITRATE_MBPS = "bitrate_mbps"
        private const val EXTRA_HIGH_FPS = "high_fps"

        /**
         * 开始录制
         * @param resultCode 屏幕捕获授权的 resultCode
         * @param data 屏幕捕获授权返回的 Intent
         * @param recordAudio 是否录制麦克风（需要已经授予 RECORD_AUDIO）
         */
        fun start(
            context: Context,
            resultCode: Int,
            data: Intent,
            recordAudio: Boolean,
            bitrateMbps: Int,
            highFps: Boolean
        ) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                putExtra(EXTRA_RECORD_AUDIO, recordAudio)
                putExtra(EXTRA_BITRATE_MBPS, bitrateMbps)
                putExtra(EXTRA_HIGH_FPS, highFps)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * 停止录制并保存文件
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenRecordService::class.java))
        }
    }
}

package com.movtery.zalithlauncher.game.record

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.notification.NOTIFICATION_ID_SCREEN_RECORD
import com.movtery.zalithlauncher.notification.NotificationChannelData
import com.movtery.zalithlauncher.utils.logging.Logger

private const val TAG = "ScreenRecordService"

/**
 * Nev Launcher: layanan latar depan untuk perekaman layar (berjalan di proses :game, sama dengan game).
 */
class ScreenRecordService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var recorder: ScreenRecorder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> recorder?.stop() ?: stopSelf()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        if (recorder != null) return

        val options = ScreenRecorder.Options(
            gameAudio = intent.getBooleanExtra(EXTRA_GAME_AUDIO, true),
            mic = intent.getBooleanExtra(EXTRA_MIC, false),
            noiseSuppress = intent.getBooleanExtra(EXTRA_NOISE_SUPPRESS, true)
        )

        //layanan dimulai lewat startForegroundService, jadi harus segera naik ke latar depan.
        //Android 14+: jenis mediaProjection harus aktif sebelum MediaProjection dibuat
        if (!startForegroundCompat(options.mic)) {
            toast(getString(R.string.record_failed, "foreground"))
            stopSelf()
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
        if (data == null) {
            cleanup()
            stopSelf()
            return
        }

        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mp = manager.getMediaProjection(resultCode, data)
                ?: throw IllegalStateException("MediaProjection null")
            projection = mp
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    //pengguna menghentikan berbagi layar lewat sistem
                    recorder?.stop()
                }
            }, mainHandler)

            val r = ScreenRecorder(this, mp, options) { location, error ->
                mainHandler.post { onRecorderFinished(location, error) }
            }
            recorder = r
            //bila gagal start, perekam memanggil onFinished sendiri (toast + cleanup)
            r.start()
            if (ScreenRecordState.recording.value) toast(getString(R.string.record_started))
        } catch (e: Throwable) {
            Logger.error(TAG, "Failed to begin screen recording", e)
            toast(getString(R.string.record_failed, e.message ?: e.javaClass.simpleName))
            cleanup()
            stopSelf()
        }
    }

    private fun onRecorderFinished(location: String?, error: Throwable?) {
        if (location != null) {
            toast(getString(R.string.record_saved, location), Toast.LENGTH_LONG)
        } else if (error != null) {
            toast(getString(R.string.record_failed, error.message ?: error.javaClass.simpleName), Toast.LENGTH_LONG)
        }
        cleanup()
        stopSelf()
    }

    private fun cleanup() {
        recorder = null
        try { projection?.stop() } catch (_: Throwable) {}
        projection = null
        ScreenRecordState.set(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startForegroundCompat(withMic: Boolean): Boolean {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, NotificationChannelData.SCREEN_RECORD_CHANNEL.channelId)
            .setContentTitle(getString(R.string.record_notification_title))
            .setContentText(getString(R.string.record_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.record_stop), stopIntent)
            .build()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (withMic && micGranted) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                startForeground(NOTIFICATION_ID_SCREEN_RECORD, notification, type)
            } else {
                startForeground(NOTIFICATION_ID_SCREEN_RECORD, notification)
            }
            true
        } catch (e: Throwable) {
            Logger.error(TAG, "Failed to start foreground", e)
            false
        }
    }

    private fun toast(text: String, duration: Int = Toast.LENGTH_SHORT) {
        mainHandler.post { Toast.makeText(applicationContext, text, duration).show() }
    }

    override fun onDestroy() {
        recorder?.stop()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.movtery.zalithlauncher.record.START"
        const val ACTION_STOP = "com.movtery.zalithlauncher.record.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_GAME_AUDIO = "game_audio"
        private const val EXTRA_MIC = "mic"
        private const val EXTRA_NOISE_SUPPRESS = "noise_suppress"

        fun start(context: Context, resultCode: Int, data: Intent, gameAudio: Boolean, mic: Boolean, noiseSuppress: Boolean) {
            val intent = Intent(context, ScreenRecordService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
                .putExtra(EXTRA_GAME_AUDIO, gameAudio)
                .putExtra(EXTRA_MIC, mic)
                .putExtra(EXTRA_NOISE_SUPPRESS, noiseSuppress)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ScreenRecordService::class.java).setAction(ACTION_STOP))
        }
    }
}

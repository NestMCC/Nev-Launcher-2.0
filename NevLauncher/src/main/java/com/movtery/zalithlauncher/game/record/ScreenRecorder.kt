package com.movtery.zalithlauncher.game.record

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import androidx.core.content.ContextCompat
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

private const val TAG = "ScreenRecorder"

/**
 * Nev Launcher: perekam layar (video H.264 + audio AAC, hasil MP4).
 *
 * - Video: isi layar lewat MediaProjection.
 * - Audio game: AudioPlaybackCapture (Android 10+), bersih dari suara sekitar karena diambil langsung dari keluaran audio.
 * - Mikrofon (opsional): sumber VOICE_COMMUNICATION + NoiseSuppressor + AEC + noise gate,
 *   dan memakai mic headset berkabel/USB bila tersambung, sehingga suara dari luar tidak ikut terekam.
 */
class ScreenRecorder(
    context: Context,
    private val projection: MediaProjection,
    private val options: Options,
    /** Dipanggil tepat sekali setelah perekaman selesai: (lokasi hasil, kesalahan). */
    private val onFinished: (location: String?, error: Throwable?) -> Unit
) {
    data class Options(
        val gameAudio: Boolean,
        val mic: Boolean,
        val noiseSuppress: Boolean
    )

    private val appContext = context.applicationContext
    private val stopRequested = AtomicBoolean(false)
    private val finished = AtomicBoolean(false)

    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var gameRecord: AudioRecord? = null
    private var micRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    private var muxer: MediaMuxer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var outputUri: Uri? = null
    private var outputFile: File? = null
    private var outputLabel: String = ""

    private val muxLock = Any()
    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    private var hasAudio = false
    private val pending = ArrayList<PendingSample>()

    private var videoThread: Thread? = null
    private var audioThread: Thread? = null

    private var startNanos = 0L
    private var videoBasePts = -1L
    private var videoOffsetUs = 0L
    private var lastVideoPts = -1L
    private var lastAudioPts = -1L

    private class PendingSample(val video: Boolean, val data: ByteArray, val pts: Long, val flags: Int)

    private fun elapsedUs(): Long = (System.nanoTime() - startNanos) / 1000L

    fun start() {
        try {
            startNanos = System.nanoTime()
            openOutput()
            setupAudio()
            setupVideo()

            videoThread = Thread({ drainVideoLoop() }, "NevRecord-Video").also { it.start() }
            if (hasAudio) {
                audioThread = Thread({ audioLoop() }, "NevRecord-Audio").also { it.start() }
            }
            ScreenRecordState.set(true)
        } catch (e: Throwable) {
            Logger.error(TAG, "Failed to start recording", e)
            releaseAll()
            discardOutput()
            finish(null, e)
        }
    }

    /** Minta perekaman dihentikan. Aman dipanggil berulang. */
    fun stop() {
        if (!stopRequested.compareAndSet(false, true)) return
        Thread({ shutdown() }, "NevRecord-Stop").start()
    }

    //region setup

    private fun openOutput() {
        val name = "NevLauncher_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/NevLauncher")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = appContext.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Gagal membuat berkas rekaman")
            outputUri = uri
            val descriptor = appContext.contentResolver.openFileDescriptor(uri, "rw")
                ?: throw IllegalStateException("Gagal membuka berkas rekaman")
            pfd = descriptor
            muxer = MediaMuxer(descriptor.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            outputLabel = "Movies/NevLauncher/$name"
        } else {
            val dir = File(appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "NevLauncher")
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Gagal membuat folder rekaman")
            val file = File(dir, name)
            outputFile = file
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            outputLabel = file.absolutePath
        }
    }

    @Suppress("DEPRECATION")
    private fun setupVideo() {
        val metrics = DisplayMetrics()
        val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        display.getRealMetrics(metrics)

        var width = metrics.widthPixels
        var height = metrics.heightPixels
        val longSide = maxOf(width, height)
        if (longSide > MAX_LONG_SIDE) {
            val scale = MAX_LONG_SIDE.toFloat() / longSide
            width = (width * scale).toInt()
            height = (height * scale).toInt()
        }
        //kebanyakan encoder perangkat keras meminta kelipatan 16
        width = (width / 16 * 16).coerceAtLeast(16)
        height = (height / 16 * 16).coerceAtLeast(16)

        val bitRate = (width.toLong() * height * 4).coerceIn(4_000_000L, 16_000_000L).toInt()
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoEncoder = encoder
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = encoder.createInputSurface()
        inputSurface = surface
        encoder.start()

        virtualDisplay = projection.createVirtualDisplay(
            "NevScreenRecord",
            width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, null
        )
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun setupAudio() {
        if (!hasAudioPermission()) return

        if (options.gameAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                gameRecord = createGameRecord()
            } catch (e: Throwable) {
                Logger.error(TAG, "Game audio capture unavailable", e)
                gameRecord = null
            }
        }
        if (options.mic) {
            try {
                micRecord = createMicRecord()
            } catch (e: Throwable) {
                Logger.error(TAG, "Microphone unavailable", e)
                micRecord = null
            }
        }
        hasAudio = gameRecord != null || micRecord != null
        if (!hasAudio) return

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAMES_PER_READ * 4 * 2)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        audioEncoder = encoder
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
    }

    @Suppress("MissingPermission")
    private fun createGameRecord(): AudioRecord {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuf, FRAMES_PER_READ * 4 * 4))
            .setAudioPlaybackCaptureConfig(config)
            .build()
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioPlaybackCapture tidak dapat diinisialisasi")
        }
        return record
    }

    @Suppress("MissingPermission")
    private fun createMicRecord(): AudioRecord {
        //VOICE_COMMUNICATION mengaktifkan pengolahan suara (peredam bising/gema) bawaan perangkat
        val source = if (options.noiseSuppress) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, FRAMES_PER_READ * 2 * 4)
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("Mikrofon tidak dapat diinisialisasi")
        }

        //jika headset (berkabel/USB) tersambung, pakai mic headset agar suara dari luar tidak terbawa
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val headset = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (headset != null) record.setPreferredDevice(headset)

        if (options.noiseSuppress) {
            val session = record.audioSessionId
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(session)?.also { it.enabled = true }
            }
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(session)?.also { it.enabled = true }
            }
        }
        return record
    }

    //endregion

    //region loops

    private fun audioLoop() {
        val encoder = audioEncoder ?: return
        val game = gameRecord
        val mic = micRecord
        val gate = if (mic != null && options.noiseSuppress) MicNoiseGate(SAMPLE_RATE) else null

        val gameBuf = ShortArray(FRAMES_PER_READ * 2)
        val micBuf = ShortArray(FRAMES_PER_READ)
        val out = ByteArray(FRAMES_PER_READ * 4)
        val info = MediaCodec.BufferInfo()
        var totalFrames = 0L
        var basePts = -1L

        try {
            game?.startRecording()
            mic?.startRecording()

            while (!stopRequested.get()) {
                var gameRead = 0
                if (game != null) {
                    val n = game.read(gameBuf, 0, gameBuf.size)
                    if (n < 0) break
                    gameRead = n / 2
                }
                var micRead = 0
                if (mic != null) {
                    val n = mic.read(micBuf, 0, micBuf.size)
                    if (n < 0) break
                    micRead = n
                    gate?.process(micBuf, micRead)
                }
                val frames = maxOf(gameRead, micRead)
                if (frames <= 0) continue

                if (basePts < 0) basePts = maxOf(0L, elapsedUs() - frames * 1_000_000L / SAMPLE_RATE)

                for (i in 0 until frames) {
                    val m = if (i < micRead) micBuf[i].toInt() else 0
                    val l = (if (i < gameRead) gameBuf[i * 2].toInt() else 0) + m
                    val r = (if (i < gameRead) gameBuf[i * 2 + 1].toInt() else 0) + m
                    val lc = l.coerceIn(-32768, 32767)
                    val rc = r.coerceIn(-32768, 32767)
                    out[i * 4] = (lc and 0xFF).toByte()
                    out[i * 4 + 1] = ((lc shr 8) and 0xFF).toByte()
                    out[i * 4 + 2] = (rc and 0xFF).toByte()
                    out[i * 4 + 3] = ((rc shr 8) and 0xFF).toByte()
                }

                val pts = basePts + totalFrames * 1_000_000L / SAMPLE_RATE
                feedAudio(encoder, out, frames * 4, pts)
                totalFrames += frames
                drainAudio(encoder, info, false)
            }
        } catch (e: Throwable) {
            Logger.error(TAG, "Audio loop failed", e)
        } finally {
            try {
                val endPts = (if (basePts < 0) elapsedUs() else basePts) + totalFrames * 1_000_000L / SAMPLE_RATE
                feedAudioEos(encoder, endPts)
                drainAudio(encoder, info, true)
            } catch (e: Throwable) {
                Logger.error(TAG, "Failed to flush audio", e)
            }
            try { game?.stop() } catch (_: Throwable) {}
            try { mic?.stop() } catch (_: Throwable) {}
            //jika jalur audio gagal sebelum sempat menghasilkan keluaran, lanjutkan tanpa audio agar video tidak tertahan
            synchronized(muxLock) {
                if (audioTrack < 0) {
                    hasAudio = false
                    tryStartMuxer()
                }
            }
        }
    }

    private fun feedAudio(encoder: MediaCodec, data: ByteArray, length: Int, pts: Long) {
        var offset = 0
        var chunkPts = pts
        while (offset < length) {
            val index = encoder.dequeueInputBuffer(10_000)
            if (index < 0) {
                if (stopRequested.get()) return
                continue
            }
            val buffer = encoder.getInputBuffer(index) ?: return
            buffer.clear()
            val n = min(buffer.remaining(), length - offset)
            buffer.put(data, offset, n)
            encoder.queueInputBuffer(index, 0, n, chunkPts, 0)
            chunkPts += (n / 4) * 1_000_000L / SAMPLE_RATE
            offset += n
        }
    }

    private fun feedAudioEos(encoder: MediaCodec, pts: Long) {
        repeat(50) {
            val index = encoder.dequeueInputBuffer(10_000)
            if (index >= 0) {
                encoder.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return
            }
        }
    }

    private fun drainAudio(encoder: MediaCodec, info: MediaCodec.BufferInfo, untilEos: Boolean) {
        var idle = 0
        while (true) {
            val index = encoder.dequeueOutputBuffer(info, if (untilEos) 10_000 else 0)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    synchronized(muxLock) {
                        val m = muxer ?: return
                        if (audioTrack < 0) audioTrack = m.addTrack(encoder.outputFormat)
                        tryStartMuxer()
                    }
                }
                index >= 0 -> {
                    val buffer = encoder.getOutputBuffer(index)
                    if (buffer != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size)
                        buffer.get(bytes)
                        var pts = info.presentationTimeUs
                        if (pts <= lastAudioPts) pts = lastAudioPts + 1
                        lastAudioPts = pts
                        writeSample(false, bytes, pts, info.flags)
                    }
                    val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    encoder.releaseOutputBuffer(index, false)
                    if (eos) return
                }
                else -> {
                    if (!untilEos) return
                    //tunggu sebentar sampai encoder selesai
                    if (++idle > 300) return
                }
            }
        }
    }

    private fun drainVideoLoop() {
        val encoder = videoEncoder ?: return
        val info = MediaCodec.BufferInfo()
        try {
            while (true) {
                val index = encoder.dequeueOutputBuffer(info, 10_000)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        synchronized(muxLock) {
                            val m = muxer ?: return
                            if (videoTrack < 0) videoTrack = m.addTrack(encoder.outputFormat)
                            tryStartMuxer()
                        }
                    }
                    index >= 0 -> {
                        val buffer = encoder.getOutputBuffer(index)
                        if (buffer != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            buffer.get(bytes)
                            if (videoBasePts < 0) {
                                videoBasePts = info.presentationTimeUs
                                videoOffsetUs = elapsedUs()
                            }
                            var pts = info.presentationTimeUs - videoBasePts + videoOffsetUs
                            if (pts <= lastVideoPts) pts = lastVideoPts + 1
                            lastVideoPts = pts
                            writeSample(true, bytes, pts, info.flags)
                        }
                        val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        encoder.releaseOutputBuffer(index, false)
                        if (eos) break
                    }
                    else -> { /* tidak ada keluaran, coba lagi */ }
                }
            }
        } catch (e: Throwable) {
            Logger.error(TAG, "Video loop failed", e)
        }
    }

    //endregion

    //region muxer

    /** Harus dipanggil di dalam muxLock. */
    private fun tryStartMuxer() {
        if (muxerStarted) return
        if (videoTrack < 0) return
        if (hasAudio && audioTrack < 0) return
        val m = muxer ?: return
        m.start()
        muxerStarted = true
        for (p in pending) {
            writeNow(m, p.video, p.data, p.pts, p.flags)
        }
        pending.clear()
    }

    private fun writeSample(video: Boolean, data: ByteArray, pts: Long, flags: Int) {
        synchronized(muxLock) {
            val m = muxer ?: return
            if (!muxerStarted) {
                //simpan dulu sampai semua jalur (video+audio) siap, supaya keyframe pertama tidak hilang
                if (pending.size < MAX_PENDING_SAMPLES) pending.add(PendingSample(video, data, pts, flags))
                return
            }
            writeNow(m, video, data, pts, flags)
        }
    }

    private fun writeNow(m: MediaMuxer, video: Boolean, data: ByteArray, pts: Long, flags: Int) {
        val track = if (video) videoTrack else audioTrack
        if (track < 0) return
        val info = MediaCodec.BufferInfo()
        info.set(0, data.size, pts, flags and MediaCodec.BUFFER_FLAG_KEY_FRAME)
        try {
            m.writeSampleData(track, java.nio.ByteBuffer.wrap(data), info)
        } catch (e: Throwable) {
            Logger.error(TAG, "writeSampleData failed", e)
        }
    }

    //endregion

    //region shutdown

    private fun shutdown() {
        var error: Throwable? = null
        try {
            try { virtualDisplay?.release() } catch (_: Throwable) {}
            virtualDisplay = null
            try { videoEncoder?.signalEndOfInputStream() } catch (_: Throwable) {}

            videoThread?.join(8000)
            audioThread?.join(8000)

            synchronized(muxLock) {
                val m = muxer
                if (m != null) {
                    if (muxerStarted) {
                        try { m.stop() } catch (e: Throwable) { error = e }
                    }
                }
            }
        } catch (e: Throwable) {
            error = e
        }

        val saved = muxerStarted && error == null
        releaseAll()
        if (saved) finalizeOutput() else discardOutput()
        ScreenRecordState.set(false)
        finish(if (saved) outputLabel else null, error)
    }

    private fun finalizeOutput() {
        val uri = outputUri ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                appContext.contentResolver.update(uri, values, null, null)
            } catch (e: Throwable) {
                Logger.error(TAG, "Failed to finalize recording", e)
            }
        }
    }

    private fun discardOutput() {
        try {
            outputUri?.let { appContext.contentResolver.delete(it, null, null) }
            outputFile?.delete()
        } catch (_: Throwable) {}
    }

    private fun releaseAll() {
        try { videoEncoder?.stop() } catch (_: Throwable) {}
        try { videoEncoder?.release() } catch (_: Throwable) {}
        videoEncoder = null
        try { audioEncoder?.stop() } catch (_: Throwable) {}
        try { audioEncoder?.release() } catch (_: Throwable) {}
        audioEncoder = null
        try { inputSurface?.release() } catch (_: Throwable) {}
        inputSurface = null
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        virtualDisplay = null
        try { noiseSuppressor?.release() } catch (_: Throwable) {}
        try { echoCanceler?.release() } catch (_: Throwable) {}
        try { gameRecord?.release() } catch (_: Throwable) {}
        try { micRecord?.release() } catch (_: Throwable) {}
        gameRecord = null
        micRecord = null
        synchronized(muxLock) {
            try { muxer?.release() } catch (_: Throwable) {}
            muxer = null
        }
        try { pfd?.close() } catch (_: Throwable) {}
        pfd = null
    }

    private fun finish(location: String?, error: Throwable?) {
        if (finished.compareAndSet(false, true)) {
            onFinished(location, error)
        }
    }

    //endregion

    private companion object {
        const val SAMPLE_RATE = 44100
        const val FRAMES_PER_READ = 1024
        const val FRAME_RATE = 30
        const val MAX_LONG_SIDE = 1920
        const val MAX_PENDING_SAMPLES = 600
    }
}

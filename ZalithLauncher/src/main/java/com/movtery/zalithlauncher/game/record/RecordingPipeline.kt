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

import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Bundle
import android.view.Surface
import com.movtery.zalithlauncher.utils.logging.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

private const val TAG = "RecordingPipeline"

const val RECORD_SAMPLE_RATE = 44_100
private const val AUDIO_BITRATE = 128_000
private const val DEQUEUE_TIMEOUT_US = 10_000L

/** 音频每次读取的采样数（双声道交错，即 1024 帧） */
private const val AUDIO_CHUNK_SHORTS = 2048

/** 等待另一条轨道就绪期间，最多缓存的样本数 */
private const val MAX_PENDING_SAMPLES = 600

/**
 * 音频输入：游戏声音（播放捕获）与麦克风的混合，输出 44.1kHz 双声道 16 位 PCM
 *
 * @param game 播放捕获（Android 10+），为 null 表示不录游戏声音
 * @param mic 麦克风（单声道），为 null 表示不录麦克风
 */
class PcmSource(
    private val game: AudioRecord?,
    private val mic: AudioRecord?
) {
    private val micBuffer = ShortArray(AUDIO_CHUNK_SHORTS / 2)

    fun start() {
        game?.startRecording()
        mic?.startRecording()
    }

    /**
     * 读取一块双声道交错的 PCM
     * @return 读到的 short 数量，小于等于 0 表示出错
     */
    fun read(out: ShortArray): Int {
        if (game != null) {
            val n = game.read(out, 0, out.size)
            if (n <= 0 || mic == null) return n
            //游戏声音是时钟基准，麦克风有多少混多少，不阻塞
            val frames = n / 2
            val m = mic.read(micBuffer, 0, frames, AudioRecord.READ_NON_BLOCKING)
            for (i in 0 until m.coerceAtLeast(0)) {
                val s = micBuffer[i].toInt()
                out[2 * i] = clip(out[2 * i] + s)
                out[2 * i + 1] = clip(out[2 * i + 1] + s)
            }
            return n
        }
        //仅麦克风：单声道复制为双声道
        val frames = out.size / 2
        val m = mic!!.read(micBuffer, 0, frames)
        if (m <= 0) return m
        for (i in 0 until m) {
            out[2 * i] = micBuffer[i]
            out[2 * i + 1] = micBuffer[i]
        }
        return m * 2
    }

    fun release() {
        runCatching { game?.stop() }
        runCatching { mic?.stop() }
        runCatching { game?.release() }
        runCatching { mic?.release() }
    }

    private fun clip(v: Int): Short = v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
}

/**
 * 录制管线：视频（Surface → H.264）与音频（PCM → AAC）分别编码，由 [MediaMuxer] 合成 MP4
 *
 * - 视频帧的时间戳来自画面本身，音频的时间戳由起点加采样数计算，二者同属单调时钟，因此音画同步
 * - 暂停通过 [PauseTimeline] 实现：暂停区间内的样本被丢弃，之后的样本前移，文件里没有空白
 * - 恢复后视频必须从关键帧开始写入，否则解码器缺少参考帧会花屏
 *
 * 使用顺序：[prepare] → 把返回的 Surface 交给 VirtualDisplay → [start] → [pause]/[resume] → [stop]
 */
class RecordingPipeline(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val videoBitrate: Int,
    private val muxer: MediaMuxer,
    private val audio: PcmSource?,
    private val originUs: Long,
    private val onFatalError: (Throwable) -> Unit
) {
    private val timeline = PauseTimeline(originUs)

    private lateinit var videoEncoder: MediaCodec
    private var audioEncoder: MediaCodec? = null

    private var videoThread: Thread? = null
    private var audioThread: Thread? = null

    @Volatile
    private var running = false

    //muxer 相关状态，统一由 muxerLock 保护
    private val muxerLock = Any()
    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    private var videoSamples = 0
    private val pending = ArrayList<PendingSample>()
    private var lastVideoPts = 0L
    private var lastAudioPts = 0L

    /** 恢复录制后，丢弃视频帧直到下一个关键帧 */
    @Volatile
    private var waitingKeyFrame = false

    private class PendingSample(val track: Int, val data: ByteBuffer, val info: MediaCodec.BufferInfo)

    val isPaused: Boolean get() = timeline.isPaused

    /**
     * 创建并启动编码器
     * @return 视频输入 Surface，交给 VirtualDisplay 渲染
     */
    fun prepare(): Surface {
        val vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            //画面静止时 VirtualDisplay 不会产生新帧，让编码器自动重复上一帧，避免恢复后迟迟没有关键帧
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000L)
        }
        videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val surface: Surface
        try {
            videoEncoder.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = videoEncoder.createInputSurface()
            videoEncoder.start()

            if (audio != null) {
                val af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RECORD_SAMPLE_RATE, 2).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AUDIO_CHUNK_SHORTS * 2)
                }
                audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).also {
                    it.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    it.start()
                }
            }
        } catch (e: Exception) {
            releaseCodecs()
            throw e
        }
        return surface
    }

    fun start() {
        running = true
        videoThread = Thread({ runCatchingFatal { videoLoop() } }, "Nev-Record-Video").also { it.start() }
        if (audio != null) {
            audio.start()
            audioThread = Thread({ runCatchingFatal { audioLoop() } }, "Nev-Record-Audio").also { it.start() }
        }
    }

    fun pause() {
        timeline.pause(nowUs())
    }

    fun resume() {
        if (!timeline.isPaused) return
        waitingKeyFrame = true
        timeline.resume(nowUs())
        runCatching {
            videoEncoder.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
        }
    }

    /**
     * 结束录制并写完文件
     * @return 文件是否有效（至少写入了一帧视频）
     */
    fun stop(): Boolean {
        running = false
        runCatching { videoEncoder.signalEndOfInputStream() }
        videoThread?.join(3000)
        audioThread?.join(3000)

        var valid = false
        synchronized(muxerLock) {
            valid = muxerStarted && videoSamples > 0
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (e: RuntimeException) {
                    Logger.warning(TAG, "MediaMuxer.stop() failed", e)
                    valid = false
                }
            }
            runCatching { muxer.release() }
        }
        releaseCodecs()
        audio?.release()
        return valid
    }

    /**
     * 出错时放弃录制，不写文件
     */
    fun abort() {
        running = false
        runCatching { videoThread?.join(500) }
        runCatching { audioThread?.join(500) }
        synchronized(muxerLock) { runCatching { muxer.release() } }
        releaseCodecs()
        audio?.release()
    }

    private fun releaseCodecs() {
        runCatching { videoEncoder.stop() }
        runCatching { videoEncoder.release() }
        audioEncoder?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
    }

    private inline fun runCatchingFatal(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (running) {
                Logger.error(TAG, "Recording pipeline crashed", e)
                onFatalError(e)
            }
        }
    }

    private fun nowUs(): Long = System.nanoTime() / 1000

    //——— 视频 ———

    private fun videoLoop() {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val index = videoEncoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    synchronized(muxerLock) {
                        videoTrack = muxer.addTrack(videoEncoder.outputFormat)
                        startMuxerIfReady()
                    }
                }
                index >= 0 -> {
                    val buffer = videoEncoder.getOutputBuffer(index)
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (buffer != null && info.size > 0 && !isConfig) {
                        writeVideo(buffer, info)
                    }
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    videoEncoder.releaseOutputBuffer(index, false)
                    if (eos) return
                }
                //INFO_TRY_AGAIN_LATER：已经收到停止信号却迟迟没有 EOS 时，不无限等待
                index == MediaCodec.INFO_TRY_AGAIN_LATER && !running && stopWaitedTooLong() -> return
            }
        }
    }

    private var stopRequestedAt = 0L
    private var videoClockShift: Long? = null

    private fun stopWaitedTooLong(): Boolean {
        if (stopRequestedAt == 0L) stopRequestedAt = System.nanoTime()
        return System.nanoTime() - stopRequestedAt > 2_000_000_000L
    }

    private fun writeVideo(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        val isKey = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        if (waitingKeyFrame) {
            if (!isKey) return
            waitingKeyFrame = false
        }
        //视频时间戳通常与 System.nanoTime 同一时钟；若设备用了别的时钟，则以第一帧为准对齐
        val shift = videoClockShift ?: (nowUs() - info.presentationTimeUs).let { d ->
            (if (abs(d) > 2_000_000L) d else 0L).also { videoClockShift = it }
        }
        val mapped = timeline.map(info.presentationTimeUs + shift)
        if (mapped < 0) return
        info.presentationTimeUs = mapped
        writeSample(isVideo = true, buffer, info)
    }

    //——— 音频 ———

    private fun audioLoop() {
        val source = audio ?: return
        val encoder = audioEncoder ?: return
        val pcm = ShortArray(AUDIO_CHUNK_SHORTS)
        val info = MediaCodec.BufferInfo()
        val baseUs = nowUs()
        var totalFrames = 0L

        while (running) {
            val n = source.read(pcm)
            if (n < 0) throw IllegalStateException("AudioRecord.read failed: $n")
            if (n == 0) continue

            val pts = baseUs + totalFrames * 1_000_000L / RECORD_SAMPLE_RATE
            totalFrames += n / 2
            val index = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (index >= 0) {
                val input = encoder.getInputBuffer(index)!!
                input.clear()
                input.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, 0, n)
                encoder.queueInputBuffer(index, 0, n * 2, pts, 0)
            }
            drainAudio(encoder, info)
        }

        //发送 EOS，并取走剩余的编码结果
        val eosPts = baseUs + totalFrames * 1_000_000L / RECORD_SAMPLE_RATE
        var queued = false
        var tries = 0
        while (!queued && tries++ < 50) {
            val index = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (index >= 0) {
                encoder.queueInputBuffer(index, 0, 0, eosPts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                queued = true
            }
            drainAudio(encoder, info)
        }
        var drains = 0
        while (queued && drains++ < 200 && !drainAudio(encoder, info)) {
            //等待 EOS
        }
    }

    /**
     * @return 是否已经读到 EOS
     */
    private fun drainAudio(encoder: MediaCodec, info: MediaCodec.BufferInfo): Boolean {
        while (true) {
            val index = encoder.dequeueOutputBuffer(info, 0)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    synchronized(muxerLock) {
                        audioTrack = muxer.addTrack(encoder.outputFormat)
                        startMuxerIfReady()
                    }
                }
                index >= 0 -> {
                    val buffer = encoder.getOutputBuffer(index)
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (buffer != null && info.size > 0 && !isConfig) {
                        val mapped = timeline.map(info.presentationTimeUs)
                        if (mapped >= 0) {
                            info.presentationTimeUs = mapped
                            writeSample(isVideo = false, buffer, info)
                        }
                    }
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(index, false)
                    if (eos) return true
                }
                else -> return false
            }
        }
    }

    //——— 合成 ———

    private fun startMuxerIfReady() {
        val ready = videoTrack >= 0 && (audio == null || audioTrack >= 0)
        if (!ready || muxerStarted) return
        muxer.start()
        muxerStarted = true
        for (s in pending) {
            muxer.writeSampleData(s.track, s.data, s.info)
            if (s.track == videoTrack) videoSamples++
        }
        pending.clear()
    }

    private fun writeSample(isVideo: Boolean, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        synchronized(muxerLock) {
            //时间戳必须逐轨递增
            if (isVideo) {
                if (info.presentationTimeUs < lastVideoPts) info.presentationTimeUs = lastVideoPts
                lastVideoPts = info.presentationTimeUs
            } else {
                if (info.presentationTimeUs < lastAudioPts) info.presentationTimeUs = lastAudioPts
                lastAudioPts = info.presentationTimeUs
            }
            val track = if (isVideo) videoTrack else audioTrack
            if (track < 0) return
            buffer.position(info.offset)
            buffer.limit(info.offset + info.size)

            if (muxerStarted) {
                muxer.writeSampleData(track, buffer, info)
                if (isVideo) videoSamples++
            } else if (pending.size < MAX_PENDING_SAMPLES) {
                //另一条轨道还没就绪：先缓存一份拷贝
                val copy = ByteBuffer.allocate(info.size).apply {
                    put(buffer)
                    flip()
                }
                val copyInfo = MediaCodec.BufferInfo().apply {
                    set(0, info.size, info.presentationTimeUs, info.flags)
                }
                pending.add(PendingSample(track, copy, copyInfo))
            }
        }
    }
}

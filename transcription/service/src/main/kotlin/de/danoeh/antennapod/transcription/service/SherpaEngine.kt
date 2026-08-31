package de.danoeh.antennapod.transcription.service

import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineQwen3AsrModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionModel
import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionModels
import java.io.Closeable

class SherpaEngine(
    private val model: TranscriptionModel,
    private val repository: ModelRepository,
    enableSpeakerLabels: Boolean,
) : Closeable {
    private val recognizer = OfflineRecognizer(config = recognizerConfig())
    private val diarizer = if (enableSpeakerLabels) createDiarizer() else null

    fun transcribeWindow(samples: FloatArray, offsetMs: Long): List<GeneratedTranscript.Segment> {
        val speakers = diarize(samples)
        val result = mutableListOf<GeneratedTranscript.Segment>()
        val chunkSamples = 30 * SAMPLE_RATE
        var start = 0
        while (start < samples.size) {
            val end = minOf(samples.size, start + chunkSamples)
            val chunk = samples.copyOfRange(start, end)
            val chunkStartMs = start * 1000L / SAMPLE_RATE
            val chunkDurationMs = chunk.size * 1000L / SAMPLE_RATE
            val relativeSpeakers = speakers.mapNotNull { interval ->
                val relativeStart = maxOf(0, interval.startTimeMs - chunkStartMs)
                val relativeEnd = minOf(chunkDurationMs, interval.endTimeMs - chunkStartMs)
                if (relativeEnd > relativeStart) {
                    SpeakerInterval(relativeStart, relativeEnd, interval.speaker)
                } else {
                    null
                }
            }
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(chunk, SAMPLE_RATE)
                recognizer.decode(stream)
                val decoded = recognizer.getResult(stream)
                result += TranscriptAssembler.assemble(
                    offsetMs + chunkStartMs,
                    chunkDurationMs,
                    decoded.text,
                    decoded.tokens,
                    decoded.timestamps,
                    relativeSpeakers,
                )
            } finally {
                stream.release()
            }
            start = end
        }
        return result
    }

    override fun close() {
        diarizer?.release()
        recognizer.release()
    }

    private fun diarize(samples: FloatArray): List<SpeakerInterval> {
        return diarizer?.process(samples)?.map {
            SpeakerInterval((it.start * 1000).toLong(), (it.end * 1000).toLong(), it.speaker)
        } ?: emptyList()
    }

    private fun recognizerConfig(): OfflineRecognizerConfig {
        val files = model.files.associateBy { it.relativePath }
        fun path(name: String): String = repository.getFile(model, files.getValue(name)).absolutePath
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val config = when (model.engine) {
            TranscriptionModel.Engine.WHISPER -> {
                val prefix = if (model.id.contains("tiny")) "tiny" else "base"
                OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = path("$prefix-encoder.int8.onnx"),
                        decoder = path("$prefix-decoder.int8.onnx"),
                        language = "",
                        task = "transcribe",
                        enableTokenTimestamps = true,
                        enableSegmentTimestamps = true,
                    ),
                    numThreads = threads,
                    modelType = "whisper",
                    tokens = path("$prefix-tokens.txt"),
                )
            }
            TranscriptionModel.Engine.SENSE_VOICE -> OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = path("model.int8.onnx"),
                    language = "auto",
                    useInverseTextNormalization = true,
                ),
                numThreads = threads,
                modelType = "sense_voice",
                tokens = path("tokens.txt"),
            )
            TranscriptionModel.Engine.QWEN3_ASR -> OfflineModelConfig(
                qwen3Asr = OfflineQwen3AsrModelConfig(
                    convFrontend = path("conv_frontend.onnx"),
                    encoder = path("encoder.int8.onnx"),
                    decoder = path("decoder.int8.onnx"),
                    tokenizer = repository.getFile(
                        model,
                        files.getValue("tokenizer/tokenizer_config.json"),
                    ).parentFile!!.absolutePath,
                    maxTotalLen = 1024,
                    maxNewTokens = 512,
                ),
                numThreads = threads,
                modelType = "qwen3_asr",
            )
            TranscriptionModel.Engine.SPEAKER_DIARIZATION -> error("Invalid ASR model")
        }
        return OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80, dither = 0.0f),
            modelConfig = config,
        )
    }

    private fun createDiarizer(): OfflineSpeakerDiarization? {
        val speakerModel = TranscriptionModels.getSpeakerDiarization()
        if (!repository.isInstalled(speakerModel)) {
            return null
        }
        val files = speakerModel.files.associateBy { it.relativePath }
        return OfflineSpeakerDiarization(
            config = OfflineSpeakerDiarizationConfig(
                segmentation = OfflineSpeakerSegmentationModelConfig(
                    pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                        model = repository.getFile(
                            speakerModel,
                            files.getValue("segmentation.int8.onnx"),
                        ).absolutePath,
                    ),
                    numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                ),
                embedding = SpeakerEmbeddingExtractorConfig(
                    model = repository.getFile(
                        speakerModel,
                        files.getValue("embedding.onnx"),
                    ).absolutePath,
                    numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                ),
                clustering = FastClusteringConfig(numClusters = -1, threshold = 0.5f),
            ),
        )
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}

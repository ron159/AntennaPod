package de.danoeh.antennapod.transcription.serviceinterface;

import java.util.List;

public final class TranscriptionModels {
    private static final String TINY_REVISION = "65176e2deb88badc814a94058666cadccc29b61c";
    private static final String BASE_REVISION = "bb53ee204431c90d314c1cc08d28d23e5b7927cc";
    private static final String SENSE_REVISION = "2365baeacb507f821a0c8120fcee3d484dba7a07";
    private static final String QWEN_REVISION = "68818b2313fe77bd06f6a7c5068ff3ef59d02b8a";
    private static final String DIARIZATION_REVISION = "9403a6902bb58e3d5ae8c7e77c3422de279db2e0";
    private static final List<TranscriptionModel> MODELS = List.of(
            whisperTiny(), whisperBase(), senseVoice(), qwen3Asr());

    private TranscriptionModels() {
    }

    public static List<TranscriptionModel> getAll() {
        return MODELS;
    }

    public static TranscriptionModel findById(String id) {
        for (TranscriptionModel model : MODELS) {
            if (model.getId().equals(id)) {
                return model;
            }
        }
        if (getSpeakerDiarization().getId().equals(id)) {
            return getSpeakerDiarization();
        }
        return null;
    }

    public static TranscriptionModel getSpeakerDiarization() {
        String segmentationRoot = "https://huggingface.co/csukuangfj/"
                + "sherpa-onnx-pyannote-segmentation-3-0/resolve/" + DIARIZATION_REVISION;
        String embeddingUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/"
                + "speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx";
        return new TranscriptionModel("speaker-diarization-int8", "Speaker labels",
                "Optional local speaker segmentation and clustering",
                TranscriptionModel.Engine.SPEAKER_DIARIZATION, List.of("language-independent"), List.of(
                    new TranscriptionModel.File("segmentation.int8.onnx", segmentationRoot + "/model.int8.onnx",
                            1_540_506L, "d582f4b4c6b48205de7e0643c57df0df5615a3c176189be3fc461e9d18827b5d"),
                    new TranscriptionModel.File("embedding.onnx", embeddingUrl, 39_593_761L,
                            "1a331345f04805badbb495c775a6ddffcdd1a732567d5ec8b3d5749e3c7a5e4b")),
                1_536, false, false);
    }

    private static TranscriptionModel whisperTiny() {
        String root = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/" + TINY_REVISION;
        return new TranscriptionModel("whisper-tiny-int8", "Whisper Tiny INT8",
                "Small multilingual model for lower-memory phones", TranscriptionModel.Engine.WHISPER,
                List.of("multilingual"), List.of(
                file("tiny-encoder.int8.onnx", root, 12_937_772L,
                        "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434"),
                file("tiny-decoder.int8.onnx", root, 89_855_401L,
                        "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925"),
                file("tiny-tokens.txt", root, 816_730L,
                        "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126")),
                1_024, false, true);
    }

    private static TranscriptionModel whisperBase() {
        String root = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/" + BASE_REVISION;
        return new TranscriptionModel("whisper-base-int8", "Whisper Base INT8",
                "Balanced multilingual model with improved accuracy", TranscriptionModel.Engine.WHISPER,
                List.of("multilingual"), List.of(
                file("base-encoder.int8.onnx", root, 29_120_534L,
                        "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11"),
                file("base-decoder.int8.onnx", root, 130_672_026L,
                        "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d"),
                file("base-tokens.txt", root, 816_730L,
                        "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126")),
                1_536, false, true);
    }

    private static TranscriptionModel senseVoice() {
        String root = "https://huggingface.co/csukuangfj/"
                + "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/" + SENSE_REVISION;
        return new TranscriptionModel("sense-voice-int8", "SenseVoice Small INT8",
                "Recommended for Mandarin, Cantonese, English, Japanese, and Korean",
                TranscriptionModel.Engine.SENSE_VOICE, List.of("zh", "yue", "en", "ja", "ko"), List.of(
                file("model.int8.onnx", root, 239_233_841L,
                        "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51"),
                file("tokens.txt", root, 315_894L,
                        "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc")),
                2_048, true, true);
    }

    private static TranscriptionModel qwen3Asr() {
        String root = "https://huggingface.co/csukuangfj2/"
                + "sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25/resolve/" + QWEN_REVISION;
        return new TranscriptionModel("qwen3-asr-0.6b-int8", "Qwen3-ASR 0.6B INT8",
                "Large multilingual model for recent high-memory phones", TranscriptionModel.Engine.QWEN3_ASR,
                List.of("multilingual"), List.of(
                file("conv_frontend.onnx", root, 44_148_281L,
                        "d22dc4423e0940e49884e903d2ea2f7e5567c14fc1aed97e4e26d6b8f208ef9e"),
                file("encoder.int8.onnx", root, 182_491_662L,
                        "60748d3e6744a57c9c91e1b17424a6c2990567e8adceb0783940c03ed98fa9d9"),
                file("decoder.int8.onnx", root, 755_914_231L,
                        "4f6885be5959ae26af3089d38ee7972c5fafbeeb1cf8d5e76eab6d8b61ca5771"),
                file("tokenizer/merges.txt", root, 1_671_853L,
                        "8831e4f1a044471340f7c0a83d7bd71306a5b867e95fd870f74d0c5308a904d5"),
                file("tokenizer/tokenizer_config.json", root, 12_487L,
                        "4942d005604266809309cabc9f4e9cb89ce855d59b14681fdc0e1cc62ea26c4c"),
                file("tokenizer/vocab.json", root, 2_776_833L,
                        "ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910")),
                4_096, false, false);
    }

    private static TranscriptionModel.File file(String path, String root, long size, String sha256) {
        return new TranscriptionModel.File(path, root + "/" + path, size, sha256);
    }
}

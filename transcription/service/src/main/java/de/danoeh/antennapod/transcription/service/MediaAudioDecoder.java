package de.danoeh.antennapod.transcription.service;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

final class MediaAudioDecoder {
    interface StopSignal {
        boolean isStopped();
    }

    private static final long TIMEOUT_US = 10_000;
    private static final int TARGET_RATE = 16_000;
    private static final int WINDOW_SECONDS = 300;

    private MediaAudioDecoder() {
    }

    static void decode(Context context, String source, PcmWindowCollector.Consumer consumer,
                       StopSignal stopSignal) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            setDataSource(extractor, context, source);
            int track = findAudioTrack(extractor);
            if (track < 0) {
                throw new IOException("The episode has no decodable audio track");
            }
            extractor.selectTrack(track);
            MediaFormat inputFormat = extractor.getTrackFormat(track);
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                throw new IOException("The audio track has no codec type");
            }
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(inputFormat, null, null, 0);
            codec.start();
            decodeLoop(extractor, codec, inputFormat, consumer, stopSignal);
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } finally {
                    codec.release();
                }
            }
            extractor.release();
        }
    }

    private static void decodeLoop(MediaExtractor extractor, MediaCodec codec, MediaFormat initialFormat,
                                   PcmWindowCollector.Consumer consumer, StopSignal stopSignal) throws IOException {
        PcmWindowCollector collector = new PcmWindowCollector(TARGET_RATE, WINDOW_SECONDS, consumer);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        MediaFormat outputFormat = initialFormat;
        boolean inputEnded = false;
        boolean outputEnded = false;
        while (!outputEnded) {
            if (stopSignal.isStopped()) {
                throw new IOException("Transcription was cancelled");
            }
            if (!inputEnded) {
                int inputIndex = codec.dequeueInputBuffer(TIMEOUT_US);
                if (inputIndex >= 0) {
                    ByteBuffer input = codec.getInputBuffer(inputIndex);
                    int size = input == null ? -1 : extractor.readSampleData(input, 0);
                    if (size < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputEnded = true;
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, size, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
            }
            int outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US);
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                outputFormat = codec.getOutputFormat();
            } else if (outputIndex >= 0) {
                ByteBuffer output = codec.getOutputBuffer(outputIndex);
                if (output != null && info.size > 0) {
                    output.position(info.offset);
                    output.limit(info.offset + info.size);
                    collector.accept(toMono(output.slice(), outputFormat), sampleRate(outputFormat));
                }
                outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(outputIndex, false);
            }
        }
        collector.finish();
    }

    private static float[] toMono(ByteBuffer buffer, MediaFormat format) throws IOException {
        int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
        int encoding = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && format.containsKey(MediaFormat.KEY_PCM_ENCODING)
                ? format.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int bytesPerSample;
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            bytesPerSample = 4;
        } else if (encoding == AudioFormat.ENCODING_PCM_8BIT) {
            bytesPerSample = 1;
        } else if (encoding == AudioFormat.ENCODING_PCM_16BIT) {
            bytesPerSample = 2;
        } else {
            throw new IOException(String.format(Locale.US, "Unsupported decoded PCM encoding %d", encoding));
        }
        int frames = buffer.remaining() / bytesPerSample / channels;
        float[] mono = new float[frames];
        for (int frame = 0; frame < frames; frame++) {
            float sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                    sum += buffer.getFloat();
                } else if (encoding == AudioFormat.ENCODING_PCM_8BIT) {
                    sum += (buffer.get() & 0xff) / 127.5f - 1.0f;
                } else {
                    sum += buffer.getShort() / 32768.0f;
                }
            }
            mono[frame] = sum / channels;
        }
        return mono;
    }

    private static int sampleRate(MediaFormat format) throws IOException {
        if (!format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            throw new IOException("The decoder did not report a sample rate");
        }
        return format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
    }

    private static int findAudioTrack(MediaExtractor extractor) {
        for (int index = 0; index < extractor.getTrackCount(); index++) {
            String mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return index;
            }
        }
        return -1;
    }

    private static void setDataSource(MediaExtractor extractor, Context context, String source) throws IOException {
        if (source.startsWith("content://") || source.startsWith("file://")) {
            extractor.setDataSource(context, Uri.parse(source), null);
        } else {
            extractor.setDataSource(source);
        }
    }
}

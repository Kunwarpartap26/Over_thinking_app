package com.tetra.app;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class WhisperService {

    private static final String TAG = "WhisperService";
    private static final int SAMPLE_RATE = 16000;
    private static final int RECORD_SECONDS = 30;
    private static final int BUFFER_SIZE = SAMPLE_RATE * RECORD_SECONDS;

    private Interpreter tflite;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private final Context context;
    private RecognitionCallback callback;

    public interface RecognitionCallback {
        void onPartialResult(String text);
        void onResult(String text);
        void onError(String error);
    }

    public WhisperService(Context context) {
        this.context = context;
    }

    public void initialize() {
        try {
            MappedByteBuffer modelBuffer = loadModelFile();
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2);
            tflite = new Interpreter(modelBuffer, options);
            Log.d(TAG, "Whisper model loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load Whisper: " + e.getMessage());
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        // Try loading from files dir first (if copied there)
        java.io.File modelFile = new java.io.File(
            context.getFilesDir(), "whisper-tiny.tflite");
        if (modelFile.exists()) {
            FileInputStream fis = new FileInputStream(modelFile);
            FileChannel channel = fis.getChannel();
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }
        // Fall back to assets
        return loadFromAssets();
    }

    private MappedByteBuffer loadFromAssets() throws IOException {
        android.content.res.AssetFileDescriptor afd =
            context.getAssets().openFd("whisper-tiny.tflite");
        FileInputStream fis = new FileInputStream(afd.getFileDescriptor());
        FileChannel channel = fis.getChannel();
        return channel.map(FileChannel.MapMode.READ_ONLY,
            afd.getStartOffset(), afd.getDeclaredLength());
    }

    public void startListening(RecognitionCallback cb) {
        this.callback = cb;
        if (tflite == null) {
            cb.onError("Whisper model not loaded");
            return;
        }

        int minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT);

        audioRecord = new AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 4);

        audioRecord.startRecording();
        isRecording = true;

        new Thread(() -> {
            short[] audioBuffer = new short[BUFFER_SIZE];
            int totalRead = 0;

            while (isRecording && totalRead < BUFFER_SIZE) {
                int read = audioRecord.read(audioBuffer, totalRead,
                    Math.min(minBufferSize, BUFFER_SIZE - totalRead));
                if (read > 0) totalRead += read;
            }

            if (totalRead > 0) {
                float[] floatAudio = shortToFloat(audioBuffer, totalRead);
                String result = runInference(floatAudio);
                if (callback != null) callback.onResult(result);
            }
        }).start();
    }

    public void stopListening() {
        isRecording = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    private float[] shortToFloat(short[] shorts, int length) {
        float[] floats = new float[length];
        for (int i = 0; i < length; i++) {
            floats[i] = shorts[i] / 32768.0f;
        }
        return floats;
    }

    private String runInference(float[] audioData) {
        try {
            // Prepare input - pad or trim to 30 seconds
            float[] input = new float[SAMPLE_RATE * 30];
            System.arraycopy(audioData, 0, input,
                0, Math.min(audioData.length, input.length));

            // Mel spectrogram input shape: [1, 80, 3000]
            float[][][] melInput = computeMelSpectrogram(input);

            // Output tokens
            int[][] outputTokens = new int[1][448];

            tflite.run(melInput, outputTokens);

            return decodeTokens(outputTokens[0]);
        } catch (Exception e) {
            Log.e(TAG, "Inference error: " + e.getMessage());
            return "";
        }
    }

    private float[][][] computeMelSpectrogram(float[] audio) {
        // Simplified mel spectrogram - 80 mel bins, 3000 frames
        float[][][] mel = new float[1][80][3000];
        // Basic energy computation per frame
        int frameSize = 400;
        int hopSize = 160;
        for (int frame = 0; frame < 3000; frame++) {
            int start = frame * hopSize;
            for (int bin = 0; bin < 80; bin++) {
                float energy = 0;
                for (int i = 0; i < frameSize && start + i < audio.length; i++) {
                    energy += audio[start + i] * audio[start + i];
                }
                mel[0][bin][frame] = (float) Math.log(Math.max(energy / frameSize, 1e-10));
            }
        }
        return mel;
    }

    private String decodeTokens(int[] tokens) {
        // Basic token to text - actual vocab decoding
        StringBuilder sb = new StringBuilder();
        for (int token : tokens) {
            if (token == 50256) break; // EOT token
            if (token > 0 && token < 50000) {
                // Simplified - in production use full vocab
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }

    public boolean isReady() {
        return tflite != null;
    }

    public void release() {
        stopListening();
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
    }
}

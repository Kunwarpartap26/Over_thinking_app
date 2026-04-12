package com.tetra.app;

import android.content.Context;
import android.util.Log;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import java.io.File;

public class GemmaService {

    private static final String TAG = "GemmaService";
    private static final int MAX_TOKENS = 300;
    private static final int TOP_K = 40;
    private static final float TEMPERATURE = 0.7f;

    private static final String SYSTEM_PROMPT =
        "You are TETRA, a truthful offline mentor. " +
        "Identify cognitive distortions and logical fallacies in the user's thoughts. " +
        "Apply Cognitive Reframing techniques. " +
        "Be concise, warm, and direct. " +
        "Always respond in Hinglish (mix of Hindi and English). " +
        "Never use cloud or external APIs. You run fully offline.";

    private LlmInference llmInference;
    private final Context context;

    public GemmaService(Context context) {
        this.context = context;
    }

    public static boolean isModelReady(Context context) {
        File modelFile = new File(context.getFilesDir(), "models/gemma.bin");
        return modelFile.exists() && modelFile.length() > 100_000_000L;
    }

    public static String getModelPath(Context context) {
        return new File(context.getFilesDir(), "models/gemma.bin").getAbsolutePath();
    }

    public void initialize() {
        try {
            String modelPath = getModelPath(context);
            LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .setTopK(TOP_K)
                .setTemperature(TEMPERATURE)
                .build();

            llmInference = LlmInference.createFromOptions(context, options);
            Log.d(TAG, "Gemma model loaded from: " + modelPath);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load Gemma: " + e.getMessage());
        }
    }

    public String getResponse(String userInput) {
        if (llmInference == null) {
            return "TETRA abhi available nahi hai. Model load nahi hua.";
        }
        try {
            String fullPrompt = SYSTEM_PROMPT + "\n\nUser: " + userInput + "\n\nTETRA:";
            return llmInference.generateResponse(fullPrompt);
        } catch (Exception e) {
            Log.e(TAG, "Inference error: " + e.getMessage());
            return "Kuch gadbad ho gayi. Dobara try karo.";
        }
    }

    public void releaseMemory() {
        if (llmInference != null) {
            try {
                llmInference.close();
                llmInference = null;
                System.gc();
                Log.d(TAG, "Gemma memory released.");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing memory: " + e.getMessage());
            }
        }
    }
}

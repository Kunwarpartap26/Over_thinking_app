package com.tetra.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import java.io.File;

public class GemmaService {

    private static final String TAG = "GemmaService";
    private static final int MAX_TOKENS = 300;
    private static final int TOP_K = 40;
    private static final float TEMPERATURE = 0.7f;

    private static final String PROMPT_ENGLISH =
        "You are TETRA, a compassionate offline therapist. " +
        "When user shares thoughts, follow these 3 steps:\n" +
        "1. VALIDATE: Acknowledge their feelings warmly\n" +
        "2. REFRAME: Gently identify any cognitive distortion\n" +
        "3. ACTION: Give one small actionable step\n" +
        "Always respond in clear, simple English. " +
        "Be warm, never judgmental. Keep response under 4 sentences.";

    private static final String PROMPT_HINDI =
        "Aap TETRA hain, ek dardmand offline therapist. " +
        "Jab user apne thoughts share kare, yeh 3 steps follow karo:\n" +
        "1. VALIDATE: Unki feelings ko pyaar se samjho\n" +
        "2. REFRAME: Dhire se koi cognitive distortion identify karo\n" +
        "3. ACTION: Ek chota sa actionable step do\n" +
        "Hamesha sirf shuddh Hindi mein jawab do. " +
        "Dost jaisa warm raho. Jawab 4 sentences se kam rakho.";

    private static final String PROMPT_HINGLISH =
        "You are TETRA, a compassionate offline therapist and truthful mentor. " +
        "When user shares thoughts, follow these 3 steps:\n" +
        "1. VALIDATE: Acknowledge their feelings warmly\n" +
        "2. REFRAME: Gently identify any cognitive distortion\n" +
        "3. ACTION: Give one small actionable step\n" +
        "Always respond in Hinglish (natural mix of Hindi and English). " +
        "Be warm, never judgmental. Keep response under 4 sentences.";

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

    private String getSystemPrompt() {
        SharedPreferences prefs = context.getSharedPreferences("tetra_prefs",
            Context.MODE_PRIVATE);
        String lang = prefs.getString("app_language", "hinglish");
        switch (lang) {
            case "english":  return PROMPT_ENGLISH;
            case "hindi":    return PROMPT_HINDI;
            default:         return PROMPT_HINGLISH;
        }
    }

    public void initialize() {
        try {
            String modelPath = getModelPath(context);
            LlmInference.LlmInferenceOptions options =
                LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(MAX_TOKENS)
                    .setTopK(TOP_K)
                    .setTemperature(TEMPERATURE)
                    .build();
            llmInference = LlmInference.createFromOptions(context, options);
            Log.d(TAG, "Gemma loaded.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load Gemma: " + e.getMessage());
        }
    }

    public String getResponse(String userInput) {
        if (llmInference == null) return getFallback();
        try {
            String prompt = getSystemPrompt() + "\n\nUser: " + userInput + "\n\nTETRA:";
            return llmInference.generateResponse(prompt);
        } catch (Exception e) {
            Log.e(TAG, "Inference error: " + e.getMessage());
            return getFallback();
        }
    }

    private String getFallback() {
        SharedPreferences prefs = context.getSharedPreferences("tetra_prefs",
            Context.MODE_PRIVATE);
        String lang = prefs.getString("app_language", "hinglish");
        switch (lang) {
            case "english": return "TETRA is not available right now. Please wait.";
            case "hindi":   return "TETRA abhi uplabdh nahi hai. Thoda intezaar karo.";
            default:        return "TETRA abhi load nahi hua. Thoda wait karo.";
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

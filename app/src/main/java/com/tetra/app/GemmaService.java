package com.tetra.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import java.io.File;

public class GemmaService {

    private static final String TAG = "GemmaService";
    private static final int MAX_TOKENS = 100;
    private static final int TOP_K = 20;
    private static final float TEMPERATURE = 0.7f;

    private static final String PROMPT_ENGLISH =
        "You are TETRA, a warm offline therapist for Indian users. " +
        "Follow 3 steps: 1.VALIDATE feelings 2.REFRAME gently 3.ACTION one small step. " +
        "Under 3 sentences. Simple English. " +
        "CRISIS: If suicide/self-harm mentioned say: 'Please call VANDREVALA: 9999 666 555 or KIRAN: 1800-599-0019'\n";

    private static final String PROMPT_HINDI =
        "Aap TETRA hain, ek warm offline therapist Indian users ke liye. " +
        "3 steps follow karo: 1.VALIDATE feelings 2.REFRAME dheere se 3.ACTION ek chota step. " +
        "3 sentences se kam. Simple Hindi. " +
        "CRISIS: Agar suicide/self-harm ka zikr ho: 'Please call VANDREVALA: 9999 666 555 ya KIRAN: 1800-599-0019'\n";

    private static final String PROMPT_HINGLISH =
        "You are TETRA, ek warm offline therapist for Indian users. " +
        "3 steps follow karo: 1.VALIDATE feelings 2.REFRAME gently 3.ACTION ek chota step. " +
        "3 sentences se kam. Natural Hinglish mein. " +
        "CRISIS: Agar suicide/self-harm mention ho: 'Please call VANDREVALA: 9999 666 555 ya KIRAN: 1800-599-0019'\n";

    private LlmInference llmInference;
    private final Context context;
    private boolean isLoading = false;
    private boolean isLoaded = false;
    private int messageCount = 0;

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
            case "english": return PROMPT_ENGLISH;
            case "hindi":   return PROMPT_HINDI;
            default:        return PROMPT_HINGLISH;
        }
    }

    // Call this from background thread only!
    public void initialize() {
        if (isLoaded || isLoading) return;
        if (!isModelReady(context)) {
            Log.w(TAG, "Model not ready yet");
            return;
        }
        isLoading = true;
        try {
            System.gc(); // Free memory before loading
            Thread.sleep(500); // Give GC time to work

            String modelPath = getModelPath(context);
            LlmInference.LlmInferenceOptions options =
                LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(MAX_TOKENS)
                    .setTopK(TOP_K)
                    .setTemperature(TEMPERATURE)
                    .build();
            llmInference = LlmInference.createFromOptions(context, options);
            isLoaded = true;
            Log.d(TAG, "Gemma loaded successfully");
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "OOM loading Gemma: " + e.getMessage());
            isLoaded = false;
            llmInference = null;
            System.gc();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load Gemma: " + e.getMessage());
            isLoaded = false;
        } finally {
            isLoading = false;
        }
    }

    public boolean isReady() { return isLoaded && llmInference != null; }
    public boolean isLoading() { return isLoading; }

    public String getResponse(String userInput) {
        if (!isReady()) return getFallback();
        try {
            messageCount++;
            // Reload every 8 messages to prevent memory buildup
            if (messageCount % 8 == 0) {
                releaseMemory();
                initialize();
            }
            String prompt = getSystemPrompt() +
                "User: " + userInput + "\nTETRA:";
            return llmInference.generateResponse(prompt);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "OOM during inference");
            releaseMemory();
            System.gc();
            return getFallback();
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
            case "english": return "I hear you. Can you tell me more about how you're feeling?";
            case "hindi":   return "Main sun raha hoon. Thoda aur batao kya feel kar rahe ho?";
            default:        return "Main sun raha hoon. Thoda aur batao kya chal raha hai?";
        }
    }

    public void releaseMemory() {
        if (llmInference != null) {
            try {
                llmInference.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing: " + e.getMessage());
            }
        }
        llmInference = null;
        isLoaded = false;
        messageCount = 0;
        System.gc();
        Log.d(TAG, "Gemma memory released");
    }
}

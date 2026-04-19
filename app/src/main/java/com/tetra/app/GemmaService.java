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
        "3 steps: VALIDATE feelings → REFRAME gently → ACTION one small step. " +
        "Max 3 sentences. Simple English. " +
        "CRISIS: If suicide/self-harm → say: 'Please call VANDREVALA: 9999 666 555 or KIRAN: 1800-599-0019'\n";

    private static final String PROMPT_HINDI =
        "Aap TETRA hain, ek warm offline therapist. " +
        "3 steps: VALIDATE feelings → REFRAME dheere → ACTION ek chota step. " +
        "Max 3 sentences. Simple Hindi. " +
        "CRISIS: Suicide/self-harm mention ho → 'Please call VANDREVALA: 9999 666 555 ya KIRAN: 1800-599-0019'\n";

    private static final String PROMPT_HINGLISH =
        "You are TETRA, ek warm offline therapist for Indian users. " +
        "3 steps: VALIDATE feelings → REFRAME gently → ACTION ek chota step. " +
        "Max 3 sentences. Natural Hinglish. " +
        "CRISIS: Suicide/self-harm mention ho → 'Please call VANDREVALA: 9999 666 555 ya KIRAN: 1800-599-0019'\n";

    private LlmInference llmInference;
    private final Context context;
    private boolean isLoading = false;
    private boolean isLoaded = false;
    private int messageCount = 0;

    public GemmaService(Context context) {
        this.context = context;
    }

    public static boolean isModelReady(Context context) {
        File modelFile = new File(context.getFilesDir(), "models/gemma.task");
        boolean exists = modelFile.exists() && modelFile.length() > 10_000_000L;
        Log.d("GemmaService", "Model check: " + modelFile.getAbsolutePath() +
            " exists=" + exists + " size=" + modelFile.length());
        return exists;
    }

    public static String getModelPath(Context context) {
        return new File(context.getFilesDir(), "models/gemma.task").getAbsolutePath();
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

    public void initialize() {
        if (isLoaded || isLoading) return;
        if (!isModelReady(context)) {
            Log.w(TAG, "Model not ready");
            return;
        }
        isLoading = true;
        try {
            System.gc();
            Thread.sleep(500);
            String modelPath = getModelPath(context);
            Log.d(TAG, "Loading model from: " + modelPath);
            LlmInference.LlmInferenceOptions options =
                LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .build();
            llmInference = LlmInference.createFromOptions(context, options);
            isLoaded = true;
            Log.d(TAG, "Gemma loaded successfully!");
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "OOM: " + e.getMessage());
            isLoaded = false;
            llmInference = null;
            System.gc();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load: " + e.getMessage());
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
            if (messageCount % 8 == 0) {
                releaseMemory();
                initialize();
            }
            String prompt = getSystemPrompt() +
                "User: " + userInput + "\nTETRA:";
            return llmInference.generateResponse(prompt);
        } catch (Exception e) {
            Log.e(TAG, "Inference error: " + e.getMessage());
            releaseMemory();
            return getFallback();
        }
    }

    private String getFallback() {
        SharedPreferences prefs = context.getSharedPreferences("tetra_prefs",
            Context.MODE_PRIVATE);
        String lang = prefs.getString("app_language", "hinglish");
        switch (lang) {
            case "english": return "I hear you. Tell me more about how you feel?";
            case "hindi":   return "Main sun raha hoon. Thoda aur batao?";
            default:        return "Main sun raha hoon. Thoda aur batao kya chal raha hai?";
        }
    }

    public void releaseMemory() {
        if (llmInference != null) {
            try { llmInference.close(); } catch (Exception ignored) {}
        }
        llmInference = null;
        isLoaded = false;
        messageCount = 0;
        System.gc();
        Log.d(TAG, "Memory released");
    }
}

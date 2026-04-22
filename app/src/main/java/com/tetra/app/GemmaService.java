package com.tetra.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import java.io.File;
import java.util.Arrays;
import java.util.List;

public class GemmaService {

    private static final String TAG = "GemmaService";
    private static final int MAX_TOKENS = 150;
    private static final int TOP_K = 20;
    private static final float TEMPERATURE = 0.7f;

    // ── Correct Gemma IT chat template format ──────────────────────────────
    // Tiny system prompts — 270M can follow simple role instructions reliably
    private static final String ROLE_ENGLISH =
        "You are TETRA, a warm therapist. Validate feelings, reframe gently, " +
        "suggest one small action. Max 3 sentences. Simple English.";

    private static final String ROLE_HINDI =
        "Aap TETRA hain, ek warm therapist. Feelings validate karo, gently reframe karo, " +
        "ek chota action suggest karo. Max 3 sentences. Simple Hindi.";

    private static final String ROLE_HINGLISH =
        "You are TETRA, ek warm therapist. Feelings validate karo, gently reframe karo, " +
        "ek chota action suggest karo. Max 3 sentences. Natural Hinglish.";

    // ── Java-side crisis keywords (model never handles these) ──────────────
    private static final List<String> CRISIS_KEYWORDS = Arrays.asList(
        "suicide", "suicidal", "kill myself", "end my life", "want to die",
        "marna chahta", "marna chahti", "mar jaunga", "mar jaungi",
        "khatam kar loon", "jina nahi", "jeena nahi", "self harm",
        "cut myself", "hurt myself", "khud ko hurt"
    );

    // ── Java-side panic keywords ───────────────────────────────────────────
    private static final List<String> PANIC_KEYWORDS = Arrays.asList(
        "panic attack", "anxiety attack", "can't breathe", "cant breathe",
        "saans nahi", "ghabra raha", "ghabra rahi", "dil tez",
        "bahut darr", "bahut dar", "help me breathe"
    );

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
        return modelFile.exists() && modelFile.length() > 100_000_000L;
    }

    public static String getModelPath(Context context) {
        return new File(context.getFilesDir(), "models/gemma.task").getAbsolutePath();
    }

    private String getLang() {
        SharedPreferences prefs = context.getSharedPreferences("tetra_prefs",
            Context.MODE_PRIVATE);
        return prefs.getString("app_language", "english");
    }

    private String getSystemRole() {
        switch (getLang()) {
            case "english":  return ROLE_ENGLISH;
            case "hindi":    return ROLE_HINDI;
            default:         return ROLE_HINGLISH;
        }
    }

    // Call from background thread only
    public void initialize() {
        if (isLoaded || isLoading) return;
        if (!isModelReady(context)) {
            Log.w(TAG, "Model not ready");
            return;
        }
        isLoading = true;
        try {
            System.gc();
            Thread.sleep(300);
            LlmInference.LlmInferenceOptions options =
                LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(getModelPath(context))
                    .setMaxTokens(MAX_TOKENS)
                    .build();
            llmInference = LlmInference.createFromOptions(context, options);
            isLoaded = true;
            Log.d(TAG, "Gemma loaded");
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "OOM: " + e.getMessage());
            isLoaded = false;
            llmInference = null;
            System.gc();
        } catch (Exception e) {
            Log.e(TAG, "Load failed: " + e.getMessage());
            isLoaded = false;
        } finally {
            isLoading = false;
        }
    }

    public boolean isReady()   { return isLoaded && llmInference != null; }
    public boolean isLoading() { return isLoading; }

    public String getResponse(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) return getFallback();

        String lower = userInput.toLowerCase();

        // ── Crisis check — hardcoded, never touches model ──────────────────
        for (String kw : CRISIS_KEYWORDS) {
            if (lower.contains(kw)) {
                return getCrisisResponse();
            }
        }

        // ── Panic check — hardcoded breathing guide ────────────────────────
        for (String kw : PANIC_KEYWORDS) {
            if (lower.contains(kw)) {
                return getPanicResponse();
            }
        }

        // ── Normal response — model handles it ────────────────────────────
        if (!isReady()) return getFallback();

        try {
            messageCount++;
            // Reload every 10 messages to prevent memory buildup
            if (messageCount % 10 == 0) {
                releaseMemory();
                initialize();
            }

            // Correct Gemma IT chat template format
            // This stops the model from echoing the prompt
            String prompt =
                "<start_of_turn>user\n" +
                getSystemRole() + "\n\n" +
                userInput.trim() + "<end_of_turn>\n" +
                "<start_of_turn>model\n";

            Log.d(TAG, "Prompt sent: " + prompt);
            String raw = llmInference.generateResponse(prompt);
            Log.d(TAG, "Raw response: " + raw);

            return cleanResponse(raw);

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

    // Strip any leaked prompt tokens from the response
    private String cleanResponse(String raw) {
        if (raw == null) return getFallback();
        // Remove any <end_of_turn> or <start_of_turn> tokens that leaked
        String cleaned = raw
            .replace("<end_of_turn>", "")
            .replace("<start_of_turn>", "")
            .replace("model\n", "")
            .replace("user\n", "")
            .trim();
        // If model echoed the role instruction, strip everything before first newline
        if (cleaned.contains(ROLE_ENGLISH) || cleaned.contains("TETRA, ek warm") ||
                cleaned.contains("TETRA, a warm")) {
            int idx = cleaned.lastIndexOf('\n');
            if (idx > 0 && idx < cleaned.length() - 1) {
                cleaned = cleaned.substring(idx + 1).trim();
            }
        }
        return cleaned.isEmpty() ? getFallback() : cleaned;
    }

    private String getCrisisResponse() {
        switch (getLang()) {
            case "english":
                return "I hear how much pain you're in right now. " +
                       "Please call VANDREVALA FOUNDATION: 9999 666 555 " +
                       "or KIRAN: 1800-599-0019 — they're available 24/7 and free.";
            case "hindi":
                return "Main samajh sakta hoon aap abhi kitne dard mein hain. " +
                       "Please abhi call karein — VANDREVALA: 9999 666 555 " +
                       "ya KIRAN: 1800-599-0019. Yeh free hai, 24 ghante available hai.";
            default:
                return "Main samajh sakta hoon aap bahut dard mein hain abhi. " +
                       "Please call karo — VANDREVALA: 9999 666 555 " +
                       "ya KIRAN: 1800-599-0019. Free hai, 24/7 available hai.";
        }
    }

    private String getPanicResponse() {
        switch (getLang()) {
            case "english":
                return "You are safe. Breathe with me: inhale 4 seconds, " +
                       "hold 4 seconds, exhale 6 seconds. " +
                       "Now name 3 things you can see around you.";
            case "hindi":
                return "Aap safe hain. Mere saath saans lo: 4 second andar, " +
                       "4 second roko, 6 second bahar. " +
                       "Ab aas-paas ki 3 cheezein batao jo dikh rahi hain.";
            default:
                return "Aap safe ho. Mere saath breathe karo: 4 second inhale, " +
                       "4 second hold, 6 second exhale. " +
                       "Ab 3 cheezein batao jo tumhare aas-paas dikh rahi hain.";
        }
    }

    private String getFallback() {
        switch (getLang()) {
            case "english": return "I hear you. Tell me more about what you're feeling.";
            case "hindi":   return "Main sun raha hoon. Aur batao kya feel ho raha hai.";
            default:        return "Main sun raha hoon. Thoda aur batao kya chal raha hai.";
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

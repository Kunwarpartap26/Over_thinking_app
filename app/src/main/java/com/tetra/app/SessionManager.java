package com.tetra.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.util.List;

public class SessionManager {

    private static final String TAG = "SessionManager";
    private final Context context;
    private final GemmaService gemmaService;
    private final PDFJournalService pdfJournalService;
    private final AutoSaveManager autoSaveManager;
    private boolean gemmaInitialized = false;

    public SessionManager(Context context) {
        this.context = context;
        this.gemmaService = new GemmaService(context);
        this.pdfJournalService = new PDFJournalService(context);
        this.autoSaveManager = new AutoSaveManager(context);
        // DO NOT initialize Gemma here - lazy load on first message
    }

    // Call from background thread
    public void initializeGemmaInBackground() {
        if (!gemmaInitialized) {
            gemmaService.initialize();
            gemmaInitialized = true;
        }
    }

    public boolean isGemmaReady() {
        return gemmaService.isReady();
    }

    public boolean isGemmaLoading() {
        return gemmaService.isLoading();
    }

    public String processUserInputAndGetResponse(String userText) {
        if (userText == null || userText.trim().isEmpty()) return "";

        // Lazy init if not done yet
        if (!gemmaInitialized) {
            gemmaService.initialize();
            gemmaInitialized = true;
        }

        ChatMessage userMsg = new ChatMessage(userText, ChatMessage.Role.USER);
        autoSaveManager.saveMessage(userMsg);

        String aiResponse = gemmaService.getResponse(userText);

        ChatMessage aiMsg = new ChatMessage(aiResponse, ChatMessage.Role.AI);
        autoSaveManager.saveMessage(aiMsg);

        Log.d(TAG, "User: " + userText);
        Log.d(TAG, "TETRA: " + aiResponse);
        return aiResponse;
    }

    public String saveAndEndSession() {
        List<ChatMessage> messages = autoSaveManager.loadMessages();
        String pdfPath = null;
        if (!messages.isEmpty()) {
            pdfPath = pdfJournalService.exportToPDF(messages);
            autoSaveManager.clearMessages();
        }
        gemmaService.releaseMemory();
        gemmaInitialized = false;
        return pdfPath;
    }

    public boolean hasRestoredSession() {
        return autoSaveManager.hasUnsavedMessages();
    }

    public List<ChatMessage> getSessionMessages() {
        return autoSaveManager.loadMessages();
    }

    public String getLanguage() {
        SharedPreferences prefs = context.getSharedPreferences("tetra_prefs",
            Context.MODE_PRIVATE);
        return prefs.getString("app_language", "hinglish");
    }
}

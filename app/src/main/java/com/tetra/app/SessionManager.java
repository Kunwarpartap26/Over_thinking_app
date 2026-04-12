package com.tetra.app;

import android.content.Context;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class SessionManager {

    private static final String TAG = "SessionManager";
    private final Context context;
    private final GemmaService gemmaService;
    private final PDFJournalService pdfJournalService;
    private final List<ChatMessage> sessionMessages;

    public SessionManager(Context context) {
        this.context = context;
        this.gemmaService = new GemmaService(context);
        this.pdfJournalService = new PDFJournalService(context);
        this.sessionMessages = new ArrayList<>();
        gemmaService.initialize();
    }

    public String processUserInputAndGetResponse(String userText) {
        if (userText == null || userText.trim().isEmpty()) return "";
        sessionMessages.add(new ChatMessage(userText, ChatMessage.Role.USER));
        String aiResponse = gemmaService.getResponse(userText);
        sessionMessages.add(new ChatMessage(aiResponse, ChatMessage.Role.AI));
        Log.d(TAG, "User: " + userText);
        Log.d(TAG, "TETRA: " + aiResponse);
        return aiResponse;
    }

    public String saveAndEndSession() {
        String pdfPath = null;
        if (!sessionMessages.isEmpty()) {
            pdfPath = pdfJournalService.exportToPDF(sessionMessages);
        }
        gemmaService.releaseMemory();
        sessionMessages.clear();
        return pdfPath;
    }

    public List<ChatMessage> getSessionMessages() {
        return sessionMessages;
    }
}

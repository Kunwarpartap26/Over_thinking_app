package com.tetra.app;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SessionManager {

    private static final String TAG = "SessionManager";

    private final Context context;
    private final GemmaService gemmaService;
    private final PDFJournalService pdfJournalService;
    private final List<ChatMessage> sessionMessages;
    private TextToSpeech tts;

    public SessionManager(Context context) {
        this.context = context;
        this.gemmaService = new GemmaService(context);
        this.pdfJournalService = new PDFJournalService(context);
        this.sessionMessages = new ArrayList<>();

        // Initialize TTS
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("hi", "IN")); // Hindi TTS
                Log.d(TAG, "TTS initialized.");
            }
        });

        // Initialize Gemma
        gemmaService.initialize();
    }

    // Called when Vosk gives us a transcription
    public void processUserInput(String userText) {
        if (userText == null || userText.trim().isEmpty()) return;

        // 1. Log user message
        sessionMessages.add(new ChatMessage(userText, ChatMessage.Role.USER));
        Log.d(TAG, "User: " + userText);

        // 2. Get Gemma response
        String aiResponse = gemmaService.getResponse(userText);

        // 3. Log AI message
        sessionMessages.add(new ChatMessage(aiResponse, ChatMessage.Role.AI));
        Log.d(TAG, "TETRA: " + aiResponse);

        // 4. Speak the response
        speakOut(aiResponse);
    }

    // Save session to PDF and release memory
    public String saveAndEndSession() {
        String pdfPath = null;

        if (!sessionMessages.isEmpty()) {
            pdfPath = pdfJournalService.exportToPDF(sessionMessages);
            Log.d(TAG, "Session saved to: " + pdfPath);
        }

        // Phase C: Release LLM memory
        gemmaService.releaseMemory();
        sessionMessages.clear();

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        return pdfPath;
    }

    public List<ChatMessage> getSessionMessages() {
        return sessionMessages;
    }

    private void speakOut(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tetra_tts");
        }
    }
}

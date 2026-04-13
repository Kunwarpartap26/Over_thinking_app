package com.tetra.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AutoSaveManager {

    private static final String TAG = "AutoSaveManager";
    private static final String KEY_MESSAGES = "autosave_messages";
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public AutoSaveManager(Context context) {
        prefs = context.getSharedPreferences("tetra_autosave", Context.MODE_PRIVATE);
    }

    public void saveMessage(ChatMessage message) {
        try {
            List<ChatMessage> messages = loadMessages();
            messages.add(message);
            prefs.edit().putString(KEY_MESSAGES, gson.toJson(messages)).apply();
        } catch (Exception e) {
            Log.e(TAG, "Save error: " + e.getMessage());
        }
    }

    public List<ChatMessage> loadMessages() {
        try {
            String json = prefs.getString(KEY_MESSAGES, null);
            if (json == null) return new ArrayList<>();
            ChatMessage[] arr = gson.fromJson(json, ChatMessage[].class);
            return new ArrayList<>(Arrays.asList(arr));
        } catch (Exception e) {
            Log.e(TAG, "Load error: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public void clearMessages() {
        prefs.edit().remove(KEY_MESSAGES).apply();
    }

    public boolean hasUnsavedMessages() {
        return loadMessages().size() > 0;
    }
}

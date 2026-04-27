package com.tetra.app;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.widget.NestedScrollView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends BaseActivity {

    // UI
    private LinearLayout   chatContainer;
    private NestedScrollView scrollView;
    private EditText       inputBox;
    private Button         sendButton;
    private Button         micButton;
    private Button         sosButton;
    private Button         saveButton;
    private View           loadingBar;
    private TextView       loadingText;

    // Services
    private SessionManager  sessionManager;
    private WhisperService  whisperService;
    private AutoSaveManager autoSaveManager;
    private PDFJournalService pdfService;

    // State
    private final List<ChatMessage> messages  = new ArrayList<>();
    private final Handler           mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService   executor  = Executors.newSingleThreadExecutor();
    private boolean isListening = false;

    // Theme colors resolved at runtime
    private int colorBubbleAi;
    private int colorBubbleAiBorder;
    private int colorBubbleUser;
    private int colorBubbleUserText;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorAccent;
    private int colorBackground;
    private int colorSurface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // BaseActivity applies theme here
        setContentView(R.layout.activity_main);

        resolveThemeColors();
        bindViews();
        setupClickListeners();
        initServices();
        loadPreviousSession();
        initModelAsync();
    }

    // ── Resolve theme colors from attrs ────────────────────────────────────
    private void resolveThemeColors() {
        TypedValue tv = new TypedValue();
        colorBubbleAi       = resolveAttr(R.attr.tetraBubbleAi);
        colorBubbleAiBorder = resolveAttr(R.attr.tetraBubbleAiBorder);
        colorBubbleUser     = resolveAttr(R.attr.tetraBubbleUser);
        colorBubbleUserText = resolveAttr(R.attr.tetraBubbleUserText);
        colorTextPrimary    = resolveAttr(R.attr.tetraTextPrimary);
        colorTextSecondary  = resolveAttr(R.attr.tetraTextSecondary);
        colorAccent         = resolveAttr(R.attr.tetraAccent);
        colorBackground     = resolveAttr(R.attr.tetraBackground);
        colorSurface        = resolveAttr(R.attr.tetraSurface);
    }

    private int resolveAttr(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    // ── Bind views ─────────────────────────────────────────────────────────
    private void bindViews() {
        chatContainer = findViewById(R.id.chat_container);
        scrollView    = findViewById(R.id.scroll_view);
        inputBox      = findViewById(R.id.input_box);
        sendButton    = findViewById(R.id.send_button);
        micButton     = findViewById(R.id.mic_button);
        sosButton     = findViewById(R.id.sos_button);
        saveButton    = findViewById(R.id.save_button);
        loadingBar    = findViewById(R.id.loading_bar);
        loadingText   = findViewById(R.id.loading_text);
    }

    // ── Click listeners ────────────────────────────────────────────────────
    private void setupClickListeners() {
        sendButton.setOnClickListener(v -> {
            String text = inputBox.getText().toString().trim();
            if (!text.isEmpty()) {
                inputBox.setText("");
                processInput(text);
            }
        });

        inputBox.setOnEditorActionListener((v, actionId, event) -> {
            String text = inputBox.getText().toString().trim();
            if (!text.isEmpty()) {
                inputBox.setText("");
                processInput(text);
            }
            return true;
        });

        // Push-to-talk mic
        micButton.setOnClickListener(v -> {
            if (!isListening) {
                startListening();
            } else {
                stopListening();
            }
        });

        sosButton.setOnClickListener(v -> {
            startActivity(new Intent(this, BreathingActivity.class));
        });

        saveButton.setOnClickListener(v -> saveSession());

        saveButton.setOnLongClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        });
    }

    // ── Init services ──────────────────────────────────────────────────────
    private void initServices() {
        whisperService  = new WhisperService(this);
        whisperService.initialize();
        autoSaveManager = new AutoSaveManager(this);
        pdfService      = new PDFJournalService(this);
        sessionManager  = new SessionManager(this);
    }

    // ── Load previous session ──────────────────────────────────────────────
    private void loadPreviousSession() {
        List<ChatMessage> saved = autoSaveManager.loadMessages();
        if (saved != null && !saved.isEmpty()) {
            messages.addAll(saved);
            for (ChatMessage msg : saved) {
                boolean isUser = msg.getRole() == ChatMessage.Role.USER;
                addBubble(msg.getText(), isUser, false);
            }
            addTimestampDivider("Previous session restored");
        }
    }

    // ── Init model on background thread ───────────────────────────────────
    private void initModelAsync() {
        showLoading(true);
        executor.execute(() -> {
            sessionManager.initializeGemmaInBackground();
            mainHandler.post(() -> {
                showLoading(false);
                if (sessionManager.isGemmaReady()) {
                    addBubble("Namaste! Main ready hoon. Apne thoughts share karo. 💙",
                        false, true);
                } else {
                    addBubble("AI brain load nahi hua. Type karke baat karo — " +
                        "main sun raha hoon.", false, true);
                }
            });
        });
    }

    // ── Process user input ─────────────────────────────────────────────────
    private void processInput(String text) {
        addBubble(text, true, true);
        messages.add(new ChatMessage(text, ChatMessage.Role.USER));
        autoSaveManager.saveMessage(messages.get(messages.size()-1));

        // Typing indicator
        addBubble("...", false, false);

        executor.execute(() -> {
            String response = sessionManager.processUserInputAndGetResponse(text);
            mainHandler.post(() -> {
                // Remove typing indicator (last bubble)
                if (chatContainer.getChildCount() > 0) {
                    chatContainer.removeViewAt(chatContainer.getChildCount() - 1);
                }
                addBubble(response, false, true);
                messages.add(new ChatMessage(response, ChatMessage.Role.AI));
                autoSaveManager.saveMessage(messages.get(messages.size()-1));
            });
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CORE: addBubble — creates themed chat bubbles programmatically
    // ══════════════════════════════════════════════════════════════════════
    private void addBubble(String text, boolean isUser, boolean animate) {
        // Outer row — full width, controls alignment
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 6, 0, 6);
        row.setLayoutParams(rowParams);
        row.setGravity(isUser ? Gravity.END : Gravity.START);

        // Bubble text view
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(15f);
        bubble.setLineSpacing(4f, 1f);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));

        // Max width = 80% of screen
        int maxWidth = (int)(getResources().getDisplayMetrics().widthPixels * 0.80f);

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        bubbleParams.setMargins(
            isUser ? dp(48) : dp(4),
            0,
            isUser ? dp(4) : dp(48),
            0);
        bubble.setLayoutParams(bubbleParams);
        bubble.setMaxWidth(maxWidth);

        if (isUser) {
            // User bubble — accent color, right tail
            bubble.setTextColor(colorBubbleUserText);
            bubble.setBackgroundColor(colorBubbleUser);
            // Apply asymmetric corners via custom drawable
            try {
                bubble.setBackground(getDrawable(R.drawable.bg_bubble_user));
                bubble.setTextColor(colorBubbleUserText);
            } catch (Exception e) {
                bubble.setBackgroundColor(colorBubbleUser);
            }
        } else {
            // AI bubble — surface color, accent left border
            bubble.setTextColor(colorTextPrimary);
            try {
                bubble.setBackground(getDrawable(R.drawable.bg_bubble_ai));
            } catch (Exception e) {
                bubble.setBackgroundColor(colorBubbleAi);
            }
        }

        row.addView(bubble);
        chatContainer.addView(row);

        // Animate in
        if (animate) {
            row.setAlpha(0f);
            row.setTranslationY(20f);
            row.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(250)
                .start();
        }

        // Auto scroll to bottom
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    // ── Timestamp divider ──────────────────────────────────────────────────
    private void addTimestampDivider(String label) {
        TextView ts = new TextView(this);
        ts.setText(label);
        ts.setTextSize(11f);
        ts.setTextColor(colorTextSecondary);
        ts.setGravity(Gravity.CENTER);
        ts.setAllCaps(true);
        ts.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(12), 0, dp(12));
        ts.setLayoutParams(p);
        chatContainer.addView(ts);
    }

    // ── Push-to-talk mic ───────────────────────────────────────────────────
    private void startListening() {
        if (!whisperService.isReady()) {
            Toast.makeText(this, "Mic unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        isListening = true;
        micButton.setText("🔴");
        micButton.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(0xFFB71C1C));
        inputBox.setHint("Recording... tap mic to stop");

        whisperService.startListening(new WhisperService.RecognitionCallback() {
            @Override
            public void onPartialResult(String text) {
                mainHandler.post(() -> inputBox.setHint("Listening: " + text));
            }
            @Override
            public void onResult(String text) {
                mainHandler.post(() -> {
                    isListening = false;
                    resetMicButton();
                    if (text != null && !text.trim().isEmpty()) {
                        inputBox.setText(text.trim());
                        inputBox.setSelection(text.trim().length());
                        inputBox.setHint("Type karo ya mic use karo...");
                        // Auto-send after transcription
                        processInput(text.trim());
                        inputBox.setText("");
                    }
                });
            }
            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    isListening = false;
                    resetMicButton();
                    Toast.makeText(MainActivity.this,
                        "Voice error, please try again", Toast.LENGTH_SHORT).show();
                });
            }
            @Override
            public void onTimeout() {
                mainHandler.post(() -> {
                    isListening = false;
                    resetMicButton();
                    inputBox.setHint("Type karo ya mic use karo...");
                });
            }
        });
    }

    private void stopListening() {
        // Show transcribing state
        micButton.setText("⏳");
        micButton.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(colorAccent));
        inputBox.setHint("Transcribing...");
        whisperService.stopListening();
        // onResult callback will fire and reset the button
    }

    private void resetMicButton() {
        micButton.setText("🎤");
        micButton.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(colorSurface));
        inputBox.setHint("Type karo ya mic use karo...");
        isListening = false;
    }

    // ── Loading state ──────────────────────────────────────────────────────
    private void showLoading(boolean show) {
        loadingBar.setVisibility(show ? View.VISIBLE : View.GONE);
        loadingText.setVisibility(show ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!show);
        micButton.setEnabled(!show);
    }

    // ── Save session to PDF ────────────────────────────────────────────────
    private void saveSession() {
        if (messages.isEmpty()) {
            Toast.makeText(this, "Koi session nahi hai abhi", Toast.LENGTH_SHORT).show();
            return;
        }
        executor.execute(() -> {
            String path = pdfService.exportToPDF(messages);
            mainHandler.post(() -> {
                if (path != null) {
                    Toast.makeText(this,
                        "Journal saved! Documents/TETRA/ mein dekho",
                        Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this,
                        "Save failed. Storage check karo.",
                        Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // ── Utility ───────────────────────────────────────────────────────────
    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        whisperService.release();
        executor.shutdown();
    }
}

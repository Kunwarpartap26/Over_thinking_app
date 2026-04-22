package com.tetra.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import android.net.Uri;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends BaseActivity {

    private TextView sessionLog;
    private EditText inputBox;
    private Button micButton;
    private Button sendButton;
    private Button saveButton;
    private Button sosButton;
    private ScrollView scrollView;
    private ProgressBar loadingBar;
    private TextView loadingText;

    private SessionManager sessionManager;
    private TextToSpeech tts;
    private WhisperService whisperService;
    private boolean isListening = false;
    private boolean isProcessing = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final List<String> ANXIETY_WORDS = Arrays.asList(
        "panic","anxiety","attack","breathe","help",
        "ghabrana","dar","darna","ghabrahat","ro raha",
        "rona","bahut bura","nahi raha","control nahi",
        "hosh nahi","dil tez","mar jana","khatam"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        sessionLog  = findViewById(R.id.session_log);
        inputBox    = findViewById(R.id.input_box);
        micButton   = findViewById(R.id.mic_button);
        sendButton  = findViewById(R.id.send_button);
        saveButton  = findViewById(R.id.save_button);
        sosButton   = findViewById(R.id.sos_button);
        scrollView  = findViewById(R.id.scroll_view);
        loadingBar  = findViewById(R.id.loading_bar);
        loadingText = findViewById(R.id.loading_text);

        // Disable input until Gemma is ready
        setInputEnabled(false);
        showLoading(true, "AI brain load ho raha hai...");

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        // Init TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS)
                tts.setLanguage(new Locale("hi", "IN"));
        });

        // Init SessionManager (no Gemma yet)
        sessionManager = new SessionManager(this);

        // Load Gemma + Whisper in background
        new Thread(() -> {
            // Load Whisper first (small, fast)
            whisperService = new WhisperService(this);
            whisperService.initialize();

            // Then load Gemma (big, slow)
            mainHandler.post(() ->
                showLoading(true, "AI model load ho raha hai... (~1 min)"));

            sessionManager.initializeGemmaInBackground();

            mainHandler.post(() -> {
                showLoading(false, "");
                setInputEnabled(true);

                if (sessionManager.isGemmaReady()) {
                    appendToLog("🤖 TETRA: Namaste! Main ready hoon. " +
                        "Apne thoughts share karo. 💙");
                    speakOut("Namaste! Main TETRA hoon. Apne thoughts share karo.");
                } else {
                    appendToLog("⚠️ TETRA: AI brain load nahi hua. " +
                        "Typed responses only work. Dobara try karo.");
                }

                // Restore crashed session
                if (sessionManager.hasRestoredSession()) {
                    appendToLog("📂 Previous session restored!");
                }
            });
        }).start();

        sendButton.setOnClickListener(v -> sendTextInput());

        inputBox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
               (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                sendTextInput();
                return true;
            }
            return false;
        });

        micButton.setOnClickListener(v -> {
            if (!isListening) startListening();
            else stopListening();
        });

        sosButton.setOnClickListener(v ->
            startActivity(new Intent(this, BreathingActivity.class)));

        saveButton.setOnClickListener(v -> endSession());
    }

    private void showLoading(boolean show, String message) {
        loadingBar.setVisibility(show ? View.VISIBLE : View.GONE);
        loadingText.setVisibility(show ? View.VISIBLE : View.GONE);
        loadingText.setText(message);
    }

    private void setInputEnabled(boolean enabled) {
        sendButton.setEnabled(enabled);
        micButton.setEnabled(enabled);
        inputBox.setEnabled(enabled);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "⚙️ Settings");
        menu.add(0, 2, 0, "📊 Weekly Report");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1)
            startActivity(new Intent(this, SettingsActivity.class));
        else if (item.getItemId() == 2)
            startActivity(new Intent(this, WeeklyReportActivity.class));
        return true;
    }

    private void startListening() {
        if (whisperService == null || !whisperService.isReady()) {
            Toast.makeText(this, "Voice model load ho raha hai...",
                Toast.LENGTH_SHORT).show();
            return;
        }
        isListening = true;
        micButton.setText("🔴");
        inputBox.setHint("Bol rahe ho...");

        whisperService.startListening(new WhisperService.RecognitionCallback() {
            @Override
            public void onPartialResult(String text) {
                mainHandler.post(() -> inputBox.setHint("Listening: " + text));
            }
            @Override
            public void onResult(String text) {
                mainHandler.post(() -> {
                    stopListening();
                    if (text != null && !text.trim().isEmpty())
                        processInput(text.trim());
                    inputBox.setHint("Type karo ya mic use karo...");
                });
            }
            @Override
            public void onTimeout() {
                mainHandler.post(() -> { stopListening(); inputBox.setHint("Type karo ya mic use karo..."); });
            }
            public void onError(String error) {
                mainHandler.post(() -> {
                    stopListening();
                    Toast.makeText(MainActivity.this,
                        "Voice error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void stopListening() {
        if (whisperService != null) whisperService.stopListening();
        isListening = false;
        micButton.setText("🎤");
    }

    private void endSession() {
        String path = sessionManager.saveAndEndSession();
        sessionLog.setText("");
        Intent intent = new Intent(this, PostMoodActivity.class);
        intent.putExtra("pdf_path", path);
        startActivity(intent);
    }

    private void sendTextInput() {
        String text = inputBox.getText().toString().trim();
        if (text.isEmpty() || isProcessing) return;
        inputBox.setText("");
        processInput(text);
    }

    private void processInput(String text) {
        if (isProcessing) return;
        isProcessing = true;
        appendToLog("🧑 You: " + text);

        // Anxiety detection
        String lower = text.toLowerCase();
        for (String word : ANXIETY_WORDS) {
            if (lower.contains(word)) {
                appendToLog("⚠️ TETRA: Stressed lag raha hai. " +
                    "SOS button dabao. 💙");
                speakOut("Stressed lag raha hai. SOS button dabao.");
                break;
            }
        }

        // Show thinking indicator
        appendToLog("🤖 TETRA: Soch raha hoon...");

        new Thread(() -> {
            String response = sessionManager.processUserInputAndGetResponse(text);
            mainHandler.post(() -> {
                // Remove "thinking" message
                String log = sessionLog.getText().toString();
                log = log.replace("🤖 TETRA: Soch raha hoon...\n", "");
                sessionLog.setText(log);

                appendToLog("🤖 TETRA: " + response);
                speakOut(response);
                isProcessing = false;
            });
        }).start();
    }

    private void speakOut(String text) {
        if (tts != null)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts");
    }

    private void appendToLog(String msg) {
        sessionLog.setText(sessionLog.getText() + "\n" + msg + "\n");
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopListening();
        if (whisperService != null) whisperService.release();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}

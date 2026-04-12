package com.tetra.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements RecognitionListener {

    private TextView sessionLog;
    private EditText inputBox;
    private Button micButton, sendButton, saveButton, sosButton;
    private ScrollView scrollView;
    private SessionManager sessionManager;
    private TextToSpeech tts;
    private SpeechService speechService;
    private Model voskModel;
    private boolean isListening = false;
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

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        sessionLog = findViewById(R.id.session_log);
        inputBox   = findViewById(R.id.input_box);
        micButton  = findViewById(R.id.mic_button);
        sendButton = findViewById(R.id.send_button);
        saveButton = findViewById(R.id.save_button);
        sosButton  = findViewById(R.id.sos_button);
        scrollView = findViewById(R.id.scroll_view);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS)
                tts.setLanguage(new Locale("hi", "IN"));
        });

        sessionManager = new SessionManager(this);

        new Thread(() -> {
            try {
                ModelUtils.copyModelIfNeeded(getApplicationContext());
                voskModel = new Model(
                    getApplicationContext().getFilesDir() + "/model");
                mainHandler.post(() ->
                    Toast.makeText(this, "Voice ready! 🎤",
                        Toast.LENGTH_SHORT).show());
            } catch (IOException e) {
                mainHandler.post(() ->
                    Toast.makeText(this, "Voice model failed",
                        Toast.LENGTH_SHORT).show());
            }
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

        appendToLog("🤖 TETRA: Namaste! Apne thoughts share karo.\n" +
                   "Type karo ya mic use karo. SOS button anxiety ke liye hai. 💙");
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

    private void endSession() {
        String path = sessionManager.saveAndEndSession();
        sessionLog.setText("");
        // Go to post-mood screen
        Intent intent = new Intent(this, PostMoodActivity.class);
        intent.putExtra("pdf_path", path);
        startActivity(intent);
    }

    private void sendTextInput() {
        String text = inputBox.getText().toString().trim();
        if (text.isEmpty()) return;
        inputBox.setText("");
        processInput(text);
    }

    private void processInput(String text) {
        appendToLog("🧑 You: " + text);

        String lower = text.toLowerCase();
        for (String word : ANXIETY_WORDS) {
            if (lower.contains(word)) {
                appendToLog("⚠️ TETRA: Lag raha hai tum stressed ho. " +
                           "SOS button dabao breathing ke liye. 💙");
                speakOut("Lag raha hai tum stressed ho. SOS button dabao.");
                break;
            }
        }

        new Thread(() -> {
            String response = sessionManager.processUserInputAndGetResponse(text);
            mainHandler.post(() -> {
                appendToLog("🤖 TETRA: " + response);
                speakOut(response);
            });
        }).start();
    }

    private void startListening() {
        if (voskModel == null) {
            Toast.makeText(this, "Voice load ho raha hai...",
                Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Recognizer rec = new Recognizer(voskModel, 16000.0f);
            speechService = new SpeechService(rec, 16000.0f);
            speechService.startListening(this);
            isListening = true;
            micButton.setText("🔴");
        } catch (IOException e) {
            Toast.makeText(this, "Mic start nahi hua",
                Toast.LENGTH_SHORT).show();
        }
    }

    private void stopListening() {
        if (speechService != null) {
            speechService.stop();
            speechService = null;
        }
        isListening = false;
        micButton.setText("🎤");
    }

    private void speakOut(String text) {
        if (tts != null)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts");
    }

    private void appendToLog(String msg) {
        sessionLog.setText(sessionLog.getText() + "\n" + msg + "\n");
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void sharePDF(String path) {
        File f = new File(path);
        Uri uri = FileProvider.getUriForFile(this,
            getPackageName() + ".provider", f);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/pdf");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Share TETRA Journal"));
    }

    @Override public void onPartialResult(String h) {
        inputBox.setHint("Listening: " + h);
    }
    @Override public void onResult(String h) {
        processInput(h); stopListening();
        inputBox.setHint("Type karo ya mic use karo...");
    }
    @Override public void onFinalResult(String h) {
        processInput(h); stopListening();
    }
    @Override public void onError(Exception e) { stopListening(); }
    @Override public void onTimeout() { stopListening(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopListening();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}

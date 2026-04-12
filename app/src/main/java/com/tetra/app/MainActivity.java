package com.tetra.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import org.vosk.Model;
import java.io.IOException;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements RecognitionListener {

    private TextView sessionLog;
    private EditText inputBox;
    private Button micButton;
    private Button sendButton;
    private Button saveButton;
    private ScrollView scrollView;

    private SessionManager sessionManager;
    private TextToSpeech tts;
    private SpeechService speechService;
    private Model voskModel;
    private boolean isListening = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionLog = findViewById(R.id.session_log);
        inputBox   = findViewById(R.id.input_box);
        micButton  = findViewById(R.id.mic_button);
        sendButton = findViewById(R.id.send_button);
        saveButton = findViewById(R.id.save_button);
        scrollView = findViewById(R.id.scroll_view);

        // Request mic permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        // Init TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("hi", "IN"));
            }
        });

        // Init SessionManager
        sessionManager = new SessionManager(this);

        // Init Vosk model in background
        new Thread(() -> {
            try {
                ModelUtils.copyModelIfNeeded(getApplicationContext());
                voskModel = new Model(getApplicationContext().getFilesDir() + "/model");
                mainHandler.post(() ->
                    Toast.makeText(this, "Voice model ready!", Toast.LENGTH_SHORT).show());
            } catch (IOException e) {
                mainHandler.post(() ->
                    Toast.makeText(this, "Voice model load failed", Toast.LENGTH_SHORT).show());
            }
        }).start();

        // Send button
        sendButton.setOnClickListener(v -> sendTextInput());

        // Keyboard enter key
        inputBox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
               (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                sendTextInput();
                return true;
            }
            return false;
        });

        // Mic button
        micButton.setOnClickListener(v -> {
            if (!isListening) startListening();
            else stopListening();
        });

        // Save + Share PDF
        saveButton.setOnClickListener(v -> {
            String path = sessionManager.saveAndEndSession();
            if (path != null) {
                sessionLog.setText("");
                sharePDF(path);
            } else {
                Toast.makeText(this, "Koi messages nahi hain!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendTextInput() {
        String text = inputBox.getText().toString().trim();
        if (text.isEmpty()) return;
        inputBox.setText("");
        processInput(text);
    }

    private void processInput(String text) {
        // Add user message to UI
        appendToLog("🧑 You: " + text);

        // Get AI response in background
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
            Toast.makeText(this, "Voice model abhi load ho raha hai...", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Recognizer recognizer = new Recognizer(voskModel, 16000.0f);
            speechService = new SpeechService(recognizer, 16000.0f);
            speechService.startListening(this);
            isListening = true;
            micButton.setText("🔴 Stop");
        } catch (IOException e) {
            Toast.makeText(this, "Mic start nahi hua", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopListening() {
        if (speechService != null) {
            speechService.stop();
            speechService = null;
        }
        isListening = false;
        micButton.setText("🎤 Bolo");
    }

    private void speakOut(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tetra_tts");
        }
    }

    private void appendToLog(String message) {
        String current = sessionLog.getText().toString();
        sessionLog.setText(current + "\n" + message + "\n");
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void sharePDF(String path) {
        File pdfFile = new File(path);
        Uri uri = FileProvider.getUriForFile(this,
            getPackageName() + ".provider", pdfFile);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/pdf");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Share TETRA Journal"));
    }

    // Vosk callbacks
    @Override public void onPartialResult(String hypothesis) {
        inputBox.setHint("Listening: " + hypothesis);
    }

    @Override public void onResult(String hypothesis) {
        inputBox.setHint("Tap mic to speak");
        processInput(hypothesis);
        stopListening();
    }

    @Override public void onFinalResult(String hypothesis) {
        processInput(hypothesis);
        stopListening();
    }

    @Override public void onError(Exception e) {
        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        stopListening();
    }

    @Override public void onTimeout() {
        stopListening();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopListening();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}

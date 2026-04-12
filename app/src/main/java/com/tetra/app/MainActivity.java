package com.tetra.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.vosk.android.RecognitionListener;

public class MainActivity extends AppCompatActivity implements RecognitionListener {

    private TextView resultText;
    private TextView sessionLog;
    private Button micButton;
    private Button saveButton;
    private SessionManager sessionManager;
    private boolean isListening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultText  = findViewById(R.id.result_text);
        sessionLog  = findViewById(R.id.session_log);
        micButton   = findViewById(R.id.mic_button);
        saveButton  = findViewById(R.id.save_button);

        // Check mic permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        sessionManager = new SessionManager(this);

        micButton.setOnClickListener(v -> {
            if (!isListening) {
                micButton.setText("🔴 Stop");
                resultText.setText("Bol rahe ho... (Listening)");
                isListening = true;
            } else {
                micButton.setText("🎤 Bolo");
                resultText.setText("Processing...");
                isListening = false;
            }
        });

        saveButton.setOnClickListener(v -> {
            String path = sessionManager.saveAndEndSession();
            if (path != null) {
                Toast.makeText(this, "PDF saved: " + path, Toast.LENGTH_LONG).show();
                sessionLog.setText("");
            } else {
                Toast.makeText(this, "Kuch messages nahi hain save karne ke liye.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onPartialResult(String hypothesis) {
        resultText.setText(hypothesis);
    }

    @Override
    public void onResult(String hypothesis) {
        resultText.setText(hypothesis);
        sessionManager.processUserInput(hypothesis);
        updateSessionLog();
    }

    @Override
    public void onFinalResult(String hypothesis) {
        resultText.setText(hypothesis);
        updateSessionLog();
    }

    @Override
    public void onError(Exception e) {
        resultText.setText("Error: " + e.getMessage());
    }

    @Override
    public void onTimeout() {
        resultText.setText("Timeout - dobara try karo");
    }

    private void updateSessionLog() {
        StringBuilder log = new StringBuilder();
        for (ChatMessage msg : sessionManager.getSessionMessages()) {
            String label = msg.getRole() == ChatMessage.Role.USER ? "You" : "TETRA";
            log.append(label).append(": ").append(msg.getText()).append("\n\n");
        }
        sessionLog.setText(log.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sessionManager != null) sessionManager.saveAndEndSession();
    }
}

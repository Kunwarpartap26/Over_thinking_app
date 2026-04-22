package com.tetra.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.tts.TextToSpeech;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class BreathingActivity extends BaseActivity {

    private TextView breathText;
    private TextView cycleText;
    private TextView instructionText;
    private View breathCircle;
    private Button endButton;
    private Button sosCallButton;
    private TextToSpeech tts;
    private CountDownTimer breathTimer;
    private int cycleCount = 0;
    private static final int MAX_CYCLES = 4;

    // 4-7-8 breathing: inhale 4s, hold 7s, exhale 8s
    private static final int INHALE  = 4000;
    private static final int HOLD    = 7000;
    private static final int EXHALE  = 8000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_breathing);

        // Keep screen on during breathing
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        breathText      = findViewById(R.id.breath_text);
        cycleText       = findViewById(R.id.cycle_text);
        instructionText = findViewById(R.id.instruction_text);
        breathCircle    = findViewById(R.id.breath_circle);
        endButton       = findViewById(R.id.end_button);
        sosCallButton   = findViewById(R.id.sos_call_button);

        // Init TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("hi", "IN"));
                speak("Shant raho. Mere saath saath saans lo.");
            }
        });

        endButton.setOnClickListener(v -> finish());

        // SOS call trusted contact
        sosCallButton.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("tetra_prefs", MODE_PRIVATE);
            String contact = prefs.getString("sos_contact", "");
            if (!contact.isEmpty()) {
                Intent call = new Intent(Intent.ACTION_DIAL);
                call.setData(Uri.parse("tel:" + contact));
                startActivity(call);
            } else {
                sosCallButton.setText("No contact set!\nSet in Settings");
            }
        });

        startBreathingCycle();
    }

    private void startBreathingCycle() {
        if (cycleCount >= MAX_CYCLES) {
            breathText.setText("Bahut accha kiya! 🌟");
            instructionText.setText("Ab tum theek feel kar rahe ho.");
            speak("Bahut accha kiya. Ab tum theek feel kar rahe ho.");
            return;
        }

        cycleCount++;
        cycleText.setText("Cycle " + cycleCount + " / " + MAX_CYCLES);

        // INHALE
        breathText.setText("Saans lo...");
        instructionText.setText("Naak se dheere dheere saans andar lo");
        speak("Saans lo");
        animateCircle(0.3f, 1.0f, INHALE);

        breathTimer = new CountDownTimer(INHALE, 1000) {
            public void onTick(long ms) {
                breathText.setText("Saans lo... " + (ms/1000 + 1));
            }
            public void onFinish() {
                // HOLD
                breathText.setText("Roko...");
                instructionText.setText("Saans rok ke rakho");
                speak("Roko");
                new CountDownTimer(HOLD, 1000) {
                    public void onTick(long ms) {
                        breathText.setText("Roko... " + (ms/1000 + 1));
                    }
                    public void onFinish() {
                        // EXHALE
                        breathText.setText("Chodo...");
                        instructionText.setText("Muh se dheere dheere saans bahar nikalo");
                        speak("Chodo");
                        animateCircle(1.0f, 0.3f, EXHALE);
                        new CountDownTimer(EXHALE, 1000) {
                            public void onTick(long ms) {
                                breathText.setText("Chodo... " + (ms/1000 + 1));
                            }
                            public void onFinish() {
                                startBreathingCycle();
                            }
                        }.start();
                    }
                }.start();
            }
        }.start();
    }

    private void animateCircle(float from, float to, int duration) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(breathCircle, "scaleX", from, to);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(breathCircle, "scaleY", from, to);
        scaleX.setDuration(duration);
        scaleY.setDuration(duration);
        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleX.start();
        scaleY.start();
    }

    private void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "breath_tts");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (breathTimer != null) breathTimer.cancel();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}

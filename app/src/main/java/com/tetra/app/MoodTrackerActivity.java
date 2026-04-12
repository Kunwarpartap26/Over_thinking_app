package com.tetra.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MoodTrackerActivity extends AppCompatActivity {

    private SeekBar moodSlider;
    private TextView moodEmoji;
    private TextView moodLabel;
    private Button continueBtn;
    public static int preMoodScore = 5;

    private final String[] emojis  = {"😭","😢","😞","😟","😐","🙂","😊","😄","🤩","🥳"};
    private final String[] labels  = {
        "Bahut bura", "Bura", "Thoda bura", "Ukhda hua", "Theek theek",
        "Thoda theek", "Accha", "Bahut accha", "Zabardast", "Ekdum mast!"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mood);

        moodSlider  = findViewById(R.id.mood_slider);
        moodEmoji   = findViewById(R.id.mood_emoji);
        moodLabel   = findViewById(R.id.mood_label);
        continueBtn = findViewById(R.id.continue_btn);

        moodSlider.setMax(9);
        moodSlider.setProgress(4);

        moodSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                moodEmoji.setText(emojis[progress]);
                moodLabel.setText(labels[progress]);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        continueBtn.setOnClickListener(v -> {
            preMoodScore = moodSlider.getProgress() + 1;
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}

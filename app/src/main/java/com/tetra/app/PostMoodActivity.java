package com.tetra.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PostMoodActivity extends AppCompatActivity {

    private SeekBar moodSlider;
    private TextView moodEmoji;
    private TextView moodLabel;
    private String pdfPath;

    private final String[] emojis = {"😭","😢","😞","😟","😐","🙂","😊","😄","🤩","🥳"};
    private final String[] labels = {
        "Bahut bura", "Bura", "Thoda bura", "Ukhda hua", "Theek theek",
        "Thoda theek", "Accha", "Bahut accha", "Zabardast", "Ekdum mast!"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_mood);

        pdfPath    = getIntent().getStringExtra("pdf_path");
        moodSlider = findViewById(R.id.post_mood_slider);
        moodEmoji  = findViewById(R.id.post_mood_emoji);
        moodLabel  = findViewById(R.id.post_mood_label);

        moodSlider.setMax(9);
        moodSlider.setProgress(4);

        moodSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean f) {
                moodEmoji.setText(emojis[p]);
                moodLabel.setText(labels[p]);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        findViewById(R.id.done_btn).setOnClickListener(v -> {
            int moodAfter = moodSlider.getProgress() + 1;
            int moodBefore = MoodTrackerActivity.preMoodScore;

            // Save session summary
            SharedPreferences prefs = getSharedPreferences("tetra_prefs", MODE_PRIVATE);
            String today = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(new Date());

            // Save to prefs for weekly report
            int sessionCount = prefs.getInt("session_count", 0) + 1;
            prefs.edit()
                .putInt("session_count", sessionCount)
                .putString("session_" + sessionCount + "_date", today)
                .putInt("session_" + sessionCount + "_before", moodBefore)
                .putInt("session_" + sessionCount + "_after", moodAfter)
                .apply();

            // Show mood comparison
            int diff = moodAfter - moodBefore;
            String msg = diff > 0 ? "Tumhara mood improve hua! +" + diff :
                         diff < 0 ? "Aaj thoda tough tha. Kal better hoga." :
                         "Mood same raha. Practice jaari rakho.";

            TextView resultText = findViewById(R.id.mood_result);
            resultText.setText(
                "Pehle: " + moodBefore + "/10  →  Baad mein: " + moodAfter + "/10\n" + msg);

            // Share PDF if available
            if (pdfPath != null) {
                Intent share = new Intent(this, MainActivity.class);
                startActivity(share);
            }
            finish();
        });
    }
}

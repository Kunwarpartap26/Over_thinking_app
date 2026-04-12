package com.tetra.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class LanguageSelectActivity extends AppCompatActivity {

    private Button btnEnglish, btnHindi, btnHinglish;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language_select);

        prefs       = getSharedPreferences("tetra_prefs", MODE_PRIVATE);
        btnEnglish  = findViewById(R.id.btn_english);
        btnHindi    = findViewById(R.id.btn_hindi);
        btnHinglish = findViewById(R.id.btn_hinglish);

        btnEnglish.setOnClickListener(v  -> selectLanguage("english"));
        btnHindi.setOnClickListener(v    -> selectLanguage("hindi"));
        btnHinglish.setOnClickListener(v -> selectLanguage("hinglish"));
    }

    private void selectLanguage(String lang) {
        prefs.edit()
            .putString("app_language", lang)
            .putBoolean("language_selected", true)
            .apply();

        // First time -> go to PIN setup
        // From settings -> just go back
        boolean fromSettings = getIntent().getBooleanExtra("from_settings", false);
        if (fromSettings) {
            finish();
        } else {
            startActivity(new Intent(this, PINActivity.class));
            finish();
        }
    }
}

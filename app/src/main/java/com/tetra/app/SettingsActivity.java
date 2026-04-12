package com.tetra.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private EditText sosContactInput;
    private EditText newPinInput;
    private TextView currentLangText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs           = getSharedPreferences("tetra_prefs", MODE_PRIVATE);
        sosContactInput = findViewById(R.id.sos_contact_input);
        newPinInput     = findViewById(R.id.new_pin_input);
        currentLangText = findViewById(R.id.current_lang_text);

        sosContactInput.setText(prefs.getString("sos_contact", ""));
        updateLangDisplay();

        // Save SOS contact
        findViewById(R.id.save_contact_btn).setOnClickListener(v -> {
            String contact = sosContactInput.getText().toString().trim();
            if (contact.isEmpty()) {
                Toast.makeText(this, "Number daalo!", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString("sos_contact", contact).apply();
            Toast.makeText(this, "Contact save ho gaya!", Toast.LENGTH_SHORT).show();
        });

        // Change language
        findViewById(R.id.change_lang_btn).setOnClickListener(v -> {
            Intent intent = new Intent(this, LanguageSelectActivity.class);
            intent.putExtra("from_settings", true);
            startActivity(intent);
        });

        // Change PIN
        findViewById(R.id.change_pin_btn).setOnClickListener(v -> {
            String newPin = newPinInput.getText().toString().trim();
            if (newPin.length() != 4) {
                Toast.makeText(this, "4 digit PIN daalo!", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString("app_pin", newPin).apply();
            newPinInput.setText("");
            Toast.makeText(this, "PIN badal gaya!", Toast.LENGTH_SHORT).show();
        });

        // Remove PIN
        findViewById(R.id.remove_pin_btn).setOnClickListener(v -> {
            prefs.edit().remove("app_pin").apply();
            Toast.makeText(this, "PIN hata diya!", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.back_btn).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLangDisplay();
    }

    private void updateLangDisplay() {
        String lang = prefs.getString("app_language", "hinglish");
        String display;
        switch (lang) {
            case "english":  display = "🇬🇧 English Only"; break;
            case "hindi":    display = "🇮🇳 केवल हिंदी"; break;
            default:         display = "🔀 Hinglish"; break;
        }
        currentLangText.setText("Current: " + display);
    }
}

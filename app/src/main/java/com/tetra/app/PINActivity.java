package com.tetra.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class PINActivity extends BaseActivity {

    private TextView pinDisplay;
    private StringBuilder pinInput = new StringBuilder();
    private SharedPreferences prefs;
    private boolean isSettingPIN = false;
    private String firstPIN = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        prefs      = getSharedPreferences("tetra_prefs", MODE_PRIVATE);
        pinDisplay = findViewById(R.id.pin_display);

        String savedPIN = prefs.getString("app_pin", "");
        isSettingPIN = savedPIN.isEmpty();

        TextView titleText = findViewById(R.id.pin_title);
        if (isSettingPIN) {
            titleText.setText("Naya PIN set karo");
        } else {
            titleText.setText("PIN daalo");
        }

        // Number buttons
        int[] btnIds = {
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3,
            R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7,
            R.id.btn8, R.id.btn9
        };
        String[] nums = {"0","1","2","3","4","5","6","7","8","9"};

        for (int i = 0; i < btnIds.length; i++) {
            final String num = nums[i];
            findViewById(btnIds[i]).setOnClickListener(v -> addDigit(num));
        }

        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            if (pinInput.length() > 0) {
                pinInput.deleteCharAt(pinInput.length() - 1);
                updateDisplay();
            }
        });
    }

    private void addDigit(String digit) {
        if (pinInput.length() >= 4) return;
        pinInput.append(digit);
        updateDisplay();
        if (pinInput.length() == 4) checkPIN();
    }

    private void updateDisplay() {
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < pinInput.length(); i++) dots.append("● ");
        for (int i = pinInput.length(); i < 4; i++) dots.append("○ ");
        pinDisplay.setText(dots.toString().trim());
    }

    private void checkPIN() {
        String entered = pinInput.toString();
        pinInput.setLength(0);
        updateDisplay();

        if (isSettingPIN) {
            if (firstPIN.isEmpty()) {
                firstPIN = entered;
                ((TextView)findViewById(R.id.pin_title)).setText("Dobara daalo confirm karne ke liye");
            } else {
                if (firstPIN.equals(entered)) {
                    prefs.edit().putString("app_pin", entered).apply();
                    Toast.makeText(this, "PIN set ho gaya!", Toast.LENGTH_SHORT).show();
                    goNext();
                } else {
                    firstPIN = "";
                    ((TextView)findViewById(R.id.pin_title)).setText("PIN match nahi hua. Dobara try karo.");
                }
            }
        } else {
            String savedPIN = prefs.getString("app_pin", "");
            if (entered.equals(savedPIN)) {
                goNext();
            } else {
                Toast.makeText(this, "Galat PIN", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void goNext() {
        startActivity(new Intent(this, MoodTrackerActivity.class));
        finish();
    }
}

package com.tetra.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.cardview.widget.CardView;

public class ThemeSelectActivity extends BaseActivity {

    private String selectedTheme = ThemeManager.VAULT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_select);

        setupSwatches();

        Button btnContinue = findViewById(R.id.btn_continue);
        btnContinue.setOnClickListener(v -> {
            ThemeManager.save(this, selectedTheme);
            startActivity(new Intent(this, PINActivity.class));
            finish();
        });
    }

    private void setupSwatches() {
        int[][] swatches = {
            {R.id.swatch_vault,      R.id.label_vault},
            {R.id.swatch_midnight,   R.id.label_midnight},
            {R.id.swatch_monsoon,    R.id.label_monsoon},
            {R.id.swatch_terracotta, R.id.label_terracotta},
            {R.id.swatch_clarity,    R.id.label_clarity},
        };
        String[] keys = {
            ThemeManager.VAULT,
            ThemeManager.MIDNIGHT,
            ThemeManager.MONSOON,
            ThemeManager.TERRACOTTA,
            ThemeManager.CLARITY
        };

        for (int i = 0; i < swatches.length; i++) {
            final String key = keys[i];
            CardView card = findViewById(swatches[i][0]);
            card.setOnClickListener(v -> selectTheme(key));
        }

        // default selection
        selectTheme(ThemeManager.VAULT);
    }

    private void selectTheme(String key) {
        selectedTheme = key;

        // reset all borders
        int[] all = {
            R.id.swatch_vault, R.id.swatch_midnight, R.id.swatch_monsoon,
            R.id.swatch_terracotta, R.id.swatch_clarity
        };
        for (int id : all) {
            CardView c = findViewById(id);
            c.setCardElevation(2f);
            c.setAlpha(0.7f);
        }

        // highlight selected
        int selectedId;
        switch (key) {
            case ThemeManager.MIDNIGHT:   selectedId = R.id.swatch_midnight;   break;
            case ThemeManager.MONSOON:    selectedId = R.id.swatch_monsoon;    break;
            case ThemeManager.TERRACOTTA: selectedId = R.id.swatch_terracotta; break;
            case ThemeManager.CLARITY:    selectedId = R.id.swatch_clarity;    break;
            default:                      selectedId = R.id.swatch_vault;      break;
        }
        CardView selected = findViewById(selectedId);
        selected.setCardElevation(12f);
        selected.setAlpha(1f);
    }
}

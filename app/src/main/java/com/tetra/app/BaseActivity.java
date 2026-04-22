package com.tetra.app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);   // theme applied before layout inflates
        super.onCreate(savedInstanceState);
    }
}

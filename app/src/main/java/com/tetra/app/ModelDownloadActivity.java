package com.tetra.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ModelDownloadActivity extends AppCompatActivity {

    private static final String MODEL_URL =
        "https://drive.usercontent.google.com/download?id=1ds6ie9ByZB-G9ytwKtaWbvc6lChA-QfD&export=download&confirm=t";

    private TextView statusText;
    private TextView percentText;
    private ProgressBar progressBar;
    private Button downloadBtn;
    private Button manualBtn;
    private Button skipBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);

        statusText  = findViewById(R.id.status_text);
        percentText = findViewById(R.id.percent_text);
        progressBar = findViewById(R.id.progress_bar);
        downloadBtn = findViewById(R.id.download_btn);
        manualBtn   = findViewById(R.id.manual_btn);
        skipBtn     = findViewById(R.id.skip_btn);

        // Check if model already exists
        if (isModelReady()) {
            statusText.setText("✅ Model ready!");
            launchNext();
            return;
        }

        statusText.setText("TETRA ko ek baar AI brain download karna hoga (~290MB)");
        downloadBtn.setOnClickListener(v -> startDownload());
        manualBtn.setOnClickListener(v -> showManualInstructions());
        skipBtn.setOnClickListener(v -> launchNext());
    }

    private boolean isModelReady() {
        File modelFile = getModelFile();
        boolean ready = modelFile.exists() && modelFile.length() > 10_000_000L;
        android.util.Log.d("ModelDownload", "Model check: " + modelFile.getAbsolutePath() +
            " ready=" + ready + " size=" + modelFile.length());
        return ready;
    }

    private File getModelFile() {
        File dir = new File(getFilesDir(), "models");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "gemma.task");
    }

    private void showManualInstructions() {
        statusText.setText(
            "Manual setup:\n\n" +
            "1. Kaggle.com → gemma-3-270m-it-int8\n" +
            "2. Download gemma-3-270m-it-int8.task\n" +
            "3. Copy to phone:\n" +
            "   Android/data/com.tetra.app/files/models/gemma.task\n\n" +
            "Phir app restart karo!"
        );
    }

    private void startDownload() {
        downloadBtn.setEnabled(false);
        manualBtn.setEnabled(false);
        skipBtn.setEnabled(false);
        statusText.setText("Downloading... (~290MB) WiFi use karo");
        new DownloadTask().execute(MODEL_URL);
    }

    private void launchNext() {
        SharedPreferences prefs = getSharedPreferences("tetra_prefs", MODE_PRIVATE);
        boolean langSelected = prefs.getBoolean("language_selected", false);
        if (!langSelected) {
            startActivity(new Intent(this, LanguageSelectActivity.class));
        } else {
            // Check if PIN is set
            String pin = prefs.getString("app_pin", "");
            if (pin.isEmpty()) {
                startActivity(new Intent(this, PINActivity.class));
            } else {
                startActivity(new Intent(this, PINActivity.class));
            }
        }
        finish();
    }

    private class DownloadTask extends AsyncTask<String, Integer, Boolean> {
        @Override
        protected Boolean doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(0);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.connect();

                int responseCode = conn.getResponseCode();
                android.util.Log.d("ModelDownload", "Response code: " + responseCode);
                if (responseCode != 200) return false;

                int fileLength = conn.getContentLength();
                File outFile = getModelFile();
                android.util.Log.d("ModelDownload", "Saving to: " + outFile.getAbsolutePath());

                try (InputStream input = conn.getInputStream();
                     FileOutputStream output = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[8192];
                    long downloaded = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        downloaded += count;
                        output.write(buffer, 0, count);
                        if (fileLength > 0)
                            publishProgress((int)(downloaded * 100 / fileLength));
                    }
                }
                android.util.Log.d("ModelDownload", "Download complete! Size: " + outFile.length());
                return true;
            } catch (Exception e) {
                android.util.Log.e("ModelDownload", "Error: " + e.getMessage());
                return false;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progressBar.setProgress(values[0]);
            percentText.setText(values[0] + "%");
            statusText.setText("Downloading... " + values[0] + "%");
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                statusText.setText("✅ TETRA ready!");
                launchNext();
            } else {
                statusText.setText("❌ Download failed!\nManual setup try karo.");
                downloadBtn.setEnabled(true);
                manualBtn.setEnabled(true);
                skipBtn.setEnabled(true);
            }
        }
    }
}

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
        "https://drive.usercontent.google.com/download?id=1gcwyHMh0x323CZxmj4TIC7fcoD3Ns8Rv&export=download&confirm=t";

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

        if (isModelReady()) {
            statusText.setText("✅ Model already exists!");
            launchNext();
            return;
        }

        statusText.setText("TETRA ko ek baar AI brain download karna hoga (~1.5GB)");
        downloadBtn.setOnClickListener(v -> startDownload());
        manualBtn.setOnClickListener(v -> showManualInstructions());
        skipBtn.setOnClickListener(v -> launchNext());
    }

    private boolean isModelReady() {
        File modelFile = new File(getFilesDir(), "models/gemma.bin");
        return modelFile.exists() && modelFile.length() > 100_000_000L;
    }

    private void showManualInstructions() {
        statusText.setText(
            "Manual setup:\n\n" +
            "1. Kaggle.com pe jao (free signup)\n" +
            "2. gemma-2b-it-gpu-int4 download karo\n" +
            "3. Rename to gemma.bin\n" +
            "4. Copy to phone:\n" +
            "   Android/data/com.tetra.app/files/models/\n\n" +
            "Phir app restart karo!"
        );
    }

    private void startDownload() {
        downloadBtn.setEnabled(false);
        manualBtn.setEnabled(false);
        skipBtn.setEnabled(false);
        statusText.setText("Downloading... WiFi use karo");
        new DownloadTask().execute(MODEL_URL);
    }

    private void launchNext() {
        SharedPreferences prefs = getSharedPreferences("tetra_prefs", MODE_PRIVATE);
        boolean langSelected = prefs.getBoolean("language_selected", false);
        if (!langSelected) {
            startActivity(new Intent(this, LanguageSelectActivity.class));
        } else {
            startActivity(new Intent(this, PINActivity.class));
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
                conn.setReadTimeout(0); // no timeout for large file
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) return false;

                int fileLength = conn.getContentLength();
                File dir = new File(getFilesDir(), "models");
                dir.mkdirs();
                File outFile = new File(dir, "gemma.bin");

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
                return true;
            } catch (Exception e) {
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

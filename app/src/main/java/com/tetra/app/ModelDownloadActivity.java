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
        "https://huggingface.co/google/gemma-2b-it-gpu-int4/resolve/main/gemma-2b-it-gpu-int4.bin";

    private TextView statusText;
    private TextView percentText;
    private ProgressBar progressBar;
    private Button downloadBtn;
    private Button skipBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);

        statusText  = findViewById(R.id.status_text);
        percentText = findViewById(R.id.percent_text);
        progressBar = findViewById(R.id.progress_bar);
        downloadBtn = findViewById(R.id.download_btn);
        skipBtn     = findViewById(R.id.skip_btn);

        // Check if model already exists
        File modelFile = getModelFile();
        if (modelFile.exists() && modelFile.length() > 100_000_000L) {
            launchNext();
            return;
        }

        downloadBtn.setOnClickListener(v -> startDownload());
        skipBtn.setOnClickListener(v -> launchNext());
    }

    private File getModelFile() {
        File dir = new File(getFilesDir(), "models");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "gemma.bin");
    }

    private void startDownload() {
        downloadBtn.setEnabled(false);
        skipBtn.setEnabled(false);
        statusText.setText("Downloading TETRA brain... WiFi use karo");
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
                conn.connect();
                int fileLength = conn.getContentLength();
                File outFile = getModelFile();
                try (InputStream input = conn.getInputStream();
                     FileOutputStream output = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[4096];
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
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                statusText.setText("TETRA ready!");
                launchNext();
            } else {
                statusText.setText("Download failed. Internet check karo.");
                downloadBtn.setEnabled(true);
                skipBtn.setEnabled(true);
            }
        }
    }
}

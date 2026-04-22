package com.tetra.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WeeklyReportActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weekly_report);

        SharedPreferences prefs = getSharedPreferences("tetra_prefs", MODE_PRIVATE);
        int sessionCount = prefs.getInt("session_count", 0);

        TextView statsText = findViewById(R.id.stats_text);

        if (sessionCount == 0) {
            statsText.setText("Abhi tak koi session nahi hua.\nTETRA se baat karo!");
        } else {
            // Load summaries
            List<WeeklyReportService.SessionSummary> summaries = new ArrayList<>();
            int totalBefore = 0, totalAfter = 0;
            for (int i = 1; i <= sessionCount; i++) {
                String date   = prefs.getString("session_" + i + "_date", "");
                int before    = prefs.getInt("session_" + i + "_before", 5);
                int after     = prefs.getInt("session_" + i + "_after", 5);
                summaries.add(new WeeklyReportService.SessionSummary(date, before, after));
                totalBefore += before;
                totalAfter  += after;
            }

            double avgBefore = (double) totalBefore / sessionCount;
            double avgAfter  = (double) totalAfter  / sessionCount;
            double improvement = avgAfter - avgBefore;
            String trend = improvement > 0 ? "↑ Improving 🌟" :
                           improvement < 0 ? "↓ Needs attention 💙" : "→ Stable";

            statsText.setText(
                "Total Sessions: " + sessionCount + "\n\n" +
                String.format("Avg Mood Before: %.1f / 10\n", avgBefore) +
                String.format("Avg Mood After:  %.1f / 10\n\n", avgAfter) +
                "Trend: " + trend
            );

            // Generate PDF button
            Button generateBtn = findViewById(R.id.generate_report_btn);
            generateBtn.setOnClickListener(v -> {
                WeeklyReportService service = new WeeklyReportService(this);
                String path = service.generateWeeklyReport(summaries);
                if (path != null) {
                    sharePDF(path);
                } else {
                    Toast.makeText(this, "Report generate nahi hua", Toast.LENGTH_SHORT).show();
                }
            });
        }

        findViewById(R.id.back_btn2).setOnClickListener(v -> finish());
    }

    private void sharePDF(String path) {
        File file = new File(path);
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/pdf");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Weekly Report Share Karo"));
    }
}

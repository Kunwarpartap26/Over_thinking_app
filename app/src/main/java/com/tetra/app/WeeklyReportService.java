package com.tetra.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WeeklyReportService {

    private static final int PAGE_WIDTH  = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;

    private final Context context;

    public WeeklyReportService(Context context) {
        this.context = context;
    }

    public String generateWeeklyReport(List<SessionSummary> summaries) {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
            PAGE_WIDTH, PAGE_HEIGHT, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        int y = MARGIN;

        Paint titlePaint = new Paint();
        titlePaint.setColor(Color.parseColor("#1A237E"));
        titlePaint.setTextSize(24f);
        titlePaint.setFakeBoldText(true);

        Paint bodyPaint = new Paint();
        bodyPaint.setColor(Color.BLACK);
        bodyPaint.setTextSize(13f);

        Paint divider = new Paint();
        divider.setColor(Color.LTGRAY);
        divider.setStrokeWidth(1f);

        Paint moodPaint = new Paint();
        moodPaint.setTextSize(13f);

        // Header
        canvas.drawText("TETRA - Weekly Report", MARGIN, y + 24, titlePaint);
        y += 32;
        String week = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date());
        bodyPaint.setColor(Color.GRAY);
        canvas.drawText("Week ending: " + week, MARGIN, y, bodyPaint);
        y += 8;
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, divider);
        y += 20;

        bodyPaint.setColor(Color.BLACK);

        // Summary stats
        int totalSessions = summaries.size();
        double avgMoodBefore = 0, avgMoodAfter = 0;
        for (SessionSummary s : summaries) {
            avgMoodBefore += s.moodBefore;
            avgMoodAfter  += s.moodAfter;
        }
        if (totalSessions > 0) {
            avgMoodBefore /= totalSessions;
            avgMoodAfter  /= totalSessions;
        }

        canvas.drawText("Total Sessions: " + totalSessions, MARGIN, y, bodyPaint);
        y += 22;
        canvas.drawText(String.format("Avg Mood Before: %.1f / 10", avgMoodBefore), MARGIN, y, bodyPaint);
        y += 22;
        canvas.drawText(String.format("Avg Mood After:  %.1f / 10", avgMoodAfter), MARGIN, y, bodyPaint);
        y += 22;

        double improvement = avgMoodAfter - avgMoodBefore;
        String trend = improvement > 0 ? "↑ Improving" : improvement < 0 ? "↓ Needs attention" : "→ Stable";
        moodPaint.setColor(improvement > 0 ? Color.parseColor("#1B5E20") : Color.parseColor("#B71C1C"));
        canvas.drawText("Mood Trend: " + trend, MARGIN, y, moodPaint);
        y += 30;

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, divider);
        y += 20;

        // Session list
        bodyPaint.setColor(Color.BLACK);
        canvas.drawText("Session Details:", MARGIN, y, bodyPaint);
        y += 22;

        for (int i = 0; i < summaries.size(); i++) {
            SessionSummary s = summaries.get(i);
            canvas.drawText((i+1) + ". " + s.date +
                " | Before: " + s.moodBefore +
                " | After: " + s.moodAfter, MARGIN + 10, y, bodyPaint);
            y += 20;
        }

        // Footer
        Paint footerPaint = new Paint();
        footerPaint.setColor(Color.GRAY);
        footerPaint.setTextSize(10f);
        canvas.drawText("TETRA - Your Offline Truthful Mentor", MARGIN, PAGE_HEIGHT - 20, footerPaint);

        document.finishPage(page);

        // Save
        String fileName = "TETRA_Weekly_" +
            new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date()) + ".pdf";
        File outDir = context.getExternalFilesDir(null);
        if (outDir != null) outDir.mkdirs();
        File pdfFile = new File(outDir, fileName);

        try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
            document.writeTo(fos);
            return pdfFile.getAbsolutePath();
        } catch (IOException e) {
            return null;
        } finally {
            document.close();
        }
    }

    public static class SessionSummary {
        public String date;
        public int moodBefore;
        public int moodAfter;
        public SessionSummary(String date, int before, int after) {
            this.date = date;
            this.moodBefore = before;
            this.moodAfter = after;
        }
    }
}

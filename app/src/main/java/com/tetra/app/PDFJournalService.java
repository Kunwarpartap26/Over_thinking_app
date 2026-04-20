package com.tetra.app;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PDFJournalService {

    private static final String TAG        = "PDFJournalService";
    private static final int    PAGE_W     = 595;
    private static final int    PAGE_H     = 842;
    private static final int    MARGIN     = 40;
    private static final int    LINE_H     = 22;
    private static final String FOLDER     = "TETRA";

    private final Context context;

    public PDFJournalService(Context context) {
        this.context = context;
    }

    public String exportToPDF(List<ChatMessage> messages) {
        PdfDocument doc = buildDocument(messages);
        String fileName = "TETRA_Journal_" +
            new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date()) + ".pdf";
        try {
            String path = saveFile(doc, fileName);
            Log.d(TAG, "PDF saved: " + path);
            return path;
        } catch (IOException e) {
            Log.e(TAG, "Save failed", e);
            return null;
        } finally {
            doc.close();
        }
    }

    // ── Save using MediaStore on API 29+, direct path on older ────────────
    private String saveFile(PdfDocument doc, String fileName) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveViaMediaStore(doc, fileName);
        } else {
            return saveLegacy(doc, fileName);
        }
    }

    private String saveViaMediaStore(PdfDocument doc, String fileName) throws IOException {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        // Places file in Documents/TETRA/ folder — no permission needed on API 29+
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER);

        Uri uri = context.getContentResolver()
            .insert(MediaStore.Files.getContentUri("external"), cv);

        if (uri == null) throw new IOException("MediaStore insert failed");

        try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
            if (os == null) throw new IOException("Cannot open output stream");
            doc.writeTo(os);
        }
        return "Documents/" + FOLDER + "/" + fileName;
    }

    private String saveLegacy(PdfDocument doc, String fileName) throws IOException {
        File dir = new File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), FOLDER);
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            doc.writeTo(fos);
        }
        return file.getAbsolutePath();
    }

    // ── Build the PDF document ─────────────────────────────────────────────
    private PdfDocument buildDocument(List<ChatMessage> messages) {
        PdfDocument doc = new PdfDocument();

        Paint brand = makePaint("#1A237E", 22f, true);
        Paint sub   = makePaint("#5C6BC0", 12f, false);
        Paint date  = makePaint("#888888", 11f, false);
        Paint user  = makePaint("#1B5E20", 13f, true);
        Paint ai    = makePaint("#BF360C", 13f, true);
        Paint body  = makePaint("#000000", 13f, false);
        Paint div   = makePaint("#DDDDDD", 11f, false);
        div.setStrokeWidth(1f);

        PdfDocument.Page page = startPage(doc, 1);
        Canvas canvas = page.getCanvas();
        int[] state = {MARGIN, 1}; // y, pageNum

        // Header
        canvas.drawText("TETRA", MARGIN, state[0] + 22, brand);
        state[0] += 28;
        canvas.drawText("Truthful Guidance Journal", MARGIN, state[0], sub);
        state[0] += 18;
        String ts = new SimpleDateFormat("dd MMM yyyy, hh:mm a",
            Locale.getDefault()).format(new Date());
        canvas.drawText("Session: " + ts, MARGIN, state[0], date);
        state[0] += 8;
        canvas.drawLine(MARGIN, state[0], PAGE_W - MARGIN, state[0], div);
        state[0] += 20;

        // Messages
        for (ChatMessage msg : messages) {
            boolean isUser = msg.getRole() == ChatMessage.Role.USER;
            String label = isUser ? "You:" : "TETRA:";

            if (state[0] > PAGE_H - 80) {
                doc.finishPage(page);
                state[1]++;
                page = startPage(doc, state[1]);
                canvas = page.getCanvas();
                state[0] = MARGIN;
            }

            canvas.drawText(label, MARGIN, state[0], isUser ? user : ai);
            state[0] += LINE_H;

            for (String line : wrapText(msg.getText(), body, PAGE_W - MARGIN * 2)) {
                if (state[0] > PAGE_H - 60) {
                    doc.finishPage(page);
                    state[1]++;
                    page = startPage(doc, state[1]);
                    canvas = page.getCanvas();
                    state[0] = MARGIN;
                }
                canvas.drawText(line, MARGIN + 10, state[0], body);
                state[0] += LINE_H;
            }
            canvas.drawLine(MARGIN, state[0], PAGE_W - MARGIN, state[0], div);
            state[0] += 14;
        }

        // Footer
        Paint footer = makePaint("#AAAAAA", 10f, false);
        canvas.drawText("Generated by TETRA — Your Offline Truthful Mentor",
            MARGIN, PAGE_H - 20, footer);

        doc.finishPage(page);
        return doc;
    }

    private PdfDocument.Page startPage(PdfDocument doc, int num) {
        PdfDocument.PageInfo info =
            new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, num).create();
        return doc.startPage(info);
    }

    private Paint makePaint(String hex, float size, boolean bold) {
        Paint p = new Paint();
        p.setColor(Color.parseColor(hex));
        p.setTextSize(size);
        if (bold) p.setFakeBoldText(true);
        return p;
    }

    private List<String> wrapText(String text, Paint paint, int maxW) {
        List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            String test = cur.length() == 0 ? w : cur + " " + w;
            if (paint.measureText(test) <= maxW) {
                cur = new StringBuilder(test);
            } else {
                if (cur.length() > 0) lines.add(cur.toString());
                cur = new StringBuilder(w);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }
}

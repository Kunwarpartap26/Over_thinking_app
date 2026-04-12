package com.tetra.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ModelUtils {

    private static final String TAG = "ModelUtils";

    public static void copyModelIfNeeded(Context context) throws IOException {
        File destDir = new File(context.getFilesDir(), "model");
        if (destDir.exists()) return; // already copied
        destDir.mkdirs();
        copyAssetFolder(context.getAssets(), "model", destDir.getAbsolutePath());
        Log.d(TAG, "Vosk model copied to: " + destDir.getAbsolutePath());
    }

    private static void copyAssetFolder(AssetManager assets,
                                         String src, String dest) throws IOException {
        String[] files = assets.list(src);
        if (files == null) return;
        for (String file : files) {
            String srcPath  = src + "/" + file;
            String destPath = dest + "/" + file;
            if (assets.list(srcPath) != null && assets.list(srcPath).length > 0) {
                new File(destPath).mkdirs();
                copyAssetFolder(assets, srcPath, destPath);
            } else {
                try (InputStream in = assets.open(srcPath);
                     FileOutputStream out = new FileOutputStream(destPath)) {
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                }
            }
        }
    }
}

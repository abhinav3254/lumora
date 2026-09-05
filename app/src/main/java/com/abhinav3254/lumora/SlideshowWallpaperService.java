package com.abhinav3254.lumora;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class SlideshowWallpaperService extends WallpaperService {

    public static final String PREFS_NAME = "LumoraPrefs";
    public static final String KEY_IMAGE_URIS = "image_uris";
    public static final String URI_SEPARATOR = "|||";

    @Override
    public Engine onCreateEngine() {
        return new SlideshowEngine();
    }

    private class SlideshowEngine extends Engine {

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final List<String> imageUriStrings = new ArrayList<>();

        private Bitmap currentBitmap = null;
        private int currentIndex = 0;
        private boolean visible = false;
        private int surfaceW = 0, surfaceH = 0;

        private final Runnable slideshowRunnable = new Runnable() {
            @Override
            public void run() {
                if (!imageUriStrings.isEmpty()) {
                    currentIndex = (currentIndex + 1) % imageUriStrings.size();
                    loadBitmapAt(currentIndex);
                    drawFrame();
                }
                if (visible) {
                    handler.postDelayed(this, getIntervalMs());
                }
            }
        };

        private long getIntervalMs() {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            return prefs.getLong(MainActivity.KEY_INTERVAL_MS, 5000);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) {
                loadUrisFromPrefs();
                if (!imageUriStrings.isEmpty()) {
                    loadBitmapAt(currentIndex);
                }
                drawFrame();
                handler.removeCallbacks(slideshowRunnable);
                handler.postDelayed(slideshowRunnable, getIntervalMs());
            } else {
                handler.removeCallbacks(slideshowRunnable);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            surfaceW = width;
            surfaceH = height;
            loadUrisFromPrefs();
            if (!imageUriStrings.isEmpty()) {
                loadBitmapAt(currentIndex);
            }
            drawFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            visible = false;
            handler.removeCallbacks(slideshowRunnable);
            recycleBitmap();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            handler.removeCallbacks(slideshowRunnable);
            recycleBitmap();
        }

        private void recycleBitmap() {
            if (currentBitmap != null && !currentBitmap.isRecycled()) {
                currentBitmap.recycle();
                currentBitmap = null;
            }
        }

        private void loadUrisFromPrefs() {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String raw = prefs.getString(KEY_IMAGE_URIS, "");
            imageUriStrings.clear();
            if (raw != null && !raw.isEmpty()) {
                for (String p : raw.split("\\|\\|\\|")) {
                    if (!p.trim().isEmpty()) imageUriStrings.add(p.trim());
                }
            }
            if (currentIndex >= imageUriStrings.size()) currentIndex = 0;
        }

        private void loadBitmapAt(int index) {
            if (imageUriStrings.isEmpty() || surfaceW == 0 || surfaceH == 0) return;
            try {
                Uri uri = Uri.parse(imageUriStrings.get(index));

                // Pass 1: get dimensions
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) return;
                BitmapFactory.decodeStream(is, null, opts);
                is.close();

                opts.inSampleSize = calculateInSampleSize(opts, surfaceW, surfaceH);
                opts.inJustDecodeBounds = false;
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

                // Pass 2: decode
                is = getContentResolver().openInputStream(uri);
                if (is == null) return;
                Bitmap raw = BitmapFactory.decodeStream(is, null, opts);
                is.close();

                if (raw == null) return;
                Bitmap scaled = scaleBitmapToFill(raw, surfaceW, surfaceH);
                if (scaled != raw) raw.recycle();

                recycleBitmap();
                currentBitmap = scaled;

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private Bitmap scaleBitmapToFill(Bitmap src, int targetW, int targetH) {
            float srcRatio = (float) src.getWidth() / src.getHeight();
            float dstRatio = (float) targetW / targetH;
            int scaledW, scaledH;
            if (srcRatio > dstRatio) {
                scaledH = targetH;
                scaledW = (int) (targetH * srcRatio);
            } else {
                scaledW = targetW;
                scaledH = (int) (targetW / srcRatio);
            }
            Bitmap scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true);
            int x = Math.max(0, (scaledW - targetW) / 2);
            int y = Math.max(0, (scaledH - targetH) / 2);
            int cropW = Math.min(targetW, scaled.getWidth() - x);
            int cropH = Math.min(targetH, scaled.getHeight() - y);
            Bitmap cropped = Bitmap.createBitmap(scaled, x, y, cropW, cropH);
            if (cropped != scaled) scaled.recycle();
            return cropped;
        }

        private int calculateInSampleSize(BitmapFactory.Options opts, int reqW, int reqH) {
            int h = opts.outHeight, w = opts.outWidth, s = 1;
            if (h > reqH || w > reqW) {
                int hh = h / 2, hw = w / 2;
                while ((hh / s) >= reqH && (hw / s) >= reqW) s *= 2;
            }
            return s;
        }

        private void drawFrame() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;
                if (currentBitmap != null && !currentBitmap.isRecycled()) {
                    canvas.drawBitmap(currentBitmap, 0, 0, paint);
                } else {
                    canvas.drawColor(Color.BLACK);
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
        }
    }
}
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

        private List<String> imageUriStrings = new ArrayList<>();
        private Bitmap currentBitmap = null;
        private int currentIndex = 0;
        private boolean visible = false;

        private static final long INTERVAL_MS = 5000; // 5 seconds

        private final Runnable slideshowRunnable = new Runnable() {
            @Override
            public void run() {
                if (!imageUriStrings.isEmpty()) {
                    currentIndex = (currentIndex + 1) % imageUriStrings.size();
                    loadBitmapAt(currentIndex);
                    drawFrame();
                }
                if (visible) {
                    handler.postDelayed(this, INTERVAL_MS);
                }
            }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            loadUrisFromPrefs();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) {
                loadUrisFromPrefs();
                if (!imageUriStrings.isEmpty() && currentBitmap == null) {
                    loadBitmapAt(currentIndex);
                }
                drawFrame();
                handler.postDelayed(slideshowRunnable, INTERVAL_MS);
            } else {
                handler.removeCallbacks(slideshowRunnable);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            // Reload bitmap scaled to new surface size
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
            if (currentBitmap != null) {
                currentBitmap.recycle();
                currentBitmap = null;
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            handler.removeCallbacks(slideshowRunnable);
        }

        private void loadUrisFromPrefs() {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String raw = prefs.getString(KEY_IMAGE_URIS, "");
            imageUriStrings.clear();
            if (raw != null && !raw.isEmpty()) {
                String[] parts = raw.split("\\|\\|\\|");
                for (String p : parts) {
                    if (!p.trim().isEmpty()) {
                        imageUriStrings.add(p.trim());
                    }
                }
            }
            // Reset index if out of bounds
            if (currentIndex >= imageUriStrings.size()) {
                currentIndex = 0;
            }
        }

        private void loadBitmapAt(int index) {
            if (imageUriStrings.isEmpty()) return;

            SurfaceHolder holder = getSurfaceHolder();
            int targetW = holder.getSurfaceFrame().width();
            int targetH = holder.getSurfaceFrame().height();
            if (targetW == 0 || targetH == 0) return;

            try {
                Uri uri = Uri.parse(imageUriStrings.get(index));
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) return;

                // First pass: just get dimensions
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(is, null, opts);
                is.close();

                // Calculate sample size to avoid OOM
                opts.inSampleSize = calculateInSampleSize(opts, targetW, targetH);
                opts.inJustDecodeBounds = false;
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

                is = getContentResolver().openInputStream(uri);
                Bitmap raw = BitmapFactory.decodeStream(is, null, opts);
                is.close();

                if (raw == null) return;

                // Scale to fill screen
                Bitmap scaled = scaleBitmapToFill(raw, targetW, targetH);
                if (scaled != raw) raw.recycle();

                if (currentBitmap != null) currentBitmap.recycle();
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

            // Center-crop to target size
            int x = (scaledW - targetW) / 2;
            int y = (scaledH - targetH) / 2;
            return Bitmap.createBitmap(scaled, x, y, targetW, targetH);
        }

        private int calculateInSampleSize(BitmapFactory.Options options, int reqW, int reqH) {
            int height = options.outHeight;
            int width = options.outWidth;
            int inSampleSize = 1;
            if (height > reqH || width > reqW) {
                int halfH = height / 2;
                int halfW = width / 2;
                while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                    inSampleSize *= 2;
                }
            }
            return inSampleSize;
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
                    // Fallback: draw solid dark background
                    canvas.drawColor(Color.BLACK);
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }
}
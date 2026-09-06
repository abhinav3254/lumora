package com.abhinav3254.lumora;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SlideshowWallpaperService extends WallpaperService {

    public static final String PREFS_NAME      = "LumoraPrefs";
    public static final String KEY_IMAGE_URIS  = "image_uris";
    public static final String URI_SEPARATOR   = "|||";
    public static final String KEY_SHUFFLE     = "shuffle_mode";
    public static final String KEY_STAT_CYCLED = "stat_total_cycled";

    // Transition keys
    public static final String KEY_TRANSITION          = "transition_type";
    public static final String KEY_TRANSITION_DURATION = "transition_duration_ms";
    public static final String TRANSITION_NONE         = "none";
    public static final String TRANSITION_FADE         = "fade";
    public static final String TRANSITION_SLIDE        = "slide";
    public static final String TRANSITION_ZOOM         = "zoom";
    public static final int    DEFAULT_TRANSITION_MS   = 500;

    @Override
    public Engine onCreateEngine() {
        return new SlideshowEngine();
    }

    private class SlideshowEngine extends Engine {

        private final Handler handler  = new Handler(Looper.getMainLooper());
        private final Paint   paint    = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint   alphaPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Random  rng      = new Random();

        private final List<String> uriList = new ArrayList<>();

        private Bitmap  currentBitmap = null;
        private Bitmap  nextBitmap    = null;   // used during transition
        private boolean visible       = false;
        private int     surfaceW      = 0;
        private int     surfaceH      = 0;
        private int     currentIndex  = 0;

        // Transition state
        private boolean inTransition   = false;
        private long    transitionStart = 0;
        private int     transitionDuration = DEFAULT_TRANSITION_MS;
        private String  transitionType = TRANSITION_FADE;

        // ── Slideshow tick ────────────────────────────────────────────────────
        private final Runnable slideshowRunnable = new Runnable() {
            @Override
            public void run() {
                if (!uriList.isEmpty() && !inTransition) {
                    advance();
                    preloadNext();           // load next into nextBitmap
                    startTransition();       // begin animation
                    incrementStat();
                }
                if (visible) {
                    handler.postDelayed(this, getIntervalMs());
                }
            }
        };

        // ── Animation tick — runs at ~60fps during transition ─────────────────
        private final Runnable animRunnable = new Runnable() {
            @Override
            public void run() {
                if (!inTransition) return;
                drawFrame();
                float progress = (float)(System.currentTimeMillis() - transitionStart)
                        / transitionDuration;
                if (progress < 1f) {
                    handler.post(this);
                } else {
                    // Transition complete — swap bitmaps
                    finishTransition();
                }
            }
        };

        // ── Helpers ───────────────────────────────────────────────────────────
        private SharedPreferences prefs() {
            return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        }

        private long getIntervalMs() {
            return prefs().getLong(MainActivity.KEY_INTERVAL_MS, 5000);
        }

        private boolean isShuffleOn() {
            return prefs().getBoolean(KEY_SHUFFLE, false);
        }

        private String getTransitionType() {
            return prefs().getString(KEY_TRANSITION, TRANSITION_FADE);
        }

        private int getTransitionDuration() {
            return prefs().getInt(KEY_TRANSITION_DURATION, DEFAULT_TRANSITION_MS);
        }

        private void advance() {
            if (uriList.size() <= 1) { currentIndex = 0; return; }
            if (isShuffleOn()) {
                int next;
                do { next = rng.nextInt(uriList.size()); } while (next == currentIndex);
                currentIndex = next;
            } else {
                currentIndex = (currentIndex + 1) % uriList.size();
            }
        }

        private void incrementStat() {
            long count = prefs().getLong(KEY_STAT_CYCLED, 0);
            prefs().edit().putLong(KEY_STAT_CYCLED, count + 1).apply();
        }

        // ── Transition control ────────────────────────────────────────────────
        private void preloadNext() {
            if (nextBitmap != null && !nextBitmap.isRecycled()) {
                nextBitmap.recycle();
                nextBitmap = null;
            }
            nextBitmap = loadBitmapFromUri(currentIndex);
        }

        private void startTransition() {
            transitionType     = getTransitionType();
            transitionDuration = getTransitionDuration();

            if (transitionType.equals(TRANSITION_NONE) || nextBitmap == null) {
                // No animation — instant swap
                recycleBitmap(currentBitmap);
                currentBitmap = nextBitmap;
                nextBitmap    = null;
                drawFrame();
                return;
            }

            inTransition    = true;
            transitionStart = System.currentTimeMillis();
            handler.post(animRunnable);
        }

        private void finishTransition() {
            inTransition = false;
            recycleBitmap(currentBitmap);
            currentBitmap = nextBitmap;
            nextBitmap    = null;
            drawFrame();
        }

        // ── Lifecycle ─────────────────────────────────────────────────────────
        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) {
                loadUrisFromPrefs();
                if (currentBitmap == null && !uriList.isEmpty()) {
                    currentBitmap = loadBitmapFromUri(currentIndex);
                }
                drawFrame();
                handler.removeCallbacks(slideshowRunnable);
                handler.postDelayed(slideshowRunnable, getIntervalMs());
            } else {
                handler.removeCallbacks(slideshowRunnable);
                handler.removeCallbacks(animRunnable);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            surfaceW = width;
            surfaceH = height;
            loadUrisFromPrefs();
            if (!uriList.isEmpty()) currentBitmap = loadBitmapFromUri(currentIndex);
            drawFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            visible = false;
            handler.removeCallbacks(slideshowRunnable);
            handler.removeCallbacks(animRunnable);
            recycleBitmap(currentBitmap); currentBitmap = null;
            recycleBitmap(nextBitmap);    nextBitmap    = null;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            handler.removeCallbacks(slideshowRunnable);
            handler.removeCallbacks(animRunnable);
        }

        // ── URI loading ───────────────────────────────────────────────────────
        private void loadUrisFromPrefs() {
            String raw = prefs().getString(KEY_IMAGE_URIS, "");
            uriList.clear();
            if (raw != null && !raw.isEmpty()) {
                for (String p : raw.split("\\|\\|\\|")) {
                    if (!p.trim().isEmpty()) uriList.add(p.trim());
                }
            }
            if (currentIndex >= uriList.size()) currentIndex = 0;
        }

        // ── Bitmap loading ────────────────────────────────────────────────────
        private Bitmap loadBitmapFromUri(int index) {
            if (uriList.isEmpty() || surfaceW == 0 || surfaceH == 0) return null;
            try {
                Uri uri = Uri.parse(uriList.get(index));

                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) return null;
                BitmapFactory.decodeStream(is, null, opts);
                is.close();

                opts.inSampleSize     = calculateInSampleSize(opts, surfaceW, surfaceH);
                opts.inJustDecodeBounds = false;
                opts.inPreferredConfig  = Bitmap.Config.ARGB_8888;

                is = getContentResolver().openInputStream(uri);
                if (is == null) return null;
                Bitmap raw = BitmapFactory.decodeStream(is, null, opts);
                is.close();

                if (raw == null) return null;
                Bitmap scaled = scaleBitmapToFill(raw, surfaceW, surfaceH);
                if (scaled != raw) raw.recycle();
                return scaled;

            } catch (Exception e) {
                e.printStackTrace();
                return null;
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
            int x     = Math.max(0, (scaledW - targetW) / 2);
            int y     = Math.max(0, (scaledH - targetH) / 2);
            int cropW = Math.min(targetW, scaled.getWidth()  - x);
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

        private void recycleBitmap(Bitmap b) {
            if (b != null && !b.isRecycled()) b.recycle();
        }

        // ── Drawing ───────────────────────────────────────────────────────────
        private void drawFrame() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;

                if (!inTransition || nextBitmap == null) {
                    // Static frame
                    drawBitmapOrBlack(canvas, currentBitmap);
                    return;
                }

                float progress = Math.min(1f,
                        (float)(System.currentTimeMillis() - transitionStart) / transitionDuration);
                // Ease in-out
                float t = progress < 0.5f
                        ? 2 * progress * progress
                        : 1 - (float) Math.pow(-2 * progress + 2, 2) / 2;

                switch (transitionType) {
                    case TRANSITION_FADE:
                        drawFade(canvas, t);
                        break;
                    case TRANSITION_SLIDE:
                        drawSlide(canvas, t);
                        break;
                    case TRANSITION_ZOOM:
                        drawZoom(canvas, t);
                        break;
                    default:
                        drawBitmapOrBlack(canvas, nextBitmap);
                }

            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
        }

        // Fade: cross-dissolve current → next
        private void drawFade(Canvas canvas, float t) {
            drawBitmapOrBlack(canvas, currentBitmap);
            if (nextBitmap != null && !nextBitmap.isRecycled()) {
                alphaPaint.setAlpha((int)(t * 255));
                canvas.drawBitmap(nextBitmap, 0, 0, alphaPaint);
                alphaPaint.setAlpha(255);
            }
        }

        // Slide: current exits left, next enters from right
        private void drawSlide(Canvas canvas, float t) {
            int offset = (int)(surfaceW * t);
            if (currentBitmap != null && !currentBitmap.isRecycled()) {
                canvas.drawBitmap(currentBitmap, -offset, 0, paint);
            }
            if (nextBitmap != null && !nextBitmap.isRecycled()) {
                canvas.drawBitmap(nextBitmap, surfaceW - offset, 0, paint);
            }
        }

        // Zoom: current fades + zooms out, next fades in normal size
        private void drawZoom(Canvas canvas, float t) {
            // Current zooms in slightly as it fades out
            if (currentBitmap != null && !currentBitmap.isRecycled()) {
                float scale = 1f + 0.08f * t;
                Matrix m = new Matrix();
                m.setScale(scale, scale, surfaceW / 2f, surfaceH / 2f);
                alphaPaint.setAlpha((int)((1f - t) * 255));
                canvas.drawBitmap(currentBitmap, m, alphaPaint);
                alphaPaint.setAlpha(255);
            }
            // Next fades in
            if (nextBitmap != null && !nextBitmap.isRecycled()) {
                alphaPaint.setAlpha((int)(t * 255));
                canvas.drawBitmap(nextBitmap, 0, 0, alphaPaint);
                alphaPaint.setAlpha(255);
            }
        }

        private void drawBitmapOrBlack(Canvas canvas, Bitmap b) {
            if (b != null && !b.isRecycled()) {
                canvas.drawBitmap(b, 0, 0, paint);
            } else {
                canvas.drawColor(Color.BLACK);
            }
        }
    }
}

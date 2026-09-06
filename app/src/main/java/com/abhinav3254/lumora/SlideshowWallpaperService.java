package com.abhinav3254.lumora;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SlideshowWallpaperService extends WallpaperService {

    public static final String PREFS_NAME             = "LumoraPrefs";
    public static final String KEY_IMAGE_URIS         = "image_uris";
    public static final String URI_SEPARATOR          = "|||";
    public static final String KEY_SHUFFLE            = "shuffle_mode";
    public static final String KEY_STAT_CYCLED        = "stat_total_cycled";
    public static final String KEY_TRANSITION         = "transition_type";
    public static final String KEY_TRANSITION_DURATION= "transition_duration_ms";
    public static final String TRANSITION_NONE        = "none";
    public static final String TRANSITION_FADE        = "fade";
    public static final String TRANSITION_SLIDE       = "slide";
    public static final String TRANSITION_ZOOM        = "zoom";
    public static final int    DEFAULT_TRANSITION_MS  = 500;

    @Override
    public Engine onCreateEngine() { return new SlideshowEngine(); }

    private class SlideshowEngine extends Engine {

        private final Handler handler    = new Handler(Looper.getMainLooper());
        private final Paint   paint      = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint   alphaPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Random  rng        = new Random();
        private final List<String> uriList = new ArrayList<>();

        // Wallpaper state
        private Bitmap  currentBitmap    = null;
        private Bitmap  nextBitmap       = null;
        private boolean visible          = false;
        private int     surfaceW         = 0, surfaceH = 0;
        private int     currentIndex     = 0;

        // Transition state
        private boolean inTransition     = false;
        private long    transitionStart  = 0;
        private int     transitionDuration = DEFAULT_TRANSITION_MS;
        private String  transitionType   = TRANSITION_FADE;

        // ── Overlay (drawn on canvas) ─────────────────────────────────────────
        private boolean overlayVisible   = false;
        private long    lastTapTime      = 0;
        private static final long DOUBLE_TAP_MS  = 350;
        private static final long OVERLAY_AUTO_HIDE_MS = 5000;

        // Overlay geometry — computed once surface is known
        private float  overlayL, overlayT, overlayR, overlayB;   // card bounds
        private final float[] chipL = new float[4];
        private final float[] chipR = new float[4];
        private float  chipTop, chipBot;

        // Overlay paints
        private final Paint overlayBgPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint chipActivePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint chipInactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint         = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint        = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dimPaint          = new Paint();

        private final String[] CHIP_LABELS = {"None", "Fade", "Slide", "Zoom"};
        private final String[] CHIP_TYPES  = {
                TRANSITION_NONE, TRANSITION_FADE, TRANSITION_SLIDE, TRANSITION_ZOOM
        };

        private final Runnable autoHideOverlay = () -> {
            overlayVisible = false;
            drawFrame();
        };

        // ── Runnables ─────────────────────────────────────────────────────────
        private final Runnable slideshowRunnable = new Runnable() {
            @Override public void run() {
                if (!uriList.isEmpty() && !inTransition) {
                    advance();
                    preloadNext();
                    startTransition();
                    incrementStat();
                }
                if (visible) handler.postDelayed(this, getIntervalMs());
            }
        };

        private final Runnable animRunnable = new Runnable() {
            @Override public void run() {
                if (!inTransition) return;
                drawFrame();
                float p = (float)(System.currentTimeMillis() - transitionStart) / transitionDuration;
                if (p < 1f) handler.post(this);
                else        finishTransition();
            }
        };

        // ── Engine lifecycle ──────────────────────────────────────────────────
        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setTouchEventsEnabled(true);   // ← must enable to receive onTouchEvent

            overlayBgPaint.setColor(0xDD1a1a2a);
            chipActivePaint.setColor(0xFFccc5c1);
            chipInactivePaint.setColor(0xFF2d2927);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setColor(0xFFccc5c1);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            dimPaint.setColor(0x66000000);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) {
                loadUrisFromPrefs();
                if (currentBitmap == null && !uriList.isEmpty())
                    currentBitmap = loadBitmapFromUri(currentIndex);
                drawFrame();
                handler.removeCallbacks(slideshowRunnable);
                handler.postDelayed(slideshowRunnable, getIntervalMs());
            } else {
                handler.removeCallbacks(slideshowRunnable);
                handler.removeCallbacks(animRunnable);
                handler.removeCallbacks(autoHideOverlay);
                overlayVisible = false;
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            surfaceW = w; surfaceH = h;
            computeOverlayGeometry();
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
            handler.removeCallbacks(autoHideOverlay);
            recycleBitmap(currentBitmap); currentBitmap = null;
            recycleBitmap(nextBitmap);    nextBitmap    = null;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            handler.removeCallbacks(slideshowRunnable);
            handler.removeCallbacks(animRunnable);
            handler.removeCallbacks(autoHideOverlay);
        }

        // ── Touch ─────────────────────────────────────────────────────────────
        @Override
        public void onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) return;
            float x = event.getX(), y = event.getY();
            long now = System.currentTimeMillis();

            if (overlayVisible) {
                // Check chip taps
                boolean tappedChip = false;
                if (y >= chipTop && y <= chipBot) {
                    for (int i = 0; i < 4; i++) {
                        if (x >= chipL[i] && x <= chipR[i]) {
                            selectTransition(CHIP_TYPES[i]);
                            tappedChip = true;
                            break;
                        }
                    }
                }
                if (!tappedChip) {
                    // Tap outside → dismiss
                    overlayVisible = false;
                    handler.removeCallbacks(autoHideOverlay);
                }
                drawFrame();
                return;
            }

            // Double tap → show overlay
            if (now - lastTapTime < DOUBLE_TAP_MS) {
                overlayVisible = true;
                handler.removeCallbacks(autoHideOverlay);
                handler.postDelayed(autoHideOverlay, OVERLAY_AUTO_HIDE_MS);
                drawFrame();
                lastTapTime = 0;
            } else {
                lastTapTime = now;
            }
        }

        private void selectTransition(String type) {
            prefs().edit()
                    .putString(KEY_TRANSITION, type)
                    .apply();
            transitionType = type;
            handler.removeCallbacks(autoHideOverlay);
            handler.postDelayed(autoHideOverlay, OVERLAY_AUTO_HIDE_MS);
        }

        // ── Overlay geometry ──────────────────────────────────────────────────
        private void computeOverlayGeometry() {
            if (surfaceW == 0 || surfaceH == 0) return;
            float pad  = surfaceW * 0.05f;
            float cardH = surfaceH * 0.20f;
            overlayL = pad;
            overlayR = surfaceW - pad;
            overlayB = surfaceH - pad;
            overlayT = overlayB - cardH;

            float chipPad  = surfaceW * 0.02f;
            float chipW    = (overlayR - overlayL - chipPad * 5) / 4f;
            chipTop = overlayT + cardH * 0.45f;
            chipBot = overlayB - cardH * 0.12f;
            for (int i = 0; i < 4; i++) {
                chipL[i] = overlayL + chipPad + i * (chipW + chipPad);
                chipR[i] = chipL[i] + chipW;
            }
        }

        // ── Drawing ───────────────────────────────────────────────────────────
        private void drawFrame() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;

                // Draw wallpaper
                if (!inTransition || nextBitmap == null) {
                    drawBitmapOrBlack(canvas, currentBitmap);
                } else {
                    float progress = Math.min(1f,
                            (float)(System.currentTimeMillis() - transitionStart) / transitionDuration);
                    float t = progress < 0.5f
                            ? 2 * progress * progress
                            : 1 - (float) Math.pow(-2 * progress + 2, 2) / 2;
                    switch (transitionType) {
                        case TRANSITION_FADE:  drawFade(canvas, t);  break;
                        case TRANSITION_SLIDE: drawSlide(canvas, t); break;
                        case TRANSITION_ZOOM:  drawZoom(canvas, t);  break;
                        default: drawBitmapOrBlack(canvas, nextBitmap);
                    }
                }

                // Draw overlay on top
                if (overlayVisible) drawOverlay(canvas);

            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
        }

        private void drawOverlay(Canvas canvas) {
            // Dim the whole screen slightly
            canvas.drawRect(0, 0, surfaceW, surfaceH, dimPaint);

            float radius = surfaceW * 0.04f;

            // Card background
            RectF card = new RectF(overlayL, overlayT, overlayR, overlayB);
            canvas.drawRoundRect(card, radius, radius, overlayBgPaint);

            // Label — "Double-tap to close  ·  Transition"
            String currentType = prefs().getString(KEY_TRANSITION, TRANSITION_FADE);
            String currentName = currentType.substring(0, 1).toUpperCase() + currentType.substring(1);
            labelPaint.setTextSize(surfaceW * 0.032f);
            labelPaint.setColor(0xFF988f88);
            canvas.drawText("Transition  ·  " + currentName,
                    (overlayL + overlayR) / 2f,
                    overlayT + (overlayB - overlayT) * 0.28f,
                    labelPaint);

            // Chips
            float chipRadius = (chipBot - chipTop) * 0.3f;
            textPaint.setTextSize(surfaceW * 0.034f);
            for (int i = 0; i < 4; i++) {
                boolean active = CHIP_TYPES[i].equals(currentType);
                RectF chipRect = new RectF(chipL[i], chipTop, chipR[i], chipBot);
                canvas.drawRoundRect(chipRect, chipRadius, chipRadius,
                        active ? chipActivePaint : chipInactivePaint);
                textPaint.setColor(active ? Color.BLACK : 0xFFccc5c1);
                canvas.drawText(CHIP_LABELS[i],
                        (chipL[i] + chipR[i]) / 2f,
                        chipTop + (chipBot - chipTop) / 2f + textPaint.getTextSize() * 0.35f,
                        textPaint);
            }

            // Hint at bottom
            labelPaint.setTextSize(surfaceW * 0.026f);
            labelPaint.setColor(0xFF4c4640);
            canvas.drawText("Tap outside to close",
                    (overlayL + overlayR) / 2f,
                    overlayB - surfaceH * 0.012f,
                    labelPaint);
        }

        // ── Transition drawing ────────────────────────────────────────────────
        private void drawFade(Canvas canvas, float t) {
            drawBitmapOrBlack(canvas, currentBitmap);
            if (nextBitmap != null && !nextBitmap.isRecycled()) {
                alphaPaint.setAlpha((int)(t * 255));
                canvas.drawBitmap(nextBitmap, 0, 0, alphaPaint);
                alphaPaint.setAlpha(255);
            }
        }

        private void drawSlide(Canvas canvas, float t) {
            int offset = (int)(surfaceW * t);
            if (currentBitmap != null && !currentBitmap.isRecycled())
                canvas.drawBitmap(currentBitmap, -offset, 0, paint);
            if (nextBitmap != null && !nextBitmap.isRecycled())
                canvas.drawBitmap(nextBitmap, surfaceW - offset, 0, paint);
        }

        private void drawZoom(Canvas canvas, float t) {
            if (currentBitmap != null && !currentBitmap.isRecycled()) {
                float scale = 1f + 0.08f * t;
                Matrix m = new Matrix();
                m.setScale(scale, scale, surfaceW / 2f, surfaceH / 2f);
                alphaPaint.setAlpha((int)((1f - t) * 255));
                canvas.drawBitmap(currentBitmap, m, alphaPaint);
                alphaPaint.setAlpha(255);
            }
            if (nextBitmap != null && !nextBitmap.isRecycled()) {
                alphaPaint.setAlpha((int)(t * 255));
                canvas.drawBitmap(nextBitmap, 0, 0, alphaPaint);
                alphaPaint.setAlpha(255);
            }
        }

        private void drawBitmapOrBlack(Canvas canvas, Bitmap b) {
            if (b != null && !b.isRecycled()) canvas.drawBitmap(b, 0, 0, paint);
            else                               canvas.drawColor(Color.BLACK);
        }

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
            long c = prefs().getLong(KEY_STAT_CYCLED, 0);
            prefs().edit().putLong(KEY_STAT_CYCLED, c + 1).apply();
        }

        private void preloadNext() {
            recycleBitmap(nextBitmap);
            nextBitmap = loadBitmapFromUri(currentIndex);
        }

        private void startTransition() {
            transitionType     = getTransitionType();
            transitionDuration = getTransitionDuration();
            if (transitionType.equals(TRANSITION_NONE) || nextBitmap == null) {
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
            } catch (Exception e) { e.printStackTrace(); return null; }
        }

        private Bitmap scaleBitmapToFill(Bitmap src, int tw, int th) {
            float sr = (float) src.getWidth() / src.getHeight();
            float dr = (float) tw / th;
            int sw, sh;
            if (sr > dr) { sh = th; sw = (int)(th * sr); }
            else          { sw = tw; sh = (int)(tw / sr); }
            Bitmap scaled  = Bitmap.createScaledBitmap(src, sw, sh, true);
            int x = Math.max(0, (sw - tw) / 2);
            int y = Math.max(0, (sh - th) / 2);
            Bitmap cropped = Bitmap.createBitmap(scaled, x, y,
                    Math.min(tw, scaled.getWidth()-x),
                    Math.min(th, scaled.getHeight()-y));
            if (cropped != scaled) scaled.recycle();
            return cropped;
        }

        private int calculateInSampleSize(BitmapFactory.Options o, int rw, int rh) {
            int h = o.outHeight, w = o.outWidth, s = 1;
            if (h > rh || w > rw) {
                int hh = h/2, hw = w/2;
                while ((hh/s) >= rh && (hw/s) >= rw) s *= 2;
            }
            return s;
        }

        private void recycleBitmap(Bitmap b) {
            if (b != null && !b.isRecycled()) b.recycle();
        }
    }
}

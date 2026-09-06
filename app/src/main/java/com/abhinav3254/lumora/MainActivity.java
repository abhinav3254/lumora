package com.abhinav3254.lumora;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String KEY_INTERVAL_MS = "interval_ms";

    private static final String[] TRANSITION_TYPES = {
            SlideshowWallpaperService.TRANSITION_NONE,
            SlideshowWallpaperService.TRANSITION_FADE,
            SlideshowWallpaperService.TRANSITION_SLIDE,
            SlideshowWallpaperService.TRANSITION_ZOOM
    };
    private static final int[] SHEET_CHIP_IDS = {
            R.id.sheet_chip_none,
            R.id.sheet_chip_fade,
            R.id.sheet_chip_slide,
            R.id.sheet_chip_zoom
    };

    // Main views
    private TextView     tvPhotoCount, tvIntervalStatus;
    private RecyclerView recyclerDeck;
    private View         emptyState;
    private DeckAdapter  deckAdapter;
    private final List<String> deckUris = new ArrayList<>();

    // Bottom sheet views
    private View       bottomSheet, sheetOverlay;
    private TextView[] sheetChips;
    private TextView   tvSheetLabel, sheetTvDuration;
    private boolean    sheetVisible  = false;
    private String     selectedType  = SlideshowWallpaperService.TRANSITION_FADE;
    private int        durationMs    = SlideshowWallpaperService.DEFAULT_TRANSITION_MS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ThemeManager.apply(this);

        // Main views
        tvPhotoCount     = findViewById(R.id.tv_photo_count);
        tvIntervalStatus = findViewById(R.id.tv_interval_status);
        recyclerDeck     = findViewById(R.id.recycler_deck);
        emptyState       = findViewById(R.id.empty_state);

        deckAdapter = new DeckAdapter(this, deckUris);
        recyclerDeck.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerDeck.setAdapter(deckAdapter);

        // Bottom sheet views
        bottomSheet      = findViewById(R.id.bottom_sheet);
        sheetOverlay     = findViewById(R.id.sheet_overlay);
        tvSheetLabel     = findViewById(R.id.tv_sheet_transition_label);
        sheetTvDuration  = findViewById(R.id.sheet_tv_duration);

        sheetChips = new TextView[SHEET_CHIP_IDS.length];
        for (int i = 0; i < SHEET_CHIP_IDS.length; i++) sheetChips[i] = findViewById(SHEET_CHIP_IDS[i]);

        // Load saved transition
        SharedPreferences prefs = getSharedPreferences(
                SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE);
        selectedType = prefs.getString(SlideshowWallpaperService.KEY_TRANSITION,
                SlideshowWallpaperService.TRANSITION_FADE);
        durationMs   = prefs.getInt(SlideshowWallpaperService.KEY_TRANSITION_DURATION,
                SlideshowWallpaperService.DEFAULT_TRANSITION_MS);

        updateSheetChips();
        updateSheetLabel();

        // Nav — Apply opens system wallpaper picker directly
        findViewById(R.id.nav_set_wallpaper).setOnClickListener(v -> {
            Intent intent = new Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new android.content.ComponentName(this, SlideshowWallpaperService.class));
            startActivity(intent);
        });
        findViewById(R.id.nav_wallpapers).setOnClickListener(v -> { });
        findViewById(R.id.nav_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // Header settings button
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // Import
        findViewById(R.id.btn_add_more).setOnClickListener(v ->
                startActivity(new Intent(this, WallpaperSettingsActivity.class)));
        findViewById(R.id.btn_import).setOnClickListener(v ->
                startActivity(new Intent(this, WallpaperSettingsActivity.class)));

        // Sheet close
        findViewById(R.id.btn_sheet_close).setOnClickListener(v -> hideSheet());
        sheetOverlay.setOnClickListener(v -> hideSheet());

        // Sheet chips
        for (int i = 0; i < sheetChips.length; i++) {
            final String type = TRANSITION_TYPES[i];
            sheetChips[i].setOnClickListener(v -> {
                selectedType = type;
                saveTransition();
                updateSheetChips();
                updateSheetLabel();
            });
        }

        // Sheet duration stepper
        findViewById(R.id.sheet_btn_minus).setOnClickListener(v -> {
            if (durationMs > 100) {
                durationMs = Math.max(100, durationMs - 100);
                saveTransition();
                updateSheetLabel();
            }
        });
        findViewById(R.id.sheet_btn_plus).setOnClickListener(v -> {
            if (durationMs < 2000) {
                durationMs = Math.min(2000, durationMs + 100);
                saveTransition();
                updateSheetLabel();
            }
        });

        // Sheet apply button
        findViewById(R.id.sheet_btn_apply).setOnClickListener(v -> {
            hideSheet();
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(this, SlideshowWallpaperService.class));
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();;
        ThemeManager.apply(this);
        refreshDeck();
        refreshIntervalLabel();
        // Sync in case changed in Settings
        SharedPreferences prefs = getSharedPreferences(
                SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE);
        selectedType = prefs.getString(SlideshowWallpaperService.KEY_TRANSITION,
                SlideshowWallpaperService.TRANSITION_FADE);
        durationMs   = prefs.getInt(SlideshowWallpaperService.KEY_TRANSITION_DURATION,
                SlideshowWallpaperService.DEFAULT_TRANSITION_MS);
        updateSheetChips();
        updateSheetLabel();
    }

    @Override
    public void onBackPressed() {
        if (sheetVisible) { hideSheet(); return; }
        super.onBackPressed();
    }

    // ── Sheet animation ───────────────────────────────────────────────────────

    private void showSheet() {
        sheetOverlay.setVisibility(View.VISIBLE);
        sheetOverlay.animate().alpha(0.5f).setDuration(250).start();
        bottomSheet.animate()
                .translationY(0)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        sheetVisible = true;
    }

    private void hideSheet() {
        sheetOverlay.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> sheetOverlay.setVisibility(View.GONE)).start();
        bottomSheet.animate()
                .translationY(2000)
                .setDuration(260)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        sheetVisible = false;
    }

    // ── Sheet helpers ─────────────────────────────────────────────────────────

    private void saveTransition() {
        getSharedPreferences(SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(SlideshowWallpaperService.KEY_TRANSITION, selectedType)
                .putInt(SlideshowWallpaperService.KEY_TRANSITION_DURATION, durationMs)
                .apply();
    }

    private void updateSheetChips() {
        for (int i = 0; i < sheetChips.length; i++) {
            boolean active = TRANSITION_TYPES[i].equals(selectedType);
            sheetChips[i].setBackgroundResource(
                    active ? R.drawable.settings_chip_active : R.drawable.settings_chip_inactive);
            sheetChips[i].setTextColor(getColor(
                    active ? R.color.chip_on_active : R.color.chip_muted));
        }
    }

    private void updateSheetLabel() {
        sheetTvDuration.setText(durationMs + "ms");
        String name = selectedType.equals(SlideshowWallpaperService.TRANSITION_NONE)
                ? "None"
                : selectedType.substring(0, 1).toUpperCase() + selectedType.substring(1);
        tvSheetLabel.setText(name + " · " + durationMs + "ms");
    }

    // ── Deck + interval ───────────────────────────────────────────────────────

    private void refreshDeck() {
        SharedPreferences prefs = getSharedPreferences(
                SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE);
        String raw = prefs.getString(SlideshowWallpaperService.KEY_IMAGE_URIS, "");
        deckUris.clear();
        if (raw != null && !raw.isEmpty()) {
            for (String s : raw.split("\\|\\|\\|")) {
                if (!s.trim().isEmpty()) deckUris.add(s.trim());
            }
        }
        deckAdapter.notifyDataSetChanged();
        int n = deckUris.size();
        if (n == 0) {
            recyclerDeck.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            tvPhotoCount.setText("No photos selected");
        } else {
            recyclerDeck.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            tvPhotoCount.setText(n + " photo" + (n == 1 ? "" : "s") + " in deck");
        }
    }

    private void refreshIntervalLabel() {
        SharedPreferences prefs = getSharedPreferences(
                SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE);
        long ms = prefs.getLong(KEY_INTERVAL_MS, 5000);
        String label;
        if (ms < 60000)        label = (ms / 1000) + "s";
        else if (ms < 3600000) label = (ms / 60000) + "m";
        else                   label = (ms / 3600000) + "h";
        tvIntervalStatus.setText("· Every " + label);
    }
}

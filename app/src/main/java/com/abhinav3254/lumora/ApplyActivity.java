package com.abhinav3254.lumora;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ApplyActivity extends AppCompatActivity {

    private static final String[] TRANSITION_TYPES = {
            SlideshowWallpaperService.TRANSITION_NONE,
            SlideshowWallpaperService.TRANSITION_FADE,
            SlideshowWallpaperService.TRANSITION_SLIDE,
            SlideshowWallpaperService.TRANSITION_ZOOM
    };
    private static final int[] CHIP_IDS = {
            R.id.chip_none, R.id.chip_fade, R.id.chip_slide, R.id.chip_zoom
    };

    private TextView[] chips;
    private TextView   tvCurrentTransition, tvDurationVal;
    private int        durationMs   = SlideshowWallpaperService.DEFAULT_TRANSITION_MS;
    private String     selectedType = SlideshowWallpaperService.TRANSITION_FADE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apply);

        tvCurrentTransition = findViewById(R.id.tv_current_transition);
        tvDurationVal       = findViewById(R.id.tv_duration_val);

        chips = new TextView[CHIP_IDS.length];
        for (int i = 0; i < CHIP_IDS.length; i++) chips[i] = findViewById(CHIP_IDS[i]);

        // Load saved
        SharedPreferences prefs = getSharedPreferences(
                SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE);
        selectedType = prefs.getString(SlideshowWallpaperService.KEY_TRANSITION,
                SlideshowWallpaperService.TRANSITION_FADE);
        durationMs = prefs.getInt(SlideshowWallpaperService.KEY_TRANSITION_DURATION,
                SlideshowWallpaperService.DEFAULT_TRANSITION_MS);

        updateChips();
        updateLabel();

        // Chip taps — save + update label instantly
        for (int i = 0; i < chips.length; i++) {
            final String type = TRANSITION_TYPES[i];
            chips[i].setOnClickListener(v -> {
                selectedType = type;
                save();
                updateChips();
                updateLabel();
            });
        }

        // Duration stepper — save + update label instantly
        findViewById(R.id.btn_dur_minus).setOnClickListener(v -> {
            if (durationMs > 100) {
                durationMs = Math.max(100, durationMs - 100);
                save();
                updateLabel();
            }
        });
        findViewById(R.id.btn_dur_plus).setOnClickListener(v -> {
            if (durationMs < 2000) {
                durationMs = Math.min(2000, durationMs + 100);
                save();
                updateLabel();
            }
        });

        // Set as wallpaper
        findViewById(R.id.btn_apply_wallpaper).setOnClickListener(v -> {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(this, SlideshowWallpaperService.class));
            startActivity(intent);
        });

        // Nav
        findViewById(R.id.nav_wallpapers).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        findViewById(R.id.nav_apply).setOnClickListener(v -> { });
        findViewById(R.id.nav_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void save() {
        getSharedPreferences(SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(SlideshowWallpaperService.KEY_TRANSITION, selectedType)
                .putInt(SlideshowWallpaperService.KEY_TRANSITION_DURATION, durationMs)
                .apply();
    }

    private void updateChips() {
        for (int i = 0; i < chips.length; i++) {
            boolean active = TRANSITION_TYPES[i].equals(selectedType);
            chips[i].setBackgroundResource(
                    active ? R.drawable.settings_chip_active : R.drawable.settings_chip_inactive);
            chips[i].setTextColor(getColor(
                    active ? R.color.chip_on_active : R.color.chip_muted));
        }
    }

    private void updateLabel() {
        // Update duration stepper display
        tvDurationVal.setText(durationMs + "ms");

        // Update label under phone — shows current selection instantly
        String name = selectedType.substring(0, 1).toUpperCase() + selectedType.substring(1);
        if (selectedType.equals(SlideshowWallpaperService.TRANSITION_NONE)) {
            tvCurrentTransition.setText("NO TRANSITION");
        } else {
            tvCurrentTransition.setText(name + " · " + durationMs + "ms");
        }
    }
}

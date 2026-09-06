package com.abhinav3254.lumora;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    public static final String KEY_THEME = "selected_theme";

    private static final long[] INTERVAL_VALUES  = {5000, 15000, 60000, 300000, 3600000};
    private static final String[] INTERVAL_LABELS = {"5s", "15s", "1m", "5m", "1h"};

    private int[] chipIds = {R.id.chip_5s, R.id.chip_15s, R.id.chip_1m, R.id.chip_5m, R.id.chip_1h};
    private TextView[] chips;

    // Theme containers + their check indicators
    private int[] themeContainerIds = {
            R.id.theme_stone, R.id.theme_slate, R.id.theme_forest,
            R.id.theme_ash, R.id.theme_dusk, R.id.theme_ember
    };
    private int[] themeCheckIds = {
            R.id.theme_stone_check, R.id.theme_slate_check, R.id.theme_forest_check,
            R.id.theme_ash_check, R.id.theme_dusk_check, R.id.theme_ember_check
    };
    private static final String[] THEME_NAMES = {
            "stone", "slate", "forest", "ash", "dusk", "ember"
    };

    private TextView tvCadenceLabel, tvStepperDesc, tvStepperVal, tvDeckInfo, tvSaveConfirm, tvThemeNote;
    private long selectedInterval = 5000;
    private int stepperSeconds = 5;
    private String selectedTheme = "stone";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        tvCadenceLabel = findViewById(R.id.tv_cadence_label);
        tvStepperDesc  = findViewById(R.id.tv_stepper_desc);
        tvStepperVal   = findViewById(R.id.tv_stepper_val);
        tvDeckInfo     = findViewById(R.id.tv_deck_info);
        tvSaveConfirm  = findViewById(R.id.tv_save_confirm);
        tvThemeNote    = findViewById(R.id.tv_theme_note);

        chips = new TextView[chipIds.length];
        for (int i = 0; i < chipIds.length; i++) chips[i] = findViewById(chipIds[i]);

        SharedPreferences prefs = getSharedPreferences(
                SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE);
        selectedInterval = prefs.getLong(MainActivity.KEY_INTERVAL_MS, 5000);
        stepperSeconds   = (int) Math.min(selectedInterval / 1000, 3600);
        selectedTheme    = prefs.getString(KEY_THEME, "stone");

        updateChips();
        updateStepperDisplay();
        updateThemeSelection();
        refreshDeckInfo(prefs);

        // Interval chips
        for (int i = 0; i < chips.length; i++) {
            final int idx = i;
            chips[i].setOnClickListener(v -> {
                selectedInterval = INTERVAL_VALUES[idx];
                stepperSeconds   = (int) (selectedInterval / 1000);
                updateChips();
                updateStepperDisplay();
                tvCadenceLabel.setText(INTERVAL_LABELS[idx]);
            });
        }

        // Stepper
        findViewById(R.id.btn_minus).setOnClickListener(v -> {
            if (stepperSeconds > 3) {
                stepperSeconds--;
                selectedInterval = stepperSeconds * 1000L;
                updateStepperDisplay();
                deselectAllChips();
                tvCadenceLabel.setText(stepperSeconds + "s");
            }
        });
        findViewById(R.id.btn_plus).setOnClickListener(v -> {
            if (stepperSeconds < 3600) {
                stepperSeconds++;
                selectedInterval = stepperSeconds * 1000L;
                updateStepperDisplay();
                deselectAllChips();
                tvCadenceLabel.setText(stepperSeconds + "s");
            }
        });

        // Theme selection
        for (int i = 0; i < themeContainerIds.length; i++) {
            final String name = THEME_NAMES[i];
            findViewById(themeContainerIds[i]).setOnClickListener(v -> {
                selectedTheme = name;
                updateThemeSelection();
                tvThemeNote.setVisibility(View.VISIBLE);
            });
        }

        // Data rows
        findViewById(R.id.row_clear_deck).setOnClickListener(v -> {
            getSharedPreferences(SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE)
                    .edit().remove(SlideshowWallpaperService.KEY_IMAGE_URIS).apply();
            tvDeckInfo.setText("0 wallpapers ready");
            Toast.makeText(this, "Deck cleared", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.row_reset_splash).setOnClickListener(v -> {
            getSharedPreferences(SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE)
                    .edit().remove("seen_splash").apply();
            Toast.makeText(this, "Intro will show on next launch", Toast.LENGTH_SHORT).show();
        });

        // About rows
        findViewById(R.id.row_privacy).setOnClickListener(v ->
                Toast.makeText(this, "All data is stored locally on your device", Toast.LENGTH_LONG).show());

        findViewById(R.id.row_rate).setOnClickListener(v ->
                Toast.makeText(this, "Thanks for using Lumora!", Toast.LENGTH_SHORT).show());

        findViewById(R.id.row_share).setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT,
                    "Check out Lumora — a clean live wallpaper slideshow app for Android!");
            startActivity(Intent.createChooser(share, "Share Lumora"));
        });

        // Save
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            getSharedPreferences(SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putLong(MainActivity.KEY_INTERVAL_MS, selectedInterval)
                    .putString(KEY_THEME, selectedTheme)
                    .apply();
            tvSaveConfirm.setVisibility(View.VISIBLE);
            tvSaveConfirm.postDelayed(() -> tvSaveConfirm.setVisibility(View.GONE), 2400);
        });

        // Back
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void updateChips() {
        for (int i = 0; i < chips.length; i++) {
            boolean active = INTERVAL_VALUES[i] == selectedInterval;
            chips[i].setBackgroundResource(active
                    ? R.drawable.settings_chip_active : R.drawable.settings_chip_inactive);
            chips[i].setTextColor(getColor(active
                    ? R.color.chip_on_active : R.color.chip_muted));
            if (active) tvCadenceLabel.setText(INTERVAL_LABELS[i]);
        }
    }

    private void deselectAllChips() {
        for (TextView c : chips) {
            c.setBackgroundResource(R.drawable.settings_chip_inactive);
            c.setTextColor(getColor(R.color.chip_muted));
        }
    }

    private void updateStepperDisplay() {
        String val = stepperSeconds < 60 ? stepperSeconds + "s"
                : stepperSeconds < 3600 ? (stepperSeconds / 60) + "m"
                : (stepperSeconds / 3600) + "h";
        tvStepperVal.setText(val);
        tvStepperDesc.setText("Changes wallpaper every " + val);
    }

    private void updateThemeSelection() {
        for (int i = 0; i < THEME_NAMES.length; i++) {
            int visibility = THEME_NAMES[i].equals(selectedTheme) ? View.VISIBLE : View.GONE;
            findViewById(themeCheckIds[i]).setVisibility(visibility);
        }
    }

    private void refreshDeckInfo(SharedPreferences prefs) {
        String raw = prefs.getString(SlideshowWallpaperService.KEY_IMAGE_URIS, "");
        int count = (raw == null || raw.isEmpty()) ? 0 : raw.split("\\|\\|\\|").length;
        tvDeckInfo.setText(count + " wallpaper" + (count == 1 ? "" : "s") + " ready");
    }
}

package com.abhinav3254.lumora;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    public static final String KEY_THEME           = "selected_theme";
    public static final String KEY_PAUSE_LOW_BAT   = "pause_low_battery";
    public static final String KEY_PAUSE_LOW_POWER  = "pause_low_power_mode";
    public static final String KEY_SHAKE_TO_CYCLE   = "shake_to_cycle";
    public static final String KEY_SHUFFLE          = "shuffle_mode";

    private static final long[]   INTERVAL_VALUES = {5000, 15000, 60000, 300000, 3600000};
    private static final String[] INTERVAL_LABELS = {"5s", "15s", "1m", "5m", "1h"};

    private final int[] chipIds = {
            R.id.chip_5s, R.id.chip_15s, R.id.chip_1m, R.id.chip_5m, R.id.chip_1h
    };
    private TextView[] chips;

    private TextView tvCadenceLabel, tvStepperDesc, tvStepperVal, tvDeckInfo, tvSaveConfirm;
    private SwitchMaterial toggleLowBattery, toggleLowPowerMode, toggleShake, toggleShuffle;

    private long selectedInterval = 5000;
    private int  stepperSeconds   = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        tvCadenceLabel = findViewById(R.id.tv_cadence_label);
        tvStepperDesc  = findViewById(R.id.tv_stepper_desc);
        tvStepperVal   = findViewById(R.id.tv_stepper_val);
        tvDeckInfo     = findViewById(R.id.tv_deck_info);
        tvSaveConfirm  = findViewById(R.id.tv_save_confirm);

        toggleLowBattery   = findViewById(R.id.toggle_low_battery);
        toggleLowPowerMode = findViewById(R.id.toggle_low_power_mode);
        toggleShake        = findViewById(R.id.toggle_shake);
        toggleShuffle      = findViewById(R.id.toggle_shuffle);

        chips = new TextView[chipIds.length];
        for (int i = 0; i < chipIds.length; i++) chips[i] = findViewById(chipIds[i]);

        SharedPreferences prefs = getSharedPreferences(
                SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE);

        selectedInterval = prefs.getLong(MainActivity.KEY_INTERVAL_MS, 5000);
        stepperSeconds   = (int) Math.min(selectedInterval / 1000, 3600);

        toggleLowBattery.setChecked(prefs.getBoolean(KEY_PAUSE_LOW_BAT, true));
        toggleLowPowerMode.setChecked(prefs.getBoolean(KEY_PAUSE_LOW_POWER, true));
        toggleShake.setChecked(prefs.getBoolean(KEY_SHAKE_TO_CYCLE, false));
        toggleShuffle.setChecked(prefs.getBoolean(KEY_SHUFFLE, false));

        updateChips();
        updateStepperDisplay();
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

        // Schedule screen
        findViewById(R.id.row_schedule).setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleActivity.class)));

        // Data rows
        findViewById(R.id.row_clear_deck).setOnClickListener(v -> {
            prefs.edit().remove(SlideshowWallpaperService.KEY_IMAGE_URIS).apply();
            tvDeckInfo.setText("0 wallpapers ready");
            Toast.makeText(this, "Deck cleared", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.row_reset_splash).setOnClickListener(v -> {
            prefs.edit().remove("seen_splash").apply();
            Toast.makeText(this, "Intro will show on next launch", Toast.LENGTH_SHORT).show();
        });

        // Share
        findViewById(R.id.row_share).setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT,
                    "Check out Lumora — a clean live wallpaper slideshow app for Android!");
            startActivity(Intent.createChooser(share, "Share Lumora"));
        });

        // Save
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            prefs.edit()
                    .putLong(MainActivity.KEY_INTERVAL_MS, selectedInterval)
                    .putBoolean(KEY_PAUSE_LOW_BAT,   toggleLowBattery.isChecked())
                    .putBoolean(KEY_PAUSE_LOW_POWER,  toggleLowPowerMode.isChecked())
                    .putBoolean(KEY_SHAKE_TO_CYCLE,   toggleShake.isChecked())
                    .putBoolean(KEY_SHUFFLE,           toggleShuffle.isChecked())
                    .apply();
            tvSaveConfirm.setVisibility(View.VISIBLE);
            tvSaveConfirm.postDelayed(() -> tvSaveConfirm.setVisibility(View.GONE), 2400);
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void updateChips() {
        for (int i = 0; i < chips.length; i++) {
            boolean active = INTERVAL_VALUES[i] == selectedInterval;
            chips[i].setBackgroundResource(
                    active ? R.drawable.settings_chip_active : R.drawable.settings_chip_inactive);
            chips[i].setTextColor(getColor(
                    active ? R.color.chip_on_active : R.color.chip_muted));
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
                : stepperSeconds < 3600  ? (stepperSeconds / 60) + "m"
                :                          (stepperSeconds / 3600) + "h";
        tvStepperVal.setText(val);
        tvStepperDesc.setText("Changes wallpaper every " + val);
    }

    private void refreshDeckInfo(SharedPreferences prefs) {
        String raw   = prefs.getString(SlideshowWallpaperService.KEY_IMAGE_URIS, "");
        int    count = (raw == null || raw.isEmpty()) ? 0 : raw.split("\\|\\|\\|").length;
        tvDeckInfo.setText(count + " wallpaper" + (count == 1 ? "" : "s") + " ready");
    }
}

package com.abhinav3254.lumora;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    public static final String KEY_INTERVAL_MS = "interval_ms";

    private TextView chip5s, chip10s, chip30s, chip1m, chip5m;
    private TextView tvPhotoCount, tvIntervalStatus;

    private long selectedInterval = 5000;

    private final long[] intervals = {5000, 10000, 30000, 60000, 300000};
    private final String[] labels = {"5s", "10s", "30s", "1 min", "5 min"};
    private TextView[] chips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvPhotoCount = findViewById(R.id.tv_photo_count);
        tvIntervalStatus = findViewById(R.id.tv_interval_status);

        chip5s  = findViewById(R.id.chip_5s);
        chip10s = findViewById(R.id.chip_10s);
        chip30s = findViewById(R.id.chip_30s);
        chip1m  = findViewById(R.id.chip_1m);
        chip5m  = findViewById(R.id.chip_5m);
        chips = new TextView[]{chip5s, chip10s, chip30s, chip1m, chip5m};

        // Load saved interval
        SharedPreferences prefs = getSharedPreferences(
                SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE);
        selectedInterval = prefs.getLong(KEY_INTERVAL_MS, 5000);
        updateChipSelection();

        // Chip click listeners
        for (int i = 0; i < chips.length; i++) {
            final long iv = intervals[i];
            chips[i].setOnClickListener(v -> {
                selectedInterval = iv;
                saveInterval();
                updateChipSelection();
            });
        }

        // Select photos row
        findViewById(R.id.row_select_photos).setOnClickListener(v -> {
            startActivity(new Intent(this, WallpaperSettingsActivity.class));
        });

        // Set wallpaper row
        findViewById(R.id.row_set_wallpaper).setOnClickListener(v -> {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(this, SlideshowWallpaperService.class));
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPhotoCount();
    }

    private void refreshPhotoCount() {
        SharedPreferences prefs = getSharedPreferences(
                SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE);
        String raw = prefs.getString(SlideshowWallpaperService.KEY_IMAGE_URIS, "");
        int count = 0;
        if (raw != null && !raw.isEmpty()) {
            count = raw.split("\\|\\|\\|").length;
        }
        if (count == 0) {
            tvPhotoCount.setText("No photos selected");
        } else {
            tvPhotoCount.setText(count + " photo" + (count == 1 ? "" : "s") + " selected");
        }
    }

    private void saveInterval() {
        getSharedPreferences(SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putLong(KEY_INTERVAL_MS, selectedInterval)
                .apply();
    }

    private void updateChipSelection() {
        for (int i = 0; i < chips.length; i++) {
            boolean selected = intervals[i] == selectedInterval;
            chips[i].setBackgroundResource(
                    selected ? R.drawable.chip_selected : R.drawable.chip_unselected);
            chips[i].setTextColor(getColor(selected ? android.R.color.white : R.color.chip_muted));
        }

        // Update status label
        String label = labels[0];
        for (int i = 0; i < intervals.length; i++) {
            if (intervals[i] == selectedInterval) { label = labels[i]; break; }
        }
        tvIntervalStatus.setText("Changes every " + label);
    }
}
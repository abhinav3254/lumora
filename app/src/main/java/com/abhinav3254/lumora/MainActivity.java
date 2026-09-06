package com.abhinav3254.lumora;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String KEY_INTERVAL_MS = "interval_ms";

    private TextView   tvPhotoCount, tvIntervalStatus;
    private RecyclerView recyclerDeck;
    private View         emptyState;
    private DeckAdapter  deckAdapter;
    private final List<String> deckUris = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ThemeManager.apply(this);

        tvPhotoCount     = findViewById(R.id.tv_photo_count);
        tvIntervalStatus = findViewById(R.id.tv_interval_status);
        recyclerDeck     = findViewById(R.id.recycler_deck);
        emptyState       = findViewById(R.id.empty_state);

        deckAdapter = new DeckAdapter(this, deckUris);
        recyclerDeck.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerDeck.setAdapter(deckAdapter);

        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btn_add_more).setOnClickListener(v ->
                startActivity(new Intent(this, WallpaperSettingsActivity.class)));
        findViewById(R.id.btn_import).setOnClickListener(v ->
                startActivity(new Intent(this, WallpaperSettingsActivity.class)));

        findViewById(R.id.nav_set_wallpaper).setOnClickListener(v ->
                startActivity(new Intent(this, ApplyActivity.class)));
        findViewById(R.id.nav_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.nav_wallpapers).setOnClickListener(v -> { });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeManager.apply(this);
        refreshDeck();
        refreshIntervalLabel();
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

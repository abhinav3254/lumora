package com.abhinav3254.lumora;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class WallpaperSettingsActivity extends AppCompatActivity {

    private static final String[] TRANSITION_TYPES = {
            SlideshowWallpaperService.TRANSITION_NONE,
            SlideshowWallpaperService.TRANSITION_FADE,
            SlideshowWallpaperService.TRANSITION_SLIDE,
            SlideshowWallpaperService.TRANSITION_ZOOM
    };
    private static final int[] CHIP_IDS = {
            R.id.ws_chip_none, R.id.ws_chip_fade,
            R.id.ws_chip_slide, R.id.ws_chip_zoom
    };

    private final List<Uri> selectedUris = new ArrayList<>();
    private TextView   tvCount;
    private SelectedPhotosAdapter adapter;

    // Transition views
    private TextView[] chips;
    private TextView   tvTransitionLabel, tvDuration;
    private String     selectedTransition = SlideshowWallpaperService.TRANSITION_FADE;
    private int        durationMs         = SlideshowWallpaperService.DEFAULT_TRANSITION_MS;

    private final ActivityResultLauncher<Intent> pickImagesLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            selectedUris.clear();
                            Intent data = result.getData();
                            if (data.getClipData() != null) {
                                int limit = Math.min(data.getClipData().getItemCount(), 20);
                                for (int i = 0; i < limit; i++) {
                                    Uri uri = data.getClipData().getItemAt(i).getUri();
                                    try { getContentResolver().takePersistableUriPermission(
                                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    } catch (Exception ignored) {}
                                    selectedUris.add(uri);
                                }
                            } else if (data.getData() != null) {
                                Uri uri = data.getData();
                                try { getContentResolver().takePersistableUriPermission(
                                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                } catch (Exception ignored) {}
                                selectedUris.add(uri);
                            }
                            adapter.notifyDataSetChanged();
                            updateCount();
                        }
                    });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) openPhotoPicker();
                        else Toast.makeText(this, "Permission needed to access photos",
                                Toast.LENGTH_SHORT).show();
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallpaper_settings);

        tvCount = findViewById(R.id.tv_count);
        RecyclerView recyclerView = findViewById(R.id.recycler_photos);
        adapter = new SelectedPhotosAdapter(this, selectedUris);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerView.setAdapter(adapter);

        // Transition views
        tvTransitionLabel = findViewById(R.id.tv_transition_label);
        tvDuration        = findViewById(R.id.ws_tv_duration);
        chips             = new TextView[CHIP_IDS.length];
        for (int i = 0; i < CHIP_IDS.length; i++) chips[i] = findViewById(CHIP_IDS[i]);

        // Load saved transition
        SharedPreferences prefs = getSharedPreferences(
                SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE);
        selectedTransition = prefs.getString(SlideshowWallpaperService.KEY_TRANSITION,
                SlideshowWallpaperService.TRANSITION_FADE);
        durationMs = prefs.getInt(SlideshowWallpaperService.KEY_TRANSITION_DURATION,
                SlideshowWallpaperService.DEFAULT_TRANSITION_MS);

        updateChips();
        updateTransitionLabel();

        // Chip clicks — save immediately so wallpaper picks it up live
        for (int i = 0; i < chips.length; i++) {
            final String type = TRANSITION_TYPES[i];
            chips[i].setOnClickListener(v -> {
                selectedTransition = type;
                saveTransition();
                updateChips();
                updateTransitionLabel();
            });
        }

        // Duration stepper
        findViewById(R.id.ws_btn_minus).setOnClickListener(v -> {
            if (durationMs > 100) {
                durationMs = Math.max(100, durationMs - 100);
                saveTransition();
                updateTransitionLabel();
            }
        });
        findViewById(R.id.ws_btn_plus).setOnClickListener(v -> {
            if (durationMs < 2000) {
                durationMs = Math.min(2000, durationMs + 100);
                saveTransition();
                updateTransitionLabel();
            }
        });

        loadSavedUris();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_pick_photos).setOnClickListener(v -> checkPermissionAndPick());
        findViewById(R.id.btn_save).setOnClickListener(v -> saveAndFinish());
    }

    // ── Transition helpers ────────────────────────────────────────────────────

    private void saveTransition() {
        getSharedPreferences(SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(SlideshowWallpaperService.KEY_TRANSITION, selectedTransition)
                .putInt(SlideshowWallpaperService.KEY_TRANSITION_DURATION, durationMs)
                .apply();
    }

    private void updateChips() {
        for (int i = 0; i < chips.length; i++) {
            boolean active = TRANSITION_TYPES[i].equals(selectedTransition);
            chips[i].setBackgroundResource(
                    active ? R.drawable.settings_chip_active : R.drawable.settings_chip_inactive);
            chips[i].setTextColor(getColor(
                    active ? R.color.chip_on_active : R.color.chip_muted));
        }
    }

    private void updateTransitionLabel() {
        tvDuration.setText(durationMs + "ms");
        String name = selectedTransition.equals(SlideshowWallpaperService.TRANSITION_NONE)
                ? "None"
                : selectedTransition.substring(0, 1).toUpperCase() + selectedTransition.substring(1);
        tvTransitionLabel.setText(name + " · " + durationMs + "ms");
    }

    // ── Photo picker ──────────────────────────────────────────────────────────

    private void checkPermissionAndPick() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED) {
            openPhotoPicker();
        } else {
            permissionLauncher.launch(permission);
        }
    }

    private void openPhotoPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        pickImagesLauncher.launch(intent);
    }

    private void loadSavedUris() {
        SharedPreferences prefs = getSharedPreferences(
                SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE);
        String raw = prefs.getString(SlideshowWallpaperService.KEY_IMAGE_URIS, "");
        if (raw != null && !raw.isEmpty()) {
            for (String p : raw.split("\\|\\|\\|")) {
                if (!p.trim().isEmpty()) selectedUris.add(Uri.parse(p.trim()));
            }
            adapter.notifyDataSetChanged();
            updateCount();
        }
    }

    private void saveAndFinish() {
        if (selectedUris.isEmpty()) {
            Toast.makeText(this, "Select at least 1 photo", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selectedUris.size(); i++) {
            sb.append(selectedUris.get(i).toString());
            if (i < selectedUris.size() - 1)
                sb.append(SlideshowWallpaperService.URI_SEPARATOR);
        }
        getSharedPreferences(SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(SlideshowWallpaperService.KEY_IMAGE_URIS, sb.toString())
                .apply();
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void updateCount() {
        int n = selectedUris.size();
        tvCount.setText(n + " photo" + (n == 1 ? "" : "s") + " selected");
    }
}

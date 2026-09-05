package com.abhinav3254.lumora;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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

    private List<Uri> selectedUris = new ArrayList<>();
    private TextView tvCount;
    private Button btnPick, btnSave;
    private RecyclerView recyclerView;
    private SelectedPhotosAdapter adapter;

    // Launcher to pick multiple images
    private final ActivityResultLauncher<Intent> pickImagesLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            selectedUris.clear();
                            Intent data = result.getData();

                            if (data.getClipData() != null) {
                                // Multiple images selected
                                int count = data.getClipData().getItemCount();
                                int limit = Math.min(count, 20);
                                for (int i = 0; i < limit; i++) {
                                    Uri uri = data.getClipData().getItemAt(i).getUri();
                                    // Persist URI permission so wallpaper service can access it
                                    getContentResolver().takePersistableUriPermission(
                                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    selectedUris.add(uri);
                                }
                            } else if (data.getData() != null) {
                                // Single image selected
                                Uri uri = data.getData();
                                getContentResolver().takePersistableUriPermission(
                                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                selectedUris.add(uri);
                            }

                            adapter.notifyDataSetChanged();
                            updateCount();
                        }
                    });

    // Permission launcher
    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            openPhotoPicker();
                        } else {
                            Toast.makeText(this,
                                    "Permission needed to access photos",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallpaper_settings);

        tvCount = findViewById(R.id.tv_count);
        btnPick = findViewById(R.id.btn_pick_photos);
        btnSave = findViewById(R.id.btn_save);
        recyclerView = findViewById(R.id.recycler_photos);

        adapter = new SelectedPhotosAdapter(this, selectedUris);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerView.setAdapter(adapter);

        // Load previously saved URIs
        loadSavedUris();

        btnPick.setOnClickListener(v -> checkPermissionAndPick());

        btnSave.setOnClickListener(v -> saveAndFinish());
    }

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
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        pickImagesLauncher.launch(intent);
    }

    private void loadSavedUris() {
        SharedPreferences prefs = getSharedPreferences(
                SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE);
        String raw = prefs.getString(SlideshowWallpaperService.KEY_IMAGE_URIS, "");
        if (raw != null && !raw.isEmpty()) {
            String[] parts = raw.split("\\|\\|\\|");
            for (String p : parts) {
                if (!p.trim().isEmpty()) {
                    selectedUris.add(Uri.parse(p.trim()));
                }
            }
            adapter.notifyDataSetChanged();
            updateCount();
        }
    }

    private void saveAndFinish() {
        if (selectedUris.isEmpty()) {
            Toast.makeText(this, "Please select at least 1 photo", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selectedUris.size(); i++) {
            sb.append(selectedUris.get(i).toString());
            if (i < selectedUris.size() - 1) {
                sb.append(SlideshowWallpaperService.URI_SEPARATOR);
            }
        }

        SharedPreferences prefs = getSharedPreferences(
                SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(SlideshowWallpaperService.KEY_IMAGE_URIS, sb.toString()).apply();

        Toast.makeText(this, "Photos saved! Wallpaper will update shortly.", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void updateCount() {
        tvCount.setText(selectedUris.size() + " photo(s) selected");
    }
}
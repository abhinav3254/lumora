package com.abhinav3254.lumora;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button btnPickPhotos = findViewById(R.id.btn_pick_photos);
        Button btnSetWallpaper = findViewById(R.id.btn_set_wallpaper);

        // Opens photo picker / settings screen
        btnPickPhotos.setOnClickListener(v -> {
            Intent intent = new Intent(this, WallpaperSettingsActivity.class);
            startActivity(intent);
        });

        // Opens Android's live wallpaper picker pointing to Lumora
        btnSetWallpaper.setOnClickListener(v -> {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(this, SlideshowWallpaperService.class));
            startActivity(intent);
        });
    }
}
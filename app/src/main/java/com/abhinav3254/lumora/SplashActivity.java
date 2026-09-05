package com.abhinav3254.lumora;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final String KEY_SEEN_SPLASH = "seen_splash";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If user has seen splash before, go straight to MainActivity
        SharedPreferences prefs = getSharedPreferences(
                SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SEEN_SPLASH, false)) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_splash);

        findViewById(R.id.btn_get_started).setOnClickListener(v -> {
            // Mark splash as seen
            prefs.edit().putBoolean(KEY_SEEN_SPLASH, true).apply();
            goToMain();
        });
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
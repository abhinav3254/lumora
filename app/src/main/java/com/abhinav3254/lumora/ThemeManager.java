package com.abhinav3254.lumora;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

public class ThemeManager {

    public static final String THEME_STONE  = "stone";
    public static final String THEME_SLATE  = "slate";
    public static final String THEME_FOREST = "forest";
    public static final String THEME_ASH    = "ash";
    public static final String THEME_DUSK   = "dusk";
    public static final String THEME_EMBER  = "ember";

    // bgBase, bgCard, bgSurface, accent, textPrimary, textMuted, textDim, navBg
    public static final int[][] PALETTES = {
        { 0xFF161311, 0xFF221f1d, 0xFF1e1b19, 0xFFccc5c1, 0xFFe9e1dd, 0xFF625d5a, 0xFF4c4640, 0xFF100e0c },
        { 0xFF0d1117, 0xFF161b22, 0xFF0d1117, 0xFF58a6ff, 0xFFe6edf3, 0xFF8b949e, 0xFF30363d, 0xFF010409 },
        { 0xFF0d1a0f, 0xFF1a2e1c, 0xFF0f1f11, 0xFF4caf50, 0xFFc8e6c9, 0xFF66bb6a, 0xFF2e472f, 0xFF071209 },
        { 0xFF111111, 0xFF1c1c1c, 0xFF141414, 0xFFe0e0e0, 0xFFf5f5f5, 0xFF9e9e9e, 0xFF424242, 0xFF090909 },
        { 0xFF1a0a2e, 0xFF2d1b4e, 0xFF1f1036, 0xFFb39ddb, 0xFFede7f6, 0xFF9575cd, 0xFF4a148c, 0xFF0d0518 },
        { 0xFF1a0800, 0xFF2e1200, 0xFF1f0d00, 0xFFff6d00, 0xFFffe0b2, 0xFFff9800, 0xFF4e2000, 0xFF0f0400 },
    };

    public static final String[] NAMES = {
            THEME_STONE, THEME_SLATE, THEME_FOREST, THEME_ASH, THEME_DUSK, THEME_EMBER
    };

    public static int[] palette(String name) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(name)) return PALETTES[i];
        }
        return PALETTES[0];
    }

    public static String saved(Context ctx) {
        return ctx.getSharedPreferences(SlideshowWallpaperService.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(SettingsActivity.KEY_THEME, THEME_STONE);
    }

    /** Call at the top of onCreate (after setContentView) in every Activity. */
    public static void apply(Activity a) {
        int[] p = palette(saved(a));
        // Status bar + nav bar
        a.getWindow().setStatusBarColor(p[0]);
        a.getWindow().setNavigationBarColor(p[7]);
        // Walk the whole view tree and recolor by content description tag
        colorTree((ViewGroup) a.getWindow().getDecorView(), p);
    }

    private static void colorTree(ViewGroup root, int[] p) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View v = root.getChildAt(i);
            recolor(v, p);
            if (v instanceof ViewGroup) colorTree((ViewGroup) v, p);
        }
    }

    private static void recolor(View v, int[] p) {
        Object tag = v.getTag();
        if (tag == null) return;
        switch (tag.toString()) {
            case "bg_base":    v.setBackgroundColor(p[0]); break;
            case "bg_card":    v.setBackgroundColor(p[1]); break;
            case "bg_surface": v.setBackgroundColor(p[2]); break;
            case "bg_accent":  v.setBackgroundColor(p[3]); break;
            case "bg_nav":     v.setBackgroundColor(p[7]); break;
        }
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            switch (tag.toString()) {
                case "text_primary": tv.setTextColor(p[4]); break;
                case "text_muted":   tv.setTextColor(p[5]); break;
                case "text_dim":     tv.setTextColor(p[6]); break;
                case "text_accent":  tv.setTextColor(p[3]); break;
            }
        }
    }
}

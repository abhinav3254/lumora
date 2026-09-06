package com.abhinav3254.lumora;

import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScheduleActivity extends AppCompatActivity {

    public static final String KEY_SCHEDULE_ENABLED = "schedule_enabled";
    public static final String KEY_SCHEDULE_SLOTS   = "schedule_slots";

    private static final String[][] DEFAULT_SLOTS = {
        { "Morning",   "6",  "0",  "12", "0" },
        { "Afternoon", "12", "0",  "18", "0" },
        { "Evening",   "18", "0",  "22", "0" },
        { "Night",     "22", "0",  "6",  "0" },
    };

    private LinearLayout       containerSlots;
    private SwitchMaterial     toggleEnabled;
    private TextView           tvSaveConfirm;

    private final List<int[]>  slotTimes = new ArrayList<>();
    private final List<String> slotNames = new ArrayList<>();
    private final List<View>   slotViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        containerSlots = findViewById(R.id.container_slots);
        toggleEnabled  = findViewById(R.id.toggle_schedule_enabled);
        tvSaveConfirm  = findViewById(R.id.tv_save_confirm);

        SharedPreferences prefs = getSharedPreferences(
                SlideshowWallpaperService.PREFS_NAME, MODE_PRIVATE);

        toggleEnabled.setChecked(prefs.getBoolean(KEY_SCHEDULE_ENABLED, false));

        String savedJson = prefs.getString(KEY_SCHEDULE_SLOTS, "");
        if (savedJson != null && !savedJson.isEmpty()) {
            loadSavedSlots(savedJson);
        } else {
            loadDefaultSlots();
        }

        findViewById(R.id.btn_add_slot).setOnClickListener(v -> addSlot("Custom", 8, 0, 20, 0));
        findViewById(R.id.btn_save).setOnClickListener(v -> saveSchedule(prefs));
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    // ── Slot loading ─────────────────────────────────────────────────────────

    private void loadDefaultSlots() {
        for (String[] s : DEFAULT_SLOTS) {
            addSlot(s[0],
                    Integer.parseInt(s[1]), Integer.parseInt(s[2]),
                    Integer.parseInt(s[3]), Integer.parseInt(s[4]));
        }
    }

    private void loadSavedSlots(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                addSlot(o.getString("name"),
                        o.getInt("sh"), o.getInt("sm"),
                        o.getInt("eh"), o.getInt("em"));
            }
        } catch (Exception e) {
            loadDefaultSlots();
        }
    }

    // ── Build slot card programmatically ────────────────────────────────────

    private void addSlot(String name, int sh, int sm, int eh, int em) {
        final int index = slotTimes.size();
        slotNames.add(name);
        slotTimes.add(new int[]{sh, sm, eh, em});

        // Card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.home_card_bg);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardParams);

        // Top row: name + delete
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        topRow.setPadding(dp(16), dp(14), dp(16), dp(8));

        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextColor(0xFFe9e1dd);
        tvName.setTextSize(15);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        topRow.addView(tvName);

        TextView btnDelete = new TextView(this);
        btnDelete.setText("Remove");
        btnDelete.setTextColor(0xFF625d5a);
        btnDelete.setTextSize(12);
        btnDelete.setPadding(dp(8), dp(4), 0, dp(4));
        btnDelete.setOnClickListener(v -> removeSlot(card, index));
        topRow.addView(btnDelete);

        card.addView(topRow);

        // Divider
        View divider = new View(this);
        divider.setBackgroundColor(0xFF1e1b19);
        card.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // Time row
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        timeRow.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);

        // FROM column
        LinearLayout startCol = new LinearLayout(this);
        startCol.setOrientation(LinearLayout.VERTICAL);
        startCol.setLayoutParams(colParams);
        startCol.addView(makeLabel("FROM"));

        TextView tvStart = new TextView(this);
        tvStart.setText(formatTime(sh, sm));
        tvStart.setTextColor(0xFFccc5c1);
        tvStart.setTextSize(22);
        tvStart.setTypeface(null, android.graphics.Typeface.BOLD);
        tvStart.setOnClickListener(v -> {
            int[] t = slotTimes.get(index);
            if (t == null) return;
            new TimePickerDialog(this, (tp, h, m) -> {
                t[0] = h; t[1] = m;
                tvStart.setText(formatTime(h, m));
            }, t[0], t[1], true).show();
        });
        startCol.addView(tvStart);
        timeRow.addView(startCol);

        // Arrow
        TextView arrow = new TextView(this);
        arrow.setText("→");
        arrow.setTextColor(0xFF625d5a);
        arrow.setTextSize(18);
        arrow.setPadding(dp(12), 0, dp(12), 0);
        timeRow.addView(arrow);

        // UNTIL column
        LinearLayout endCol = new LinearLayout(this);
        endCol.setOrientation(LinearLayout.VERTICAL);
        endCol.setLayoutParams(colParams);
        endCol.addView(makeLabel("UNTIL"));

        TextView tvEnd = new TextView(this);
        tvEnd.setText(formatTime(eh, em));
        tvEnd.setTextColor(0xFFccc5c1);
        tvEnd.setTextSize(22);
        tvEnd.setTypeface(null, android.graphics.Typeface.BOLD);
        tvEnd.setOnClickListener(v -> {
            int[] t = slotTimes.get(index);
            if (t == null) return;
            new TimePickerDialog(this, (tp, h, m) -> {
                t[2] = h; t[3] = m;
                tvEnd.setText(formatTime(h, m));
            }, t[2], t[3], true).show();
        });
        endCol.addView(tvEnd);
        timeRow.addView(endCol);

        card.addView(timeRow);
        containerSlots.addView(card);
        slotViews.add(card);
    }

    private void removeSlot(LinearLayout card, int index) {
        containerSlots.removeView(card);
        if (index < slotTimes.size()) {
            slotTimes.set(index, null);
            slotNames.set(index, null);
        }
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    private void saveSchedule(SharedPreferences prefs) {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < slotTimes.size(); i++) {
                int[] t = slotTimes.get(i);
                String n = slotNames.get(i);
                if (t == null || n == null) continue;
                JSONObject o = new JSONObject();
                o.put("name", n);
                o.put("sh", t[0]); o.put("sm", t[1]);
                o.put("eh", t[2]); o.put("em", t[3]);
                arr.put(o);
            }
            prefs.edit()
                    .putBoolean(KEY_SCHEDULE_ENABLED, toggleEnabled.isChecked())
                    .putString(KEY_SCHEDULE_SLOTS, arr.toString())
                    .apply();
            tvSaveConfirm.setVisibility(View.VISIBLE);
            tvSaveConfirm.postDelayed(() -> tvSaveConfirm.setVisibility(View.GONE), 2400);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TextView makeLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF625d5a);
        tv.setTextSize(10);
        tv.setLetterSpacing(0.1f);
        tv.setAllCaps(true);
        return tv;
    }

    private String formatTime(int h, int m) {
        String ampm = h < 12 ? "AM" : "PM";
        int dh = h % 12;
        if (dh == 0) dh = 12;
        return String.format(Locale.getDefault(), "%d:%02d %s", dh, m, ampm);
    }

    private int dp(int val) {
        return (int) (val * getResources().getDisplayMetrics().density);
    }
}

package com.abhinav3254.lumora;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScheduleActivity extends AppCompatActivity {

    public static final String KEY_SCHEDULE_ENABLED = "schedule_enabled";
    public static final String KEY_SCHEDULE_SLOTS   = "schedule_slots";
    private static final String URI_SEP             = "|||";

    private static final String[][] DEFAULT_SLOTS = {
        { "Morning",   "6",  "0",  "12", "0" },
        { "Afternoon", "12", "0",  "18", "0" },
        { "Evening",   "18", "0",  "22", "0" },
        { "Night",     "22", "0",  "6",  "0" },
    };

    private LinearLayout       containerSlots;
    private SwitchMaterial     toggleEnabled;
    private TextView           tvSaveConfirm;

    private final List<int[]>        slotTimes  = new ArrayList<>();
    private final List<String>       slotNames  = new ArrayList<>();
    private final List<List<String>> slotUris   = new ArrayList<>(); // URIs per slot
    private final List<View>         slotViews  = new ArrayList<>();
    private final List<TextView>     slotPhotoBtns = new ArrayList<>(); // "X photos" label per slot

    // Which slot index is currently picking photos
    private int activePickingSlot = -1;

    // Photo picker launcher — registered once, used for whichever slot is active
    private final ActivityResultLauncher<Intent> pickImagesLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK
                                && result.getData() != null
                                && activePickingSlot >= 0) {

                            List<String> uris = slotUris.get(activePickingSlot);
                            uris.clear();
                            Intent data = result.getData();

                            if (data.getClipData() != null) {
                                int limit = Math.min(data.getClipData().getItemCount(), 20);
                                for (int i = 0; i < limit; i++) {
                                    Uri uri = data.getClipData().getItemAt(i).getUri();
                                    try { getContentResolver().takePersistableUriPermission(
                                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    } catch (Exception ignored) {}
                                    uris.add(uri.toString());
                                }
                            } else if (data.getData() != null) {
                                Uri uri = data.getData();
                                try { getContentResolver().takePersistableUriPermission(
                                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                } catch (Exception ignored) {}
                                uris.add(uri.toString());
                            }

                            // Update the photo count button label for this slot
                            updatePhotoLabel(activePickingSlot);
                            activePickingSlot = -1;
                        }
                    });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) openPhotoPicker();
                        else Toast.makeText(this,
                                "Permission needed to access photos", Toast.LENGTH_SHORT).show();
                    });

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

        findViewById(R.id.btn_add_slot).setOnClickListener(v -> addSlot("Custom", 8, 0, 20, 0, new ArrayList<>()));
        findViewById(R.id.btn_save).setOnClickListener(v -> saveSchedule(prefs));
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    private void loadDefaultSlots() {
        for (String[] s : DEFAULT_SLOTS) {
            addSlot(s[0],
                    Integer.parseInt(s[1]), Integer.parseInt(s[2]),
                    Integer.parseInt(s[3]), Integer.parseInt(s[4]),
                    new ArrayList<>());
        }
    }

    private void loadSavedSlots(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                List<String> uris = new ArrayList<>();
                if (o.has("uris")) {
                    JSONArray ua = o.getJSONArray("uris");
                    for (int j = 0; j < ua.length(); j++) uris.add(ua.getString(j));
                }
                addSlot(o.getString("name"),
                        o.getInt("sh"), o.getInt("sm"),
                        o.getInt("eh"), o.getInt("em"),
                        uris);
            }
        } catch (Exception e) {
            loadDefaultSlots();
        }
    }

    // ── Build slot card ───────────────────────────────────────────────────────

    private void addSlot(String name, int sh, int sm, int eh, int em, List<String> uris) {
        final int index = slotTimes.size();
        slotNames.add(name);
        slotTimes.add(new int[]{sh, sm, eh, em});
        slotUris.add(new ArrayList<>(uris));

        // ── Card wrapper ──
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.home_card_bg);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardParams);

        // ── Header row: name + Remove ──
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(10));

        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextColor(0xFFe9e1dd);
        tvName.setTextSize(15);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(tvName);

        TextView btnRemove = new TextView(this);
        btnRemove.setText("Remove");
        btnRemove.setTextColor(0xFF625d5a);
        btnRemove.setTextSize(12);
        btnRemove.setOnClickListener(v -> removeSlot(card, index));
        header.addView(btnRemove);
        card.addView(header);

        // ── Divider ──
        card.addView(makeDivider());

        // ── Time row ──
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        timeRow.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);

        // FROM
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

        // UNTIL
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

        // ── Divider ──
        card.addView(makeDivider());

        // ── Photos row ──
        LinearLayout photosRow = new LinearLayout(this);
        photosRow.setOrientation(LinearLayout.HORIZONTAL);
        photosRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        photosRow.setPadding(dp(16), dp(12), dp(16), dp(14));

        LinearLayout photosLeft = new LinearLayout(this);
        photosLeft.setOrientation(LinearLayout.VERTICAL);
        photosLeft.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        photosLeft.addView(makeLabel("PHOTOS FOR THIS SLOT"));

        TextView tvPhotoCount = new TextView(this);
        tvPhotoCount.setTextColor(0xFF988f88);
        tvPhotoCount.setTextSize(13);
        tvPhotoCount.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        tvPhotoCount.setTag("photocount_" + index);
        photosLeft.addView(tvPhotoCount);
        photosRow.addView(photosLeft);

        TextView btnPickPhotos = new TextView(this);
        btnPickPhotos.setText("Select");
        btnPickPhotos.setTextColor(0xFFccc5c1);
        btnPickPhotos.setTextSize(13);
        btnPickPhotos.setTypeface(null, android.graphics.Typeface.BOLD);
        btnPickPhotos.setBackgroundResource(R.drawable.settings_chip_inactive);
        btnPickPhotos.setPadding(dp(16), dp(8), dp(16), dp(8));
        btnPickPhotos.setOnClickListener(v -> {
            activePickingSlot = index;
            checkPermissionAndPick();
        });
        photosRow.addView(btnPickPhotos);

        card.addView(photosRow);

        containerSlots.addView(card);
        slotViews.add(card);
        slotPhotoBtns.add(tvPhotoCount);

        // Set initial label
        updatePhotoLabel(index);
    }

    private void updatePhotoLabel(int index) {
        if (index >= slotUris.size() || index >= slotPhotoBtns.size()) return;
        List<String> uris = slotUris.get(index);
        TextView tv = slotPhotoBtns.get(index);
        if (tv == null) return;
        int n = (uris == null) ? 0 : uris.size();
        tv.setText(n == 0 ? "No photos selected" : n + " photo" + (n == 1 ? "" : "s") + " selected");
    }

    private void removeSlot(LinearLayout card, int index) {
        containerSlots.removeView(card);
        if (index < slotTimes.size())  slotTimes.set(index, null);
        if (index < slotNames.size())  slotNames.set(index, null);
        if (index < slotUris.size())   slotUris.set(index, null);
        if (index < slotPhotoBtns.size()) slotPhotoBtns.set(index, null);
    }

    // ── Photo picking ─────────────────────────────────────────────────────────

    private void checkPermissionAndPick() {
        String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            openPhotoPicker();
        } else {
            permissionLauncher.launch(perm);
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

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveSchedule(SharedPreferences prefs) {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < slotTimes.size(); i++) {
                int[]        t = slotTimes.get(i);
                String       n = slotNames.get(i);
                List<String> u = slotUris.get(i);
                if (t == null || n == null) continue;

                JSONObject o = new JSONObject();
                o.put("name", n);
                o.put("sh", t[0]); o.put("sm", t[1]);
                o.put("eh", t[2]); o.put("em", t[3]);

                JSONArray ua = new JSONArray();
                if (u != null) for (String uri : u) ua.put(uri);
                o.put("uris", ua);

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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private View makeDivider() {
        View v = new View(this);
        v.setBackgroundColor(0xFF1e1b19);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        p.setMargins(dp(16), 0, dp(16), 0);
        v.setLayoutParams(p);
        return v;
    }

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

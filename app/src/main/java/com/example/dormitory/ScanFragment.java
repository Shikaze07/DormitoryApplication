package com.example.dormitory;

import android.graphics.Color;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONObject;

public class ScanFragment extends Fragment {
    private MaterialButtonToggleGroup toggle;
    private View resultCard;
    private TextView icon, title, name, detail, time, extra;
    private ImageView photo;
    private ActivityResultLauncher<ScanOptions> launcher;

    @Override
    public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        launcher = registerForActivityResult(new ScanContract(), result -> {
            if (result.getContents() != null) submit(result.getContents());
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_scan, parent, false);
        toggle = v.findViewById(R.id.toggle);
        resultCard = v.findViewById(R.id.resultCard);
        icon = v.findViewById(R.id.resultIcon);
        title = v.findViewById(R.id.resultTitle);
        name = v.findViewById(R.id.resultName);
        detail = v.findViewById(R.id.resultDetail);
        time = v.findViewById(R.id.resultTime);
        extra = v.findViewById(R.id.resultExtra);
        photo = v.findViewById(R.id.resultPhoto);
        toggle.check(R.id.btnIn);
        v.findViewById(R.id.scanButton).setOnClickListener(x -> {
            ScanOptions o = new ScanOptions();
            o.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            o.setPrompt("Align the QR code inside the frame");
            o.setBeepEnabled(true);
            o.setOrientationLocked(false);
            launcher.launch(o);
        });
        return v;
    }

    private void submit(String token) {
        String type = toggle.getCheckedButtonId() == R.id.btnOut ? "CheckOut" : "CheckIn";
        showResult("...", "Checking", "Please wait", "", "", Color.parseColor("#09090B"));
        try {
            JSONObject body = new JSONObject().put("token", token).put("accessType", type);
            Api.request(requireContext(), "POST", "/api/mobile/scan", body, (json, err) -> {
                if (!isAdded()) return;
                if ("SESSION_EXPIRED".equals(err)) { ((MainActivity) requireActivity()).logout(); return; }
                if (err != null) {
                    buzz(false);
                    showResult("X", "Access Denied", err, "", "", Color.parseColor("#DC2626"));
                    return;
                }
                buzz(true);
                String label = "CheckIn".equals(type) ? "Checked In" : "Checked Out";
                boolean visitor = "Visitor".equals(json.optString("type"));
                String detailText = visitor ? "Visitor" : json.optString("type") + " - " + json.optString("detail");
                showResult("OK", "Access Granted - " + label, json.optString("name"), detailText,
                        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                                .format(new java.util.Date()), Color.parseColor("#16A34A"));
                showPhoto(json.isNull("photo") ? null : json.optString("photo"));
                if (visitor) {
                    StringBuilder sb = new StringBuilder();
                    String host = json.isNull("visiting") ? "" : json.optString("visiting");
                    if (!host.isEmpty()) {
                        sb.append("Visiting: ").append(host);
                        if (!json.isNull("host_room")) sb.append(" (Room ").append(json.optString("host_room")).append(")");
                    }
                    if (!json.isNull("purpose") && !json.optString("purpose").isEmpty())
                        sb.append(sb.length() > 0 ? "\n" : "").append("Purpose: ").append(json.optString("purpose"));
                    long secs = json.optLong("visit_duration_seconds", 0);
                    if (secs > 0)
                        sb.append(sb.length() > 0 ? "\n" : "").append("Duration: ").append(formatDuration(secs));
                    extra.setText(sb.toString());
                    extra.setVisibility(sb.length() > 0 ? View.VISIBLE : View.GONE);
                }
            });
        } catch (Exception ignored) { }
    }

    private void showResult(String ic, String t, String n, String d, String tm, int color) {
        resultCard.setVisibility(View.VISIBLE);
        resultCard.setAlpha(0f);
        resultCard.setTranslationY(40f);
        resultCard.animate().alpha(1f).translationY(0f).setDuration(250).start();
        icon.setText(ic);
        icon.setTextColor(color);
        title.setText(t);
        title.setTextColor(color);
        name.setText(n);
        detail.setText(d);
        time.setText(tm);
        extra.setVisibility(View.GONE);
        photo.setVisibility(View.GONE);
    }

    private void showPhoto(String url) {
        if (url == null || url.isEmpty()) { photo.setVisibility(View.GONE); return; }
        photo.setVisibility(View.VISIBLE);
        ImageLoader.load(photo, url, 240);
    }

    static String formatDuration(long s) {
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private void buzz(boolean ok) {
        Vibrator vib = requireContext().getSystemService(Vibrator.class);
        if (vib != null) vib.vibrate(VibrationEffect.createOneShot(ok ? 80 : 250, VibrationEffect.DEFAULT_AMPLITUDE));
    }
}


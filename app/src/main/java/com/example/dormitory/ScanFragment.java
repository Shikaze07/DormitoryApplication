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
    private ActivityResultLauncher<String[]> locationPermission;
    private boolean askedOnOpen = false;
    private boolean scanAfterPermission = false;

    @Override
    public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        locationPermission = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(), result -> {
            if (!isAdded()) return;
            boolean wasScan = scanAfterPermission;
            scanAfterPermission = false;
            if (!LocationHelper.hasPermission(requireContext())) {
                showLocationDialog(true);
            } else if (wasScan) {
                ensureLocationThenScan();
            }
        });
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
        v.findViewById(R.id.scanButton).setOnClickListener(x -> ensureLocationThenScan());
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Ask for location as soon as the guard opens the scan tab.
        if (!askedOnOpen && !LocationHelper.hasPermission(requireContext())) {
            askedOnOpen = true;
            scanAfterPermission = false;
            locationPermission.launch(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    /** Makes sure location permission is granted and GPS is on, popping the system prompts if not. */
    private void ensureLocationThenScan() {
        if (!LocationHelper.hasPermission(requireContext())) {
            scanAfterPermission = true;
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    || !askedOnOpen) {
                askedOnOpen = true;
                locationPermission.launch(new String[]{
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION});
            } else {
                // Permanently denied: only the app settings screen can fix it.
                scanAfterPermission = false;
                showLocationDialog(true);
            }
            return;
        }
        if (!LocationHelper.isLocationEnabled(requireContext())) {
            showLocationDialog(false);
            return;
        }
        startScanner();
    }

    private void showLocationDialog(boolean permission) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_location, null);
        ((TextView) v.findViewById(R.id.locTitle)).setText(permission ? "Location access needed" : "Turn on GPS");
        ((TextView) v.findViewById(R.id.locMessage)).setText(permission
                ? "Allow location access so the app can confirm you are inside the dorm area before scanning."
                : "Your location is turned off. Turn on GPS so the app can confirm you are inside the dorm area.");
        android.app.Dialog dlg = new android.app.Dialog(requireContext());
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dlg.setContentView(v);
        ((com.google.android.material.button.MaterialButton) v.findViewById(R.id.locOpen))
                .setText(permission ? "Open app settings" : "Turn on GPS");
        v.findViewById(R.id.locCancel).setOnClickListener(x -> dlg.dismiss());
        v.findViewById(R.id.locOpen).setOnClickListener(x -> {
            dlg.dismiss();
            android.content.Intent i = permission
                    ? new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", requireContext().getPackageName(), null))
                    : new android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(i);
        });
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dlg.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void startScanner() {
        ScanOptions o = new ScanOptions();
        o.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        o.setPrompt("Align the QR code inside the frame");
        o.setBeepEnabled(true);
        o.setOrientationLocked(false);
        launcher.launch(o);
    }

    private void submit(String token) {
        send(token, "preview");
    }

    /** mode: "preview" (look up only), "record" (approve and log), "reject". */
    private void send(String token, String mode) {
        String type = toggle.getCheckedButtonId() == R.id.btnOut ? "CheckOut" : "CheckIn";
        showResult("...", "Checking", "Please wait", "", "", Color.parseColor("#09090B"));
        LocationHelper.get(requireContext(), loc -> {
        if (!isAdded()) return;
        try {
            JSONObject body = new JSONObject().put("token", token).put("accessType", type).put("mode", mode);
            if (loc != null) body.put("latitude", loc.getLatitude()).put("longitude", loc.getLongitude()).put("accuracy", loc.hasAccuracy() ? loc.getAccuracy() : 0);
            Api.request(requireContext(), "POST", "/api/mobile/scan", body, (json, err) -> {
                if (!isAdded()) return;
                if ("SESSION_EXPIRED".equals(err)) { ((MainActivity) requireActivity()).logout(); return; }
                if (err != null) {
                    buzz(false);
                    boolean geo = err.contains("outside the dorm area") || err.contains("Location unavailable");
                    boolean dup = err.startsWith("ALREADY SCANNED");
                    boolean warn = geo || dup;
                    showResult(warn ? "!" : "X", dup ? "Already Scanned" : geo ? "Location Warning" : "Access Denied", dup ? err.replaceFirst("^ALREADY SCANNED:\\s*", "") : err, "", "", Color.parseColor(warn ? "#D97706" : "#DC2626"));
                    return;
                }
                if (json.optBoolean("preview", false)) {
                    resultCard.setVisibility(View.GONE);
                    showReview(token, json);
                    return;
                }
                if (json.optBoolean("rejected", false)) {
                    buzz(false);
                    showResult("X", "Visitor Rejected", json.optString("name"), "Entry was not allowed.", java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date()), Color.parseColor("#DC2626"));
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
        });
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

    private void showReview(String token, JSONObject json) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_visitor_review, null);
        ImageView rp = v.findViewById(R.id.reviewPhoto);
        ((TextView) v.findViewById(R.id.reviewName)).setText(json.optString("name"));
        boolean out = "CheckOut".equals(json.optString("accessType"));
        ((TextView) v.findViewById(R.id.reviewType)).setText("Visitor " + (out ? "check-out" : "check-in") + " request");
        ImageLoader.load(rp, json.isNull("photo") ? null : json.optString("photo"), 280);
        String host = json.isNull("visiting") ? "" : json.optString("visiting");
        String visiting = host.isEmpty() ? "N/A" : host;
        if (!json.isNull("host_room")) visiting += " (Room " + json.optString("host_room") + ")";
        ((TextView) v.findViewById(R.id.reviewVisiting)).setText(visiting);
        String purpose = json.isNull("purpose") ? "" : json.optString("purpose");
        ((TextView) v.findViewById(R.id.reviewPurpose)).setText(purpose.isEmpty() ? "Personal Visit" : purpose);
        long secs = json.optLong("visit_duration_seconds", 0);
        ((TextView) v.findViewById(R.id.reviewDuration)).setText(secs > 0 ? formatDuration(secs) : "-");

        android.app.Dialog dlg = new android.app.Dialog(requireContext());
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dlg.setContentView(v);
        dlg.setCancelable(false);
        v.findViewById(R.id.reviewApprove).setOnClickListener(x -> { dlg.dismiss(); send(token, "record"); });
        v.findViewById(R.id.reviewReject).setOnClickListener(x -> { dlg.dismiss(); send(token, "reject"); });
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            int w = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
            dlg.getWindow().setLayout(w, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
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


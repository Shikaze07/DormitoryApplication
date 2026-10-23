package com.example.dormitory;

import android.graphics.Color;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
    private TextView icon, title, name, detail, time;
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
                showResult("OK", "Access Granted - " + label, json.optString("name"),
                        json.optString("type") + " - " + json.optString("detail"),
                        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                                .format(new java.util.Date()), Color.parseColor("#16A34A"));
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
    }

    private void buzz(boolean ok) {
        Vibrator vib = requireContext().getSystemService(Vibrator.class);
        if (vib != null) vib.vibrate(VibrationEffect.createOneShot(ok ? 80 : 250, VibrationEffect.DEFAULT_AMPLITUDE));
    }
}


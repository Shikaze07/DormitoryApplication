package com.example.dormitory;

import android.app.DatePickerDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Resident's visitor requests: list with QR pass, register a visitor, cancel. */
public class VisitorsFragment extends Fragment {
    private SwipeRefreshLayout refresh;
    private RecyclerView list;
    private TextView empty;
    private JSONArray data = new JSONArray();

    // State of the "Register New Visitor" form currently shown
    private ActivityResultLauncher<String> photoPicker;
    private ImageView formPhoto;
    private TextView formPhotoHint;
    private ProgressBar formPhotoProgress;
    private String photoUrl = null;
    private boolean uploading = false;

    @Override
    public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        photoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) uploadPhoto(uri);
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_list, parent, false);
        refresh = v.findViewById(R.id.refresh);
        list = v.findViewById(R.id.list);
        empty = v.findViewById(R.id.empty);
        empty.setText("No visitor requests yet.\nTap + to register a visitor.");
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(new Adapter());
        refresh.setColorSchemeColors(0xFF09090B);
        refresh.setOnRefreshListener(this::load);
        v.findViewById(R.id.addFab).setOnClickListener(x -> openForm());
        refresh.setRefreshing(true);
        load();
        return v;
    }

    private void load() {
        Api.request(requireContext(), "GET", "/api/mobile/visitors", null, (json, err) -> {
            if (!isAdded()) return;
            refresh.setRefreshing(false);
            if ("SESSION_EXPIRED".equals(err)) { ((MainActivity) requireActivity()).logout(); return; }
            if (err != null) { empty.setText(err); empty.setVisibility(View.VISIBLE); return; }
            data = json.optJSONArray("requests");
            if (data == null) data = new JSONArray();
            empty.setVisibility(data.length() == 0 ? View.VISIBLE : View.GONE);
            list.getAdapter().notifyDataSetChanged();
        });
    }

    private void uploadPhoto(Uri uri) {
        if (formPhoto == null) return;
        try {
            byte[] jpeg = compress(uri);
            formPhoto.setImageBitmap(BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length));
            formPhotoHint.setVisibility(View.GONE);
            formPhotoProgress.setVisibility(View.VISIBLE);
            uploading = true;
            photoUrl = null;
            Api.uploadImage(requireContext(), jpeg, (json, err) -> {
                uploading = false;
                if (formPhotoProgress != null) formPhotoProgress.setVisibility(View.GONE);
                if (!isAdded()) return;
                if (err != null) {
                    Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show();
                    if (formPhoto != null) formPhoto.setImageDrawable(null);
                    if (formPhotoHint != null) formPhotoHint.setVisibility(View.VISIBLE);
                    return;
                }
                photoUrl = json.optString("url");
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Could not read that image.", Toast.LENGTH_LONG).show();
        }
    }

    /** Downscales to max 1024px and compresses to JPEG. */
    private byte[] compress(Uri uri) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, o);
        }
        int sample = 1;
        while (o.outWidth / (sample * 2) >= 1024 && o.outHeight / (sample * 2) >= 1024) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(is, null, o);
        }
        if (bmp == null) throw new IllegalStateException("decode failed");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos);
        return bos.toByteArray();
    }

    private static int num(EditText e) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception ex) { return 0; }
    }

    private void openForm() {
        BottomSheetDialog d = new BottomSheetDialog(requireContext());
        View f = getLayoutInflater().inflate(R.layout.sheet_visitor, null);
        d.setContentView(f);
        photoUrl = null;
        uploading = false;
        formPhoto = f.findViewById(R.id.photoPreview);
        formPhotoHint = f.findViewById(R.id.photoHint);
        formPhotoProgress = f.findViewById(R.id.photoProgress);
        f.findViewById(R.id.photoBox).setOnClickListener(x -> { if (!uploading) photoPicker.launch("image/*"); });
        d.setOnDismissListener(x -> { formPhoto = null; formPhotoHint = null; formPhotoProgress = null; });

        EditText first = f.findViewById(R.id.firstName),
                middle = f.findViewById(R.id.middleName),
                last = f.findViewById(R.id.lastName),
                purpose = f.findViewById(R.id.purpose),
                durH = f.findViewById(R.id.durH),
                durM = f.findViewById(R.id.durM),
                durS = f.findViewById(R.id.durS);
        MaterialButtonToggleGroup gender = f.findViewById(R.id.gender);
        MaterialButton when = f.findViewById(R.id.whenBtn);
        gender.check(R.id.male);
        final Calendar cal = Calendar.getInstance();
        final SimpleDateFormat fmt = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
        when.setText(fmt.format(cal.getTime()));
        when.setOnClickListener(x -> {
            DatePickerDialog dp = new DatePickerDialog(requireContext(), (p, y, m, dd) -> {
                cal.set(y, m, dd);
                when.setText(fmt.format(cal.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            dp.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            dp.show();
        });

        MaterialButton submit = f.findViewById(R.id.submit);
        submit.setOnClickListener(x -> {
            String fn = first.getText().toString().trim();
            String mn = middle.getText().toString().trim();
            String ln = last.getText().toString().trim();
            String pu = purpose.getText().toString().trim();
            int h = num(durH), m = num(durM), s = num(durS);
            long secs = h * 3600L + m * 60L + s;
            String error = null;
            if (uploading) error = "Please wait for the photo to finish uploading.";
            else if (photoUrl == null) error = "Visitor photo is required.";
            else if (fn.length() < 2) error = "First name must be at least 2 characters.";
            else if (ln.length() < 2) error = "Last name must be at least 2 characters.";
            else if (h > 24 || m > 59 || s > 59) error = "Duration must be within 24h, 59m, 59s.";
            else if (secs <= 0) error = "Visit duration must be greater than 0.";
            else if (secs > 86400) error = "Visit duration cannot exceed 24 hours.";
            else if (pu.length() < 5) error = "Purpose must be at least 5 characters.";
            if (error != null) { Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show(); return; }

            SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            submit.setEnabled(false);
            try {
                JSONObject body = new JSONObject()
                        .put("photo", photoUrl)
                        .put("first_name", fn)
                        .put("middle_name", mn)
                        .put("last_name", ln)
                        .put("gender", gender.getCheckedButtonId() == R.id.female ? "Female" : "Male")
                        .put("purpose", pu)
                        .put("visit_duration_seconds", secs)
                        .put("expected_at", day.format(cal.getTime()));
                Api.request(requireContext(), "POST", "/api/mobile/visitors", body, (json, err) -> {
                    submit.setEnabled(true);
                    if (!isAdded()) return;
                    if (err != null) { Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show(); return; }
                    d.dismiss();
                    Toast.makeText(requireContext(), "Visitor request registered successfully", Toast.LENGTH_SHORT).show();
                    refresh.setRefreshing(true);
                    load();
                });
            } catch (Exception ignored) { submit.setEnabled(true); }
        });
        d.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        d.show();
    }

    private static java.util.Date parseIso(String iso) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            in.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return in.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }

    private static String fmtDate(String iso, String pattern) {
        java.util.Date d = parseIso(iso);
        return d == null ? iso : new SimpleDateFormat(pattern, Locale.getDefault()).format(d);
    }

    private static boolean isExpired(JSONObject o) {
        java.util.Date d = parseIso(o.optString("qr_expires_at"));
        return d != null && new java.util.Date().after(d);
    }

    /** Pass is usable only while the request is open and the QR has not expired. */
    private static boolean passValid(JSONObject o) {
        String st = o.optString("status");
        return ("Pending".equals(st) || "Approved".equals(st)) && !isExpired(o);
    }

    private void showPass(JSONObject o) {
        BottomSheetDialog d = new BottomSheetDialog(requireContext());
        View f = getLayoutInflater().inflate(R.layout.sheet_pass, null);
        d.setContentView(f);
        ((TextView) f.findViewById(R.id.passName)).setText(o.optString("name"));
        long secs = o.optLong("visit_duration_seconds", 0);
        String info = "Show this QR to the guard at the gate";
        if (secs > 0) info += "\nDuration: " + ScanFragment.formatDuration(secs);
        info += "\nExpires: " + fmtDate(o.optString("qr_expires_at"), "MMM dd, hh:mm a");
        TextView infoView = f.findViewById(R.id.passInfo);
        infoView.setText(info);
        infoView.setGravity(android.view.Gravity.CENTER);
        ((ImageView) f.findViewById(R.id.passQr)).setImageBitmap(QrUtil.make(o.optString("qr_code"), 720));
        String footer = "Valid until " + fmtDate(o.optString("qr_expires_at"), "MMM dd, hh:mm a");
        f.findViewById(R.id.passSave).setOnClickListener(x -> savePass(o, footer));
        f.findViewById(R.id.passShare).setOnClickListener(x -> sharePass(o, footer));
        d.show();
    }

    private static String fileName(JSONObject o) {
        return "visitor-pass-" + o.optString("name").trim().replaceAll("[^A-Za-z0-9]+", "-") + ".png";
    }

    /** Saves the QR card into the phone gallery (Pictures/Dormitory). */
    private void savePass(JSONObject o, String footer) {
        Bitmap card = QrUtil.card(o.optString("qr_code"), o.optString("name"), footer);
        if (card == null) { Toast.makeText(requireContext(), "Could not create the image.", Toast.LENGTH_SHORT).show(); return; }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            sharePass(o, footer); // older phones: no storage permission, so use the share sheet
            return;
        }
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName(o));
            v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Dormitory");
            Uri uri = requireContext().getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IllegalStateException("insert failed");
            try (java.io.OutputStream os = requireContext().getContentResolver().openOutputStream(uri)) {
                card.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            Toast.makeText(requireContext(), "Saved to Pictures/Dormitory", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Could not save the image.", Toast.LENGTH_LONG).show();
        }
    }

    /** Opens the share sheet so the QR image can be sent straight to the visitor. */
    private void sharePass(JSONObject o, String footer) {
        Bitmap card = QrUtil.card(o.optString("qr_code"), o.optString("name"), footer);
        if (card == null) { Toast.makeText(requireContext(), "Could not create the image.", Toast.LENGTH_SHORT).show(); return; }
        try {
            java.io.File dir = new java.io.File(requireContext().getCacheDir(), "shared");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            java.io.File file = new java.io.File(dir, fileName(o));
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                card.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(), requireContext().getPackageName() + ".fileprovider", file);
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
            i.setType("image/png");
            i.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            i.putExtra(android.content.Intent.EXTRA_TEXT, "Visitor pass for " + o.optString("name") + ". " + footer + ".");
            i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(i, "Send QR pass"));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Could not share the image.", Toast.LENGTH_LONG).show();
        }
    }

    private void showDetails(JSONObject o) {
        BottomSheetDialog d = new BottomSheetDialog(requireContext());
        View f = getLayoutInflater().inflate(R.layout.sheet_visitor_details, null);
        d.setContentView(f);
        ImageLoader.load((ImageView) f.findViewById(R.id.detPhoto), o.isNull("photo") ? null : o.optString("photo"), 280);
        ((TextView) f.findViewById(R.id.detName)).setText(o.optString("name"));
        String g = o.isNull("gender") ? "" : o.optString("gender");
        ((TextView) f.findViewById(R.id.detGender)).setText(g.isEmpty() ? "N/A" : g);
        ((TextView) f.findViewById(R.id.detStatus)).setText(o.optString("status"));
        ((TextView) f.findViewById(R.id.detExpected)).setText(fmtDate(o.optString("expected_at"), "MMM dd, yyyy"));
        long secs = o.optLong("visit_duration_seconds", 0);
        ((TextView) f.findViewById(R.id.detDuration)).setText(secs > 0 ? ScanFragment.formatDuration(secs) : "-");
        String st = o.optString("status");
        String pass = ("Cancelled".equals(st) || "Rejected".equals(st)) ? "Revoked"
                : isExpired(o) ? "Expired"
                : "Valid until " + fmtDate(o.optString("qr_expires_at"), "MMM dd, hh:mm a");
        ((TextView) f.findViewById(R.id.detPass)).setText(pass);
        String p = o.isNull("purpose") ? "" : o.optString("purpose");
        ((TextView) f.findViewById(R.id.detPurpose)).setText(p.isEmpty() ? "Personal Visit" : p);

        View viewPass = f.findViewById(R.id.detViewPass);
        viewPass.setVisibility(passValid(o) ? View.VISIBLE : View.GONE);
        viewPass.setOnClickListener(x -> { d.dismiss(); showPass(o); });
        View cancel = f.findViewById(R.id.detCancel);
        cancel.setVisibility("Pending".equals(st) ? View.VISIBLE : View.GONE);
        cancel.setOnClickListener(x -> {
            d.dismiss();
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Cancel this visit?")
                    .setMessage("The visitor's pass will be revoked.")
                    .setPositiveButton("Cancel visit", (dd, w) -> cancel(o))
                    .setNegativeButton("Keep", null).show();
        });
        d.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        d.show();
    }

    private void cancel(JSONObject o) {
        Api.request(requireContext(), "DELETE", "/api/mobile/visitors?id=" + o.optInt("id"), null, (json, err) -> {
            if (!isAdded()) return;
            if (err != null) Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show();
            else Toast.makeText(requireContext(), "Visitor request cancelled", Toast.LENGTH_SHORT).show();
            load();
        });
    }

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int HEADER = 0, ROW = 1;

        class HeaderVH extends RecyclerView.ViewHolder {
            HeaderVH(View v) { super(v); }
        }

        class VH extends RecyclerView.ViewHolder {
            TextView badge, title, sub, sub2, time, statusBadge;
            ImageView avatar;
            VH(View v) {
                super(v);
                avatar = v.findViewById(R.id.avatar);
                badge = v.findViewById(R.id.badge);
                title = v.findViewById(R.id.title);
                sub = v.findViewById(R.id.sub);
                sub2 = v.findViewById(R.id.sub2);
                time = v.findViewById(R.id.time);
                statusBadge = v.findViewById(R.id.statusBadge);
            }
        }

        @Override
        public int getItemViewType(int position) { return position == 0 ? HEADER : ROW; }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            LayoutInflater inf = LayoutInflater.from(p.getContext());
            if (t == HEADER) return new HeaderVH(inf.inflate(R.layout.header_visitor_stats, p, false));
            return new VH(inf.inflate(R.layout.item_row, p, false));
        }

        private void bindHeader(View v) {
            int active = 0, done = 0, inactive = 0;
            for (int i = 0; i < data.length(); i++) {
                String st = data.optJSONObject(i).optString("status");
                if ("Pending".equals(st) || "Approved".equals(st)) active++;
                else if ("Completed".equals(st)) done++;
                else if ("Cancelled".equals(st) || "Rejected".equals(st)) inactive++;
            }
            ((TextView) v.findViewById(R.id.statTotal)).setText(String.valueOf(data.length()));
            ((TextView) v.findViewById(R.id.statActive)).setText(String.valueOf(active));
            ((TextView) v.findViewById(R.id.statDone)).setText(String.valueOf(done));
            ((TextView) v.findViewById(R.id.statInactive)).setText(String.valueOf(inactive));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
            if (holder instanceof HeaderVH) { bindHeader(holder.itemView); return; }
            VH h = (VH) holder;
            JSONObject o = data.optJSONObject(pos - 1);
            String status = o.optString("status");
            h.badge.setVisibility(View.GONE);
            h.avatar.setVisibility(View.VISIBLE);
            h.avatar.setBackgroundResource(R.drawable.bg_photo);
            ImageLoader.load(h.avatar, o.isNull("photo") ? null : o.optString("photo"), 120);
            h.title.setText(o.optString("name"));
            String p = o.isNull("purpose") ? "" : o.optString("purpose", "");
            h.sub.setText(p.isEmpty() ? "Personal Visit" : p);
            long secs = o.optLong("visit_duration_seconds", 0);
            h.sub2.setVisibility(View.VISIBLE);
            h.sub2.setText("Duration: " + (secs > 0 ? ScanFragment.formatDuration(secs) : "-"));

            h.statusBadge.setVisibility(View.VISIBLE);
            h.statusBadge.setText(status.toUpperCase());
            if ("Pending".equalsIgnoreCase(status)) {
                h.statusBadge.setBackgroundResource(R.drawable.bg_status_pending);
                h.statusBadge.setTextColor(android.graphics.Color.parseColor("#B45309"));
            } else if ("Approved".equalsIgnoreCase(status)) {
                h.statusBadge.setBackgroundResource(R.drawable.bg_status_approved);
                h.statusBadge.setTextColor(android.graphics.Color.parseColor("#047857"));
            } else {
                h.statusBadge.setBackgroundResource(R.drawable.bg_status_neutral);
                h.statusBadge.setTextColor(android.graphics.Color.parseColor("#4B5563"));
            }

            h.time.setText(fmtDate(o.optString("expected_at"), "MMM dd, yyyy"));
            h.itemView.setOnClickListener(x -> showDetails(o));
            h.itemView.setOnLongClickListener(null);
        }

        @Override
        public int getItemCount() { return data.length() + 1; }
    }
}

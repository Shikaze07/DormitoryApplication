package com.example.dormitory;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Resident's visitor requests: list with QR pass, invite a visitor, cancel. */
public class VisitorsFragment extends Fragment {
    private SwipeRefreshLayout refresh;
    private RecyclerView list;
    private TextView empty;
    private JSONArray data = new JSONArray();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_list, parent, false);
        refresh = v.findViewById(R.id.refresh);
        list = v.findViewById(R.id.list);
        empty = v.findViewById(R.id.empty);
        empty.setText("No visitor requests yet.\nTap + to invite someone.");
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

    private void openForm() {
        BottomSheetDialog d = new BottomSheetDialog(requireContext());
        View f = getLayoutInflater().inflate(R.layout.sheet_visitor, null);
        d.setContentView(f);
        EditText first = f.findViewById(R.id.firstName),
                middle = f.findViewById(R.id.middleName),
                last = f.findViewById(R.id.lastName),
                purpose = f.findViewById(R.id.purpose);
        MaterialButtonToggleGroup gender = f.findViewById(R.id.gender);
        MaterialButton when = f.findViewById(R.id.whenBtn);
        gender.check(R.id.male);
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, 1);
        final SimpleDateFormat fmt = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault());
        when.setText(fmt.format(cal.getTime()));
        when.setOnClickListener(x -> new DatePickerDialog(requireContext(), (dp, y, m, dd) -> {
            cal.set(y, m, dd);
            new TimePickerDialog(requireContext(), (tp, h, mi) -> {
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, mi);
                when.setText(fmt.format(cal.getTime()));
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show());

        MaterialButton submit = f.findViewById(R.id.submit);
        submit.setOnClickListener(x -> {
            String fn = first.getText().toString().trim();
            String mn = middle != null ? middle.getText().toString().trim() : "";
            String ln = last.getText().toString().trim();
            if (fn.isEmpty() || ln.isEmpty()) { Toast.makeText(requireContext(), "Enter the visitor's full name", Toast.LENGTH_SHORT).show(); return; }
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            submit.setEnabled(false);
            try {
                JSONObject body = new JSONObject()
                        .put("first_name", fn)
                        .put("middle_name", mn)
                        .put("last_name", ln)
                        .put("gender", gender.getCheckedButtonId() == R.id.female ? "Female" : "Male")
                        .put("purpose", purpose.getText().toString())
                        .put("expected_at", iso.format(cal.getTime()));
                Api.request(requireContext(), "POST", "/api/mobile/visitors", body, (json, err) -> {
                    submit.setEnabled(true);
                    if (err != null) { Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show(); return; }
                    d.dismiss();
                    refresh.setRefreshing(true);
                    load();
                });
            } catch (Exception ignored) { }
        });
        d.show();
    }

    private void showPass(JSONObject o) {
        BottomSheetDialog d = new BottomSheetDialog(requireContext());
        View f = getLayoutInflater().inflate(R.layout.sheet_pass, null);
        d.setContentView(f);
        ((TextView) f.findViewById(R.id.passName)).setText(o.optString("name"));
        ((TextView) f.findViewById(R.id.passInfo)).setText("Show this QR to the guard at the gate");
        ((ImageView) f.findViewById(R.id.passQr)).setImageBitmap(QrUtil.make(o.optString("qr_code"), 720));
        d.show();
    }

    private void cancel(JSONObject o) {
        Api.request(requireContext(), "DELETE", "/api/mobile/visitors?id=" + o.optInt("id"), null, (json, err) -> {
            if (!isAdded()) return;
            if (err != null) Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show();
            load();
        });
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView badge, title, sub, time, statusBadge;
            VH(View v) {
                super(v);
                badge = v.findViewById(R.id.badge);
                title = v.findViewById(R.id.title);
                sub = v.findViewById(R.id.sub);
                time = v.findViewById(R.id.time);
                statusBadge = v.findViewById(R.id.statusBadge);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_row, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int i) {
            JSONObject o = data.optJSONObject(i);
            String status = o.optString("status");
            boolean active = "Pending".equals(status) || "Approved".equals(status);
            h.badge.setText(active ? "QR" : "-");
            h.badge.setBackgroundResource(active ? R.drawable.bg_badge_blue : R.drawable.bg_badge_gray);
            h.title.setText(o.optString("name"));
            String p = o.optString("purpose", "");
            if (p.isEmpty() || "null".equals(p)) {
                h.sub.setText("Personal Visit");
            } else {
                h.sub.setText(p);
            }

            // Visible status badge on the right
            h.statusBadge.setVisibility(View.VISIBLE);
            h.statusBadge.setText(status.toUpperCase());
            if ("Pending".equalsIgnoreCase(status)) {
                h.statusBadge.setBackgroundResource(R.drawable.bg_status_pending);
                h.statusBadge.setTextColor(android.graphics.Color.parseColor("#B45309")); // dark amber
            } else if ("Approved".equalsIgnoreCase(status)) {
                h.statusBadge.setBackgroundResource(R.drawable.bg_status_approved);
                h.statusBadge.setTextColor(android.graphics.Color.parseColor("#047857")); // dark green
            } else {
                h.statusBadge.setBackgroundResource(R.drawable.bg_status_neutral);
                h.statusBadge.setTextColor(android.graphics.Color.parseColor("#4B5563")); // slate/gray
            }

            h.time.setText(LogsFragment.when(o.optString("expected_at")));
            h.itemView.setOnClickListener(x -> { if (active) showPass(o); });
            h.itemView.setOnLongClickListener(x -> {
                if (active) {
                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("Cancel this visit?")
                            .setPositiveButton("Cancel visit", (dd, w) -> cancel(o))
                            .setNegativeButton("Keep", null).show();
                }
                return true;
            });
        }

        @Override
        public int getItemCount() { return data.length(); }
    }
}


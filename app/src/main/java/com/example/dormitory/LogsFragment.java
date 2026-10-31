package com.example.dormitory;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Entry/exit log list. Guards see everyone; residents see their own. Search, date range filter, pull to refresh. */
public class LogsFragment extends Fragment {
    private SwipeRefreshLayout refresh;
    private RecyclerView list;
    private TextView empty;
    private EditText searchQuery;
    private ImageView clearSearch;
    private List<JSONObject> rawList = new ArrayList<>();
    private List<JSONObject> filteredList = new ArrayList<>();
    private Adapter adapter;
    private String currentQuery = "";
    private Calendar fromDate, toDate;
    private com.google.android.material.button.MaterialButton btnFrom, btnTo, btnClearDates;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_logs, parent, false);
        refresh = v.findViewById(R.id.refresh);
        list = v.findViewById(R.id.list);
        empty = v.findViewById(R.id.empty);
        searchQuery = v.findViewById(R.id.searchQuery);
        clearSearch = v.findViewById(R.id.clearSearch);
        btnFrom = v.findViewById(R.id.btnFrom);
        btnTo = v.findViewById(R.id.btnTo);
        btnClearDates = v.findViewById(R.id.btnClearDates);
        btnFrom.setOnClickListener(x -> pickDate(true));
        btnTo.setOnClickListener(x -> pickDate(false));
        btnClearDates.setOnClickListener(x -> { fromDate = null; toDate = null; updateDateButtons(); refresh.setRefreshing(true); load(); });

        boolean isGuard = "Guard".equals(Api.getRole(requireContext()));
        empty.setText("No entry or exit logs yet.");

        adapter = new Adapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        refresh.setColorSchemeColors(0xFF09090B);
        refresh.setOnRefreshListener(this::load);

        if (searchQuery != null) {
            searchQuery.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentQuery = s.toString().trim().toLowerCase(Locale.getDefault());
                    if (clearSearch != null) {
                        clearSearch.setVisibility(currentQuery.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    applyFilter();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (clearSearch != null) {
            clearSearch.setOnClickListener(x -> {
                if (searchQuery != null) searchQuery.setText("");
            });
        }

        refresh.setRefreshing(true);
        load();
        return v;
    }

    private void load() {
        String path = "/api/mobile/logs";
        java.util.List<String> qs = new ArrayList<>();
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        if (fromDate != null) {
            Calendar s = (Calendar) fromDate.clone();
            s.set(Calendar.HOUR_OF_DAY, 0); s.set(Calendar.MINUTE, 0); s.set(Calendar.SECOND, 0); s.set(Calendar.MILLISECOND, 0);
            qs.add("from=" + iso.format(s.getTime()));
        }
        if (toDate != null) {
            Calendar e = (Calendar) toDate.clone();
            e.set(Calendar.HOUR_OF_DAY, 23); e.set(Calendar.MINUTE, 59); e.set(Calendar.SECOND, 59); e.set(Calendar.MILLISECOND, 999);
            qs.add("to=" + iso.format(e.getTime()));
        }
        if (!qs.isEmpty()) path += "?" + android.text.TextUtils.join("&", qs);
        Api.request(requireContext(), "GET", path, null, (json, err) -> {
            if (!isAdded()) return;
            refresh.setRefreshing(false);
            if ("SESSION_EXPIRED".equals(err)) { ((MainActivity) requireActivity()).logout(); return; }
            if (err != null) { empty.setText(err); empty.setVisibility(View.VISIBLE); return; }

            JSONArray logs = json.optJSONArray("logs");
            rawList.clear();
            if (logs != null) {
                for (int i = 0; i < logs.length(); i++) {
                    JSONObject o = logs.optJSONObject(i);
                    if (o != null) rawList.add(o);
                }
            }
            applyFilter();
        });
    }

    private void pickDate(boolean from) {
        Calendar init = from ? (fromDate != null ? fromDate : Calendar.getInstance()) : (toDate != null ? toDate : Calendar.getInstance());
        DatePickerDialog d = new DatePickerDialog(requireContext(), (view, y, m, day) -> {
            Calendar c = Calendar.getInstance();
            c.set(y, m, day);
            if (from) fromDate = c; else toDate = c;
            if (fromDate != null && toDate != null && fromDate.after(toDate)) {
                if (from) toDate = (Calendar) fromDate.clone(); else fromDate = (Calendar) toDate.clone();
            }
            updateDateButtons();
            refresh.setRefreshing(true);
            load();
        }, init.get(Calendar.YEAR), init.get(Calendar.MONTH), init.get(Calendar.DAY_OF_MONTH));
        d.getDatePicker().setMaxDate(System.currentTimeMillis());
        d.show();
    }

    private void updateDateButtons() {
        SimpleDateFormat f = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        btnFrom.setText(fromDate != null ? f.format(fromDate.getTime()) : "From date");
        btnTo.setText(toDate != null ? f.format(toDate.getTime()) : "To date");
        btnClearDates.setVisibility(fromDate != null || toDate != null ? View.VISIBLE : View.GONE);
    }

    private void applyFilter() {
        filteredList.clear();
        if (currentQuery.isEmpty()) {
            filteredList.addAll(rawList);
        } else {
            for (JSONObject o : rawList) {
                String person = o.optString("person", "").toLowerCase(Locale.getDefault());
                String category = o.optString("category", "").toLowerCase(Locale.getDefault());
                String visiting = o.isNull("visiting") ? "" : o.optString("visiting", "").toLowerCase(Locale.getDefault());
                String purpose = o.isNull("purpose") ? "" : o.optString("purpose", "").toLowerCase(Locale.getDefault());
                if (person.contains(currentQuery) || category.contains(currentQuery)
                        || visiting.contains(currentQuery) || purpose.contains(currentQuery)) {
                    filteredList.add(o);
                }
            }
        }

        if (filteredList.isEmpty()) {
            boolean isGuard = "Guard".equals(Api.getRole(requireContext()));
            if (!currentQuery.isEmpty()) {
                empty.setText("No logs found matching \"" + currentQuery + "\".");
            } else {
                empty.setText("No entry or exit logs yet.");
            }
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
        }

        adapter.notifyDataSetChanged();
    }

    static String when(String iso) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = in.parse(iso);
            return new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(d);
        } catch (Exception e) {
            return iso;
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView badge, title, sub, sub2, time;
            ImageView avatar;
            VH(View v) {
                super(v);
                badge = v.findViewById(R.id.badge);
                title = v.findViewById(R.id.title);
                sub = v.findViewById(R.id.sub);
                sub2 = v.findViewById(R.id.sub2);
                avatar = v.findViewById(R.id.avatar);
                time = v.findViewById(R.id.time);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_row, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int i) {
            JSONObject o = filteredList.get(i);
            boolean in = "CheckIn".equals(o.optString("access_type"));
            h.badge.setText(in ? "IN" : "OUT");
            h.badge.setBackgroundResource(in ? R.drawable.bg_badge_green : R.drawable.bg_badge_blue);
            h.title.setText(o.optString("person"));
            h.sub.setText(o.optString("category") + " - " + (in ? "Check-In" : "Check-Out"));
            h.time.setText(when(o.optString("timestamp")));
            String photo = o.isNull("photo") ? null : o.optString("photo");
            h.avatar.setVisibility(View.VISIBLE);
            ImageLoader.load(h.avatar, photo, 128);
            String visiting = o.isNull("visiting") ? "" : o.optString("visiting");
            if (!visiting.isEmpty()) {
                h.sub2.setText("Visiting: " + visiting);
                h.sub2.setVisibility(View.VISIBLE);
            } else {
                h.sub2.setVisibility(View.GONE);
            }
            h.itemView.setAlpha(0f);
            h.itemView.animate().alpha(1f).setDuration(200).start();
        }

        @Override
        public int getItemCount() { return filteredList.size(); }
    }
}

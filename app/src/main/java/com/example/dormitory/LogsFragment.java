package com.example.dormitory;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Entry/exit log list. Guards see everyone; residents see their own. Pull to refresh. */
public class LogsFragment extends Fragment {
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
        boolean isGuard = "Guard".equals(Api.getRole(requireContext()));
        empty.setText(isGuard ? "No entry or exit logs for today." : "No entry or exit logs yet.");
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(new Adapter());
        refresh.setColorSchemeColors(0xFF09090B);
        refresh.setOnRefreshListener(this::load);
        v.findViewById(R.id.addFab).setVisibility(View.GONE);
        refresh.setRefreshing(true);
        load();
        return v;
    }

    private void load() {
        Api.request(requireContext(), "GET", "/api/mobile/logs", null, (json, err) -> {
            if (!isAdded()) return;
            refresh.setRefreshing(false);
            if ("SESSION_EXPIRED".equals(err)) { ((MainActivity) requireActivity()).logout(); return; }
            if (err != null) { empty.setText(err); empty.setVisibility(View.VISIBLE); return; }
            data = json.optJSONArray("logs");
            if (data == null) data = new JSONArray();
            empty.setVisibility(data.length() == 0 ? View.VISIBLE : View.GONE);
            list.getAdapter().notifyDataSetChanged();
            list.scheduleLayoutAnimation();
        });
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
            TextView badge, title, sub, time;
            VH(View v) {
                super(v);
                badge = v.findViewById(R.id.badge);
                title = v.findViewById(R.id.title);
                sub = v.findViewById(R.id.sub);
                time = v.findViewById(R.id.time);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_row, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int i) {
            JSONObject o = data.optJSONObject(i);
            boolean in = "CheckIn".equals(o.optString("access_type"));
            h.badge.setText(in ? "IN" : "OUT");
            h.badge.setBackgroundResource(in ? R.drawable.bg_badge_green : R.drawable.bg_badge_blue);
            h.title.setText(o.optString("person"));
            h.sub.setText(o.optString("category") + " - " + (in ? "Check-In" : "Check-Out"));
            h.time.setText(when(o.optString("timestamp")));
            h.itemView.setAlpha(0f);
            h.itemView.animate().alpha(1f).setDuration(250).setStartDelay(Math.min(i, 8) * 30L).start();
        }

        @Override
        public int getItemCount() { return data.length(); }
    }
}


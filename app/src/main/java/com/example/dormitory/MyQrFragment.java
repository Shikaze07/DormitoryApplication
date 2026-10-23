package com.example.dormitory;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONObject;

/** Resident's personal ID badge — the QR guards scan at the gate. */
public class MyQrFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_my_qr, parent, false);
        ((TextView) v.findViewById(R.id.qrName)).setText(Api.getName(requireContext()));
        TextView info = v.findViewById(R.id.qrInfo);
        String room = Api.getRoom(requireContext());
        info.setText("@" + Api.getUsername(requireContext()) + "  ·  " + (room.isEmpty() ? "No room assigned" : "Room " + room));
        ImageView img = v.findViewById(R.id.qrImage);
        img.setImageBitmap(QrUtil.make(Api.getQr(requireContext()), 720));
        View card = v.findViewById(R.id.qrCard);
        card.setScaleX(0.9f);
        card.setScaleY(0.9f);
        card.setAlpha(0f);
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(350).start();

        // Fetch live profile to immediately reflect newly assigned rooms
        Api.request(requireContext(), "GET", "/api/mobile/me", null, (json, err) -> {
            if (!isAdded() || json == null) return;
            JSONObject user = json.optJSONObject("user");
            if (user != null) {
                Api.updateProfile(requireContext(), user);
                String updatedRoom = user.isNull("room") ? "" : user.optString("room");
                info.setText("@" + Api.getUsername(requireContext()) + "  ·  " + (updatedRoom.isEmpty() ? "No room assigned" : "Room " + updatedRoom));
            }
        });

        return v;
    }
}

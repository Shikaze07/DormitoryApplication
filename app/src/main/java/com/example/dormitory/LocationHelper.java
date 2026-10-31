package com.example.dormitory;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import java.util.concurrent.atomic.AtomicBoolean;

/** One-shot location fetch without Google Play services. Returns null when unavailable. */
public class LocationHelper {
    public interface Callback { void onLocation(Location loc); }

    public static boolean hasPermission(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** True when the device-wide location switch (GPS / network) is on. */
    public static boolean isLocationEnabled(Context c) {
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        try {
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings({"MissingPermission", "deprecation"})
    public static void get(Context c, Callback cb) {
        if (!hasPermission(c)) { cb.onLocation(null); return; }
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) { cb.onLocation(null); return; }

        Location best = null;
        for (String p : lm.getProviders(true)) {
            Location l = null;
            try { l = lm.getLastKnownLocation(p); } catch (SecurityException ignored) { }
            if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
        }
        // A fix from the last 20 seconds is fresh enough.
        if (best != null && System.currentTimeMillis() - best.getTime() < 20000) { cb.onLocation(best); return; }

        final Location fallback = best;
        final AtomicBoolean done = new AtomicBoolean(false);
        final Handler h = new Handler(Looper.getMainLooper());
        final java.util.List<LocationListener> listeners = new java.util.ArrayList<>();

        final Runnable finish = () -> { };
        LocationListener listener = new LocationListener() {
            @Override public void onLocationChanged(Location l) {
                if (done.compareAndSet(false, true)) {
                    for (LocationListener x : listeners) lm.removeUpdates(x);
                    cb.onLocation(l);
                }
            }
            @Override public void onStatusChanged(String p, int s, Bundle e) { }
            @Override public void onProviderEnabled(String p) { }
            @Override public void onProviderDisabled(String p) { }
        };
        listeners.add(listener);

        boolean requested = false;
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (lm.isProviderEnabled(p)) {
                    lm.requestSingleUpdate(p, listener, Looper.getMainLooper());
                    requested = true;
                }
            } catch (SecurityException ignored) { }
        }
        if (!requested) { cb.onLocation(fallback); return; }

        h.postDelayed(() -> {
            if (done.compareAndSet(false, true)) {
                for (LocationListener x : listeners) lm.removeUpdates(x);
                cb.onLocation(fallback);
            }
        }, 8000);
    }
}

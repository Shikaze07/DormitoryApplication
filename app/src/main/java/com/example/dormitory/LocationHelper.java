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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Location fetch without Google Play services. Returns a recent fix instantly, otherwise the first
 * fix that arrives (short timeout).
 * Returns null when unavailable.
 */
public class LocationHelper {
    public interface Callback { void onLocation(Location loc); }

    /** Stop early once a fix is at least this accurate (meters). */
    private static final float GOOD_ACCURACY_M = Float.MAX_VALUE;
    /** Give up waiting for a better fix after this long. */
    private static final long MAX_WAIT_MS = 6000;
    /** A cached fix older than this is never trusted. */
    private static final long MAX_CACHE_AGE_MS = 120000;

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

    private static boolean better(Location a, Location b) {
        if (b == null) return true;
        if (!a.hasAccuracy()) return false;
        if (!b.hasAccuracy()) return true;
        return a.getAccuracy() < b.getAccuracy();
    }

    @SuppressWarnings({"MissingPermission", "deprecation"})
    public static void get(Context c, Callback cb) {
        if (!hasPermission(c)) { cb.onLocation(null); return; }
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) { cb.onLocation(null); return; }

        final Location[] best = {null};
        for (String p : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null
                        && System.currentTimeMillis() - l.getTime() < MAX_CACHE_AGE_MS
                        && better(l, best[0])) {
                    best[0] = l;
                }
            } catch (SecurityException ignored) { }
        }
        if (best[0] != null && best[0].hasAccuracy() && best[0].getAccuracy() <= GOOD_ACCURACY_M) {
            cb.onLocation(best[0]);
            return;
        }

        final AtomicBoolean done = new AtomicBoolean(false);
        final Handler h = new Handler(Looper.getMainLooper());
        final List<LocationListener> listeners = new ArrayList<>();

        final Runnable finish = () -> {
            if (!done.compareAndSet(false, true)) return;
            for (LocationListener x : listeners) {
                try { lm.removeUpdates(x); } catch (SecurityException ignored) { }
            }
            cb.onLocation(best[0]);
        };

        LocationListener listener = new LocationListener() {
            @Override public void onLocationChanged(Location l) {
                if (done.get()) return;
                if (better(l, best[0])) best[0] = l;
                if (best[0].hasAccuracy() && best[0].getAccuracy() <= GOOD_ACCURACY_M) h.post(finish);
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
                    lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper());
                    requested = true;
                }
            } catch (SecurityException ignored) { }
        }
        if (!requested) { cb.onLocation(best[0]); return; }

        h.postDelayed(finish, MAX_WAIT_MS);
    }
}

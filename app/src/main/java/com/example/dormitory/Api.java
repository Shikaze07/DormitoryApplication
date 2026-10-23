package com.example.dormitory;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal REST client for the Dormitory web app's /api/mobile endpoints + stored session. */
public class Api {
    public interface Callback {
        /** Exactly one of json / error is non-null. Always called on the main thread. */
        void onResult(JSONObject json, String error);
    }

    public static final String DEFAULT_SERVER = "https://dormitory-management-system-nkqm.vercel.app";

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("dormitory", Context.MODE_PRIVATE);
    }

    public static String getServer(Context c) {
        String saved = prefs(c).getString("server", null);
        if (saved == null || saved.contains("10.0.2.2") || saved.contains("localhost")) {
            return DEFAULT_SERVER;
        }
        return saved;
    }

    public static void setServer(Context c, String url) {
        url = url.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (!url.startsWith("http")) url = "https://" + url;
        prefs(c).edit().putString("server", url).apply();
    }

    public static String getToken(Context c) { return prefs(c).getString("token", null); }
    public static String getRole(Context c) { return prefs(c).getString("role", ""); }
    public static String getName(Context c) { return prefs(c).getString("name", ""); }
    public static String getQr(Context c) { return prefs(c).getString("qr", ""); }
    public static String getRoom(Context c) { return prefs(c).getString("room", ""); }
    public static String getUsername(Context c) { return prefs(c).getString("username", ""); }

    public static void saveSession(Context c, String token, JSONObject user) {
        prefs(c).edit()
                .putString("token", token)
                .putString("role", user.optString("role"))
                .putString("name", user.optString("name"))
                .putString("qr", user.optString("qr_code"))
                .putString("room", user.isNull("room") ? "" : user.optString("room"))
                .putString("username", user.optString("username"))
                .apply();
    }

    public static void updateProfile(Context c, JSONObject user) {
        prefs(c).edit()
                .putString("name", user.optString("name"))
                .putString("role", user.optString("role"))
                .putString("qr", user.optString("qr_code"))
                .putString("room", user.isNull("room") ? "" : user.optString("room"))
                .putString("username", user.optString("username"))
                .apply();
    }

    public static void logout(Context c) {
        String server = getServer(c);
        prefs(c).edit().clear().apply();
        prefs(c).edit().putString("server", server).apply();
    }

    public static void request(Context c, String method, String path, JSONObject body, Callback cb) {
        final Context app = c.getApplicationContext();
        POOL.execute(() -> {
            JSONObject result = null;
            String error = null;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(getServer(app) + path).openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Accept", "application/json");
                String token = getToken(app);
                if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);
                if (body != null) {
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
                int code = conn.getResponseCode();
                InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                StringBuilder sb = new StringBuilder();
                if (is != null) {
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                    }
                }
                JSONObject json = sb.length() > 0 ? new JSONObject(sb.toString()) : new JSONObject();
                if (code >= 400) {
                    error = json.optString("error", "Request failed (" + code + ")");
                    if (code == 401 && token != null) error = "SESSION_EXPIRED";
                } else {
                    result = json;
                }
            } catch (java.net.ConnectException | java.net.SocketTimeoutException | java.net.UnknownHostException e) {
                error = "Can't reach the server. Check your connection.";
            } catch (Exception e) {
                error = "Something went wrong. Please try again.";
            } finally {
                if (conn != null) conn.disconnect();
            }
            final JSONObject r = result;
            final String err = error;
            MAIN.post(() -> cb.onResult(r, err));
        });
    }
}

package com.example.dormitory;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {
    private EditText username, password;
    private MaterialButton signIn;
    private TextView error;
    private View card;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Api.getToken(this) != null) { openMain(); return; }
        setContentView(R.layout.activity_login);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, w) -> {
            androidx.core.graphics.Insets s = w.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.ime());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return w;
        });
        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        signIn = findViewById(R.id.signIn);
        error = findViewById(R.id.error);
        card = findViewById(R.id.card);

        card.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up));
        signIn.setOnClickListener(v -> attemptLogin());
        password.setOnEditorActionListener((v, id, e) -> { attemptLogin(); return true; });
    }

    private void attemptLogin() {
        String u = username.getText().toString().trim();
        String p = password.getText().toString();
        if (u.isEmpty() || p.isEmpty()) { showError("Enter your username and password."); return; }
        showError(null);
        signIn.setEnabled(false);
        signIn.setText("Signing in...");
        try {
            JSONObject body = new JSONObject().put("username", u).put("password", p);
            Api.request(this, "POST", "/api/mobile/login", body, (json, err) -> {
                signIn.setEnabled(true);
                signIn.setText("Sign In");
                if (err != null) { showError(err); return; }
                Api.saveSession(this, json.optString("token"), json.optJSONObject("user"));
                openMain();
            });
        } catch (Exception e) { showError("Something went wrong."); }
    }

    private void showError(String msg) {
        error.setVisibility(msg == null ? View.GONE : View.VISIBLE);
        error.setText(msg);
        if (msg != null) {
            card.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake));
            android.os.Vibrator vib = getSystemService(android.os.Vibrator.class);
            if (vib != null) {
                vib.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}


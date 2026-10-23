package com.example.dormitory;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Full-size branded splash shown on launch, then routes to login (which auto-forwards if signed in). */
public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, w) -> {
            Insets s = w.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return w;
        });
        View img = findViewById(R.id.splashImage);
        View title = findViewById(R.id.splashTitle);
        img.setAlpha(0f);
        img.setScaleX(0.9f);
        img.setScaleY(0.9f);
        img.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600).start();
        title.setAlpha(0f);
        title.animate().alpha(1f).setStartDelay(300).setDuration(500).start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(this, LoginActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 1800);
    }
}
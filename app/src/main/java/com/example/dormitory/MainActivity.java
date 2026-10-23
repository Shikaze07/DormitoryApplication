package com.example.dormitory;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private boolean isGuard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Api.getToken(this) == null) { goLogin(); return; }
        setContentView(R.layout.activity_main);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, w) -> {
            androidx.core.graphics.Insets s = w.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.ime());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return w;
        });
        isGuard = "Guard".equals(Api.getRole(this));
        ((TextView) findViewById(R.id.greeting)).setText("Hi, " + Api.getName(this).split(" ")[0]);
        ((TextView) findViewById(R.id.roleLabel)).setText(isGuard ? "Security Guard" : "Resident");
        findViewById(R.id.logout).setOnClickListener(v -> logout());

        BottomNavigationView nav = findViewById(R.id.nav);
        nav.inflateMenu(isGuard ? R.menu.nav_guard : R.menu.nav_resident);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment f;
            if (id == R.id.nav_scan) f = new ScanFragment();
            else if (id == R.id.nav_logs) f = new LogsFragment();
            else if (id == R.id.nav_qr) f = new MyQrFragment();
            else f = new VisitorsFragment();
            show(f);
            return true;
        });
        if (savedInstanceState == null) {
            nav.setSelectedItemId(isGuard ? R.id.nav_scan : R.id.nav_qr);
        }
    }

    private void show(Fragment f) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.container, f)
                .commit();
    }

    /** Called by fragments when the API reports an expired session. */
    public void logout() {
        Api.logout(this);
        goLogin();
    }

    private void goLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}

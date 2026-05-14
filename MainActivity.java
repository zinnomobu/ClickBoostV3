package com.clickboost;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_REQ = 1001;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("clickboost", MODE_PRIVATE);

        setupMultiplierSlider();
        setupCPSSlider();
        setupLaunchButton();
        updateStatusViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusViews();
    }

    // ── Multiplier slider (1–20) ──────────────────────────────────────────────
    private void setupMultiplierSlider() {
        SeekBar bar  = findViewById(R.id.seekMultiplier);
        TextView lbl = findViewById(R.id.lblMultiplier);

        int saved = prefs.getInt("multiplier", 5);
        bar.setMax(19);           // 1-20
        bar.setProgress(saved - 1);
        lbl.setText("Click Multiplier: " + saved + "x");

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int val = p + 1;
                lbl.setText("Click Multiplier: " + val + "x");
                prefs.edit().putInt("multiplier", val).apply();
                notifyService();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    // ── CPS slider (1–30) ─────────────────────────────────────────────────────
    private void setupCPSSlider() {
        SeekBar bar  = findViewById(R.id.seekCPS);
        TextView lbl = findViewById(R.id.lblCPS);

        int saved = prefs.getInt("cps", 10);
        bar.setMax(29);           // 1-30
        bar.setProgress(saved - 1);
        lbl.setText("Speed: " + saved + " clicks/sec");

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int val = p + 1;
                lbl.setText("Speed: " + val + " clicks/sec");
                prefs.edit().putInt("cps", val).apply();
                notifyService();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    // ── Launch button ──────────────────────────────────────────────────────────
    private void setupLaunchButton() {
        Button btn = findViewById(R.id.btnLaunch);
        btn.setOnClickListener(v -> {
            if (!hasOverlayPermission()) {
                requestOverlayPermission();
                return;
            }
            if (!isAccessibilityEnabled()) {
                Toast.makeText(this,
                    "Enable ClickBoost in Accessibility Settings → Installed Apps",
                    Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            startFloatingService();
            finish(); // go back to game/app
        });
    }

    private void startFloatingService() {
        Intent i = new Intent(this, FloatingService.class);
        i.putExtra("multiplier", prefs.getInt("multiplier", 5));
        i.putExtra("cps",        prefs.getInt("cps", 10));
        startForegroundService(i);
        Toast.makeText(this, "ClickBoost active! Drag the target button.", Toast.LENGTH_SHORT).show();
    }

    // ── Permissions ────────────────────────────────────────────────────────────
    private boolean hasOverlayPermission() {
        return Settings.canDrawOverlays(this);
    }

    private void requestOverlayPermission() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(i, OVERLAY_PERMISSION_REQ);
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager am =
                (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> list =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : list) {
            if (info.getId().contains(getPackageName())) return true;
        }
        return false;
    }

    // ── Update UI status labels ────────────────────────────────────────────────
    private void updateStatusViews() {
        TextView overlayStatus      = findViewById(R.id.tvOverlayStatus);
        TextView accessibilityStatus = findViewById(R.id.tvAccessibilityStatus);
        Button   launchBtn          = findViewById(R.id.btnLaunch);

        boolean overlay      = hasOverlayPermission();
        boolean accessibility = isAccessibilityEnabled();

        overlayStatus.setText("Overlay Permission: " + (overlay ? "✅ Granted" : "❌ Not Granted"));
        accessibilityStatus.setText("Accessibility Service: " + (accessibility ? "✅ Enabled" : "❌ Disabled"));

        launchBtn.setText((!overlay || !accessibility)
                ? "Grant Permissions & Launch"
                : "🚀 Launch Overlay");
    }

    // Tell the running service to reload settings
    private void notifyService() {
        Intent i = new Intent(this, FloatingService.class);
        i.setAction("UPDATE_SETTINGS");
        i.putExtra("multiplier", prefs.getInt("multiplier", 5));
        i.putExtra("cps",        prefs.getInt("cps", 10));
        startService(i);
    }
}

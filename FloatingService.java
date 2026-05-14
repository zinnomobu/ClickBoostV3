package com.clickboost;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;

public class FloatingService extends Service {

    private static final String CHANNEL_ID = "clickboost_channel";
    private static final int    NOTIF_ID   = 42;

    // ── State ─────────────────────────────────────────────────────────────────
    private WindowManager   windowManager;
    private View            floatingView;
    private WindowManager.LayoutParams params;

    private int multiplier = 5;
    private int cps        = 10;

    // drag tracking
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private long  touchDownTime;
    private boolean isDragging = false;
    private static final int DRAG_THRESHOLD_PX = 12;
    private static final int TAP_MAX_MS        = 300;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        loadPrefs();
        showFloating();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "UPDATE_SETTINGS".equals(intent.getAction())) {
            multiplier = intent.getIntExtra("multiplier", multiplier);
            cps        = intent.getIntExtra("cps", cps);
            updateBadge();
        } else if (intent != null) {
            multiplier = intent.getIntExtra("multiplier", multiplier);
            cps        = intent.getIntExtra("cps", cps);
        }
        return START_STICKY;
    }

    // ── Build the floating overlay ────────────────────────────────────────────
    private void showFloating() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_layout, null);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;

        // Start in the center-right of screen
        DisplayMetrics dm = getResources().getDisplayMetrics();
        params.x = dm.widthPixels - 220;
        params.y = dm.heightPixels / 2;

        windowManager.addView(floatingView, params);
        updateBadge();
        setupTouchListener();
        setupCloseButton();
    }

    // ── Touch: drag vs tap ────────────────────────────────────────────────────
    private void setupTouchListener() {
        View clickTarget = floatingView.findViewById(R.id.btnClickTarget);

        clickTarget.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:
                    initialX      = params.x;
                    initialY      = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    touchDownTime = System.currentTimeMillis();
                    isDragging    = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - initialTouchX;
                    float dy = event.getRawY() - initialTouchY;
                    if (!isDragging && (Math.abs(dx) > DRAG_THRESHOLD_PX
                                     || Math.abs(dy) > DRAG_THRESHOLD_PX)) {
                        isDragging = true;
                    }
                    if (isDragging) {
                        params.x = initialX + (int) dx;
                        params.y = initialY + (int) dy;
                        windowManager.updateViewLayout(floatingView, params);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    long duration = System.currentTimeMillis() - touchDownTime;
                    if (!isDragging && duration < TAP_MAX_MS) {
                        handleTap(v);
                    }
                    return true;
            }
            return false;
        });
    }

    // ── What happens on a tap ─────────────────────────────────────────────────
    private void handleTap(View v) {
        // Haptic feedback
        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib != null && vib.hasVibrator()) {
            vib.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
        }

        // Send (multiplier - 1) extra clicks via accessibility service
        // The user's own finger provides the 1st click, we add the rest.
        ClickAccessibilityService svc = ClickAccessibilityService.getInstance();
        if (svc != null) {
            // Coordinates of the click target button center on screen
            int[] loc = new int[2];
            v.getLocationOnScreen(loc);
            float cx = loc[0] + v.getWidth()  / 2f;
            float cy = loc[1] + v.getHeight() / 2f;
            svc.performMultiClick(cx, cy, multiplier - 1, cps);
        }

        // Visual pulse feedback
        v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(60)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(60).start())
                .start();
    }

    private void setupCloseButton() {
        View closeBtn = floatingView.findViewById(R.id.btnClose);
        closeBtn.setOnClickListener(v -> stopSelf());
    }

    private void updateBadge() {
        if (floatingView == null) return;
        TextView badge = floatingView.findViewById(R.id.tvBadge);
        if (badge != null) badge.setText(multiplier + "x\n" + cps + "cps");
    }

    // ── Foreground notification ───────────────────────────────────────────────
    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "ClickBoost", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("ClickBoost overlay service");
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        Intent stop = new Intent(this, FloatingService.class);
        stop.setAction("STOP");
        PendingIntent pi = PendingIntent.getService(this, 0, stop,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ClickBoost Active")
                .setContentText("Tap the floating button to multiply your clicks")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .addAction(android.R.drawable.ic_delete, "Stop", pi)
                .setOngoing(true)
                .build();
    }

    private void loadPrefs() {
        SharedPreferences p = getSharedPreferences("clickboost", MODE_PRIVATE);
        multiplier = p.getInt("multiplier", 5);
        cps        = p.getInt("cps", 10);
    }

    @Override
    public void onDestroy() {
        if (floatingView != null) windowManager.removeView(floatingView);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

package com.clickboost;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

/**
 * ClickAccessibilityService
 *
 * Uses Android's GestureDescription API to inject extra tap events.
 * The user taps once (that's real tap #1); we fire (multiplier - 1) more
 * synthetic taps at the same screen coordinates, spread over time
 * according to the chosen CPS value.
 */
public class ClickAccessibilityService extends AccessibilityService {

    private static ClickAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static ClickAccessibilityService getInstance() { return instance; }

    @Override
    public void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    /**
     * Fire `extraClicks` synthetic taps at (x, y).
     *
     * @param x           screen X
     * @param y           screen Y
     * @param extraClicks how many ADDITIONAL clicks beyond the user's own tap
     * @param cps         clicks per second (controls the inter-click delay)
     */
    public void performMultiClick(float x, float y, int extraClicks, int cps) {
        if (extraClicks <= 0) return;

        // Minimum sane delay between injected taps: 16 ms (≈ 60fps)
        long delayMs = Math.max(16L, 1000L / cps);

        for (int i = 0; i < extraClicks; i++) {
            final long fireAt = (i + 1) * delayMs;
            handler.postDelayed(() -> injectTap(x, y), fireAt);
        }
    }

    /** Dispatch a single synthetic tap via GestureDescription. */
    private void injectTap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 50); // 50 ms press

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();

        dispatchGesture(gesture, null, null);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { /* not needed */ }

    @Override
    public void onInterrupt() { handler.removeCallbacksAndMessages(null); }
}

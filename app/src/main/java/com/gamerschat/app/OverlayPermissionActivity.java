package com.gamerschat.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

// Step 1 of the floating-bubble feature: explains to the person why
// the app wants permission to draw over other apps (this is what
// makes the bubble possible), then sends them to Android's own
// settings screen to grant it. Android requires this permission to
// be granted through its own UI -- no app can silently turn it on,
// which is why this screen exists at all.
public class OverlayPermissionActivity extends Activity {

    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_CODE_NOTIFICATION_PERMISSION = 1002;

    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Android 13+ (API 33+) requires this runtime permission for
        // ANY notification to show, including Toast messages fired
        // from a background Service -- without it, toasts and
        // notifications are silently swallowed with no error at all.
        // This is what was making our earlier diagnostic Toasts
        // invisible even though the underlying code was running fine.
        requestNotificationPermissionIfNeeded();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#0a0a0a"));
        layout.setPadding(48, 96, 48, 48);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Enable Floating Bubble");
        title.setTextColor(Color.parseColor("#ff8c42"));
        title.setTextSize(22);
        title.setPadding(0, 0, 0, 32);

        TextView explanation = new TextView(this);
        explanation.setText(
                "Voxx Chat can show a small floating bubble over your game, "
                        + "so you can see call status and controls without leaving "
                        + "your match.\n\nThis needs one special permission: "
                        + "\"Display over other apps.\" Android requires you to grant "
                        + "this yourself on the next screen -- tap the button below, "
                        + "then find Voxx Chat in the list and turn the toggle on."
        );
        explanation.setTextColor(Color.WHITE);
        explanation.setTextSize(15);
        explanation.setPadding(0, 0, 0, 48);

        Button grantButton = new Button(this);
        grantButton.setText("Open Permission Settings");
        grantButton.setOnClickListener(v -> requestOverlayPermission());

        statusText = new TextView(this);
        statusText.setTextColor(Color.parseColor("#3ddc97"));
        statusText.setTextSize(14);
        statusText.setPadding(0, 32, 0, 32);
        updateStatusText();

        Button showBubbleButton = new Button(this);
        showBubbleButton.setText("Show Floating Bubble (Test)");
        showBubbleButton.setOnClickListener(v -> {
            if (hasOverlayPermission()) {
                startService(new Intent(this, BubbleService.class));
            } else {
                statusText.setText("⚠️ Grant the permission first, then try again.");
            }
        });

        Button hideBubbleButton = new Button(this);
        hideBubbleButton.setText("Hide Floating Bubble");
        hideBubbleButton.setOnClickListener(v -> stopService(new Intent(this, BubbleService.class)));

        // TEMPORARY: lets us test the bubble's appearance changing
        // WITHOUT needing a second device to send a real request.
        // This calls the exact same appearance-update path the real
        // bridge uses (via a broadcast BubbleService listens for),
        // so a successful color change here proves that half of the
        // pipeline works, isolating whether any remaining issue is in
        // the WebView/JS bridge side specifically.
        TextView testLabel = new TextView(this);
        testLabel.setText("Test bubble appearance (no 2nd device needed):");
        testLabel.setTextColor(Color.parseColor("#888888"));
        testLabel.setTextSize(12);
        testLabel.setPadding(0, 48, 0, 8);

        Button testRingingButton = new Button(this);
        testRingingButton.setText("Test: Show Ringing State");
        testRingingButton.setOnClickListener(v -> sendTestState("ringing"));

        Button testInCallButton = new Button(this);
        testInCallButton.setText("Test: Show In-Call State");
        testInCallButton.setOnClickListener(v -> sendTestState("in_call"));

        Button testIdleButton = new Button(this);
        testIdleButton.setText("Test: Show Idle State");
        testIdleButton.setOnClickListener(v -> sendTestState("idle"));

        layout.addView(title);
        layout.addView(explanation);
        layout.addView(grantButton);
        layout.addView(statusText);
        layout.addView(showBubbleButton);
        layout.addView(hideBubbleButton);
        layout.addView(testLabel);
        layout.addView(testRingingButton);
        layout.addView(testInCallButton);
        layout.addView(testIdleButton);

        // Wrapped in a ScrollView: a plain LinearLayout does NOT
        // scroll on its own, so as more buttons were added to this
        // screen, anything past the bottom of the visible screen
        // became genuinely unreachable -- not hidden by a bug, just
        // literally off-screen with no way to scroll to it. This is
        // very likely why the new test buttons appeared invisible.
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(layout);
        setContentView(scrollView);
    }

    private void sendTestState(String state) {
        Intent intent = new Intent(this, BubbleService.class);
        intent.setAction("TEST_STATE_UPDATE");
        intent.putExtra("state", state);
        startService(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check every time this screen becomes visible again --
        // covers the person granting the permission in Settings and
        // then pressing back to return here.
        updateStatusText();
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) { // Build.VERSION_CODES.TIRAMISU
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_NOTIFICATION_PERMISSION
                );
            }
        }
        // Below Android 13, this permission doesn't exist as a
        // runtime permission -- notifications/toasts work without it.
    }

    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        // Below Android M, this permission is granted automatically
        // at install time -- no runtime check needed.
        return true;
    }

    private void updateStatusText() {
        if (hasOverlayPermission()) {
            statusText.setText("✅ Permission granted. The floating bubble is ready to use.");
        } else {
            statusText.setText("⏳ Permission not granted yet.");
        }
    }
}

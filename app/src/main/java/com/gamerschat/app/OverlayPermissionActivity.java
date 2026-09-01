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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

// One-time explanation screen for the "draw over other apps"
// permission the floating bubble needs. Android requires this to be
// granted through its own settings UI -- no app can silently turn it
// on -- so this screen exists purely to explain why, then hand off
// to that settings screen. Once granted, it starts the bubble
// service and closes itself automatically, returning the person to
// wherever they were (the real Voxx Chat app).
public class OverlayPermissionActivity extends Activity {

    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_CODE_NOTIFICATION_PERMISSION = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Android 13+ (API 33+) requires this runtime permission for
        // ANY notification to show, including ones fired from a
        // background Service -- without it they're silently swallowed.
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
                        + "so you can see call status without leaving your match.\n\n"
                        + "This needs one special permission: \"Display over other "
                        + "apps.\" Tap below, then find Voxx Chat in the list and "
                        + "turn the toggle on."
        );
        explanation.setTextColor(Color.WHITE);
        explanation.setTextSize(15);
        explanation.setPadding(0, 0, 0, 48);

        Button grantButton = new Button(this);
        grantButton.setText("Open Permission Settings");
        grantButton.setOnClickListener(v -> requestOverlayPermission());

        Button skipButton = new Button(this);
        skipButton.setText("Not now");
        skipButton.setOnClickListener(v -> finish());

        layout.addView(title);
        layout.addView(explanation);
        layout.addView(grantButton);
        layout.addView(skipButton);

        setContentView(layout);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Covers returning from Android's settings screen after
        // granting the permission there -- if it's now granted, start
        // the bubble and close this screen automatically rather than
        // making the person navigate back manually.
        if (hasOverlayPermission()) {
            startService(new Intent(this, BubbleService.class));
            finish();
        }
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
    }

    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }
}


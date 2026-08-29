package com.gamerschat.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

// Step 1 of the floating-bubble feature: explains to the person why
// the app wants permission to draw over other apps (this is what
// makes the bubble possible), then sends them to Android's own
// settings screen to grant it. Android requires this permission to
// be granted through its own UI -- no app can silently turn it on,
// which is why this screen exists at all.
public class OverlayPermissionActivity extends Activity {

    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;

    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        statusText.setPadding(0, 32, 0, 0);
        updateStatusText();

        layout.addView(title);
        layout.addView(explanation);
        layout.addView(grantButton);
        layout.addView(statusText);

        setContentView(layout);
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

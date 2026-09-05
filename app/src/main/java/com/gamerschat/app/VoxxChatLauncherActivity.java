package com.gamerschat.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import com.google.androidbrowserhelper.trusted.LauncherActivity;

// This is the OFFICIAL, documented way to pass native Android data
// into a Trusted Web Activity's URL (per Chrome's own developer
// docs: "Passing Information to a Trusted Web Activity using Query
// Parameters"). Extending LauncherActivity and overriding
// getLaunchingUrl() lets us append our shared deviceId, so the real
// visible app and the floating bubble's hidden WebView -- two
// otherwise completely separate storage engines with no shared
// localStorage -- end up using the exact same identity.
//
// This activity is also where the whole floating-bubble feature now
// lives from the user's point of view: no separate "Bubble Setup"
// icon anymore. Opening the real Voxx Chat app automatically checks
// for the overlay permission and starts the bubble service, or
// routes to a one-time explanation screen first if permission
// hasn't been granted yet (Android requires that to be an explicit,
// explained user action -- it can't be silently skipped).
public class VoxxChatLauncherActivity extends LauncherActivity {

    private static final String PREFS_NAME = "voxx_chat_shared_prefs";
    private static final String PREF_DEVICE_ID = "device_id";
    private static final String PREF_HAS_SEEN_OVERLAY_EXPLANATION = "has_seen_overlay_explanation";
    private static final String PREF_USER_DISABLED_BUBBLE = "user_disabled_bubble";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleBubbleToggleIntent();
        setupBubbleFeature();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Also re-check on resume, in case the person just came back
        // from granting the permission in Android's settings screen.
        setupBubbleFeature();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleBubbleToggleIntent();
    }

    // Detects the ?bubbleAction=enable/disable signal the web page
    // sends by navigating to a special URL (see the bubble toggle
    // switch in index.html). A TWA has no other channel available
    // for the visible app to tell native code to do something --
    // there's no JS bridge in a real Chrome tab, only URL parameters
    // going in, and this pattern reusing that same mechanism.
    private void handleBubbleToggleIntent() {
        Uri data = getIntent() != null ? getIntent().getData() : null;
        if (data == null) return;

        String action = data.getQueryParameter("bubbleAction");
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        if ("enable".equals(action)) {
            prefs.edit().putBoolean(PREF_USER_DISABLED_BUBBLE, false).apply();
            if (hasOverlayPermission()) {
                startService(new Intent(this, BubbleService.class));
            } else {
                startActivity(new Intent(this, OverlayPermissionActivity.class));
            }
        } else if ("disable".equals(action)) {
            prefs.edit().putBoolean(PREF_USER_DISABLED_BUBBLE, true).apply();
            stopService(new Intent(this, BubbleService.class));
        }
    }

    private void setupBubbleFeature() {
        // If the person has explicitly turned the bubble off via the
        // in-app toggle, don't auto-start it just because the app
        // reopened -- respect their choice until they turn it back on.
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean userWantsBubbleOff = prefs.getBoolean(PREF_USER_DISABLED_BUBBLE, false);
        if (userWantsBubbleOff) return;

        if (hasOverlayPermission()) {
            // Already granted (from a previous visit, or below M
            // where it's automatic) -- just make sure the bubble
            // service is running, without showing anything extra.
            startService(new Intent(this, BubbleService.class));
            return;
        }

        // Not granted yet. Only interrupt with the explanation screen
        // the FIRST time, so returning users aren't nagged repeatedly
        // if they've already dismissed it once without granting.
        boolean hasSeenExplanation = prefs.getBoolean(PREF_HAS_SEEN_OVERLAY_EXPLANATION, false);
        if (!hasSeenExplanation) {
            prefs.edit().putBoolean(PREF_HAS_SEEN_OVERLAY_EXPLANATION, true).apply();
            startActivity(new Intent(this, OverlayPermissionActivity.class));
        }
    }

    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    @Override
    protected Uri getLaunchingUrl() {
        Uri original = super.getLaunchingUrl();
        String sharedDeviceId = getOrCreateSharedDeviceId();
        return original.buildUpon()
                .appendQueryParameter("nativeDeviceId", sharedDeviceId)
                .build();
    }

    // Identical logic to BubbleService's version, deliberately kept
    // as plain duplicated code rather than a shared utility class --
    // this is a small, stable piece of logic and keeping each file
    // self-contained avoids introducing a new inter-file dependency
    // for something this simple.
    private String getOrCreateSharedDeviceId() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String deviceId = prefs.getString(PREF_DEVICE_ID, null);
        if (deviceId == null) {
            deviceId = java.util.UUID.randomUUID().toString();
            prefs.edit().putString(PREF_DEVICE_ID, deviceId).apply();
        }
        return deviceId;
    }
}


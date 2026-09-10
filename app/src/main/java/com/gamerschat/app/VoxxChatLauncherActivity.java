package com.gamerschat.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import com.google.androidbrowserhelper.trusted.LauncherActivity;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

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
// icon anymore. Opening the real Voxx Chat app checks the REAL
// bubble-enabled preference from the backend (the one channel
// genuinely shared between this app's Chrome-based WebView and the
// bubble's separate hidden WebView) and starts/stops the service to
// match, or routes to a one-time explanation screen first if
// permission hasn't been granted yet.
//
// An earlier version tried signaling the toggle via a special URL
// (?bubbleAction=...), but that only works when the app is launched
// FRESH via a deep link -- navigating an already-open Chrome tab
// never creates a new Android Intent, so native code never actually
// received it. Checking real backend state on every resume instead
// is what actually works.
public class VoxxChatLauncherActivity extends LauncherActivity {

    private static final String PREFS_NAME = "voxx_chat_shared_prefs";
    private static final String PREF_DEVICE_ID = "device_id";
    private static final String PREF_OVERLAY_EXPLANATION_LAST_SHOWN = "overlay_explanation_last_shown";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupBubbleFeature();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check every time the app comes back to the foreground --
        // this is what picks up a toggle change made in the web UI,
        // since there's no direct/instant channel from that toggle
        // into this native code.
        setupBubbleFeature();
    }

    private void setupBubbleFeature() {
        String sharedDeviceId = getOrCreateSharedDeviceId();

        new Thread(() -> {
            Boolean bubbleEnabledOnBackend = fetchBubbleEnabledFromBackend(sharedDeviceId);
            // null means the fetch failed (offline, etc.) -- in that
            // case, fall back to whatever the overlay permission
            // already implies, rather than forcing the bubble off
            // just because of a transient network issue.
            runOnUiThread(() -> {
                if (bubbleEnabledOnBackend != null && !bubbleEnabledOnBackend) {
                    stopService(new Intent(this, BubbleService.class));
                    return;
                }

                if (hasOverlayPermission()) {
                    startService(new Intent(this, BubbleService.class));
                    return;
                }

                // Permission not granted yet.
                android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                // Show the explanation screen whenever permission is
                // genuinely missing -- NOT just once ever (that
                // earlier approach meant anyone who dismissed it once,
                // even accidentally, would then have to find the
                // Android settings screen manually forever after,
                // which is exactly the real complaint this fixes). A
                // short cooldown avoids reopening it if the person
                // JUST dismissed it moments ago (e.g. tapped "Not
                // now" and immediately reopened the app).
                long lastShownAt = prefs.getLong(PREF_OVERLAY_EXPLANATION_LAST_SHOWN, 0);
                boolean cooldownElapsed = (System.currentTimeMillis() - lastShownAt) > 10_000;
                if (cooldownElapsed && (bubbleEnabledOnBackend == null || bubbleEnabledOnBackend)) {
                    prefs.edit().putLong(PREF_OVERLAY_EXPLANATION_LAST_SHOWN, System.currentTimeMillis()).apply();
                    startActivity(new Intent(this, OverlayPermissionActivity.class));
                }
            });
        }).start();
    }

    // Reads the bubbleEnabled flag directly from your existing
    // get-code backend function -- plain HttpURLConnection rather
    // than adding a new HTTP library dependency for one simple GET-
    // like POST request. Returns null on any failure so callers can
    // distinguish "genuinely disabled" from "couldn't check."
    private Boolean fetchBubbleEnabledFromBackend(String deviceId) {
        try {
            URL url = new URL(getString(R.string.twa_launch_url) + ".netlify/functions/get-code");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            JSONObject body = new JSONObject();
            body.put("deviceId", deviceId);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) return null;

            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()));
            StringBuilder responseText = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) responseText.append(line);
            reader.close();

            JSONObject json = new JSONObject(responseText.toString());
            if (!json.optBoolean("success", false)) {
                android.util.Log.e("VoxxChatBubbleCheck", "Backend returned success=false: " + responseText);
                return null;
            }
            return json.optBoolean("bubbleEnabled", true);
        } catch (Exception e) {
            // TEMPORARY diagnostic: this was previously silently
            // swallowed, giving zero visibility into why the toggle
            // check might be failing. Logging it (visible via
            // Toast in setupBubbleFeature, and in real crash/log
            // tools if used later) is how we find the real cause
            // instead of guessing.
            android.util.Log.e("VoxxChatBubbleCheck", "Fetch failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
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


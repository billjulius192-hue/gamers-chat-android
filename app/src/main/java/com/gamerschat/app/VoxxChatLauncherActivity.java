package com.gamerschat.app;

import android.net.Uri;
import com.google.androidbrowserhelper.trusted.LauncherActivity;

// This is the OFFICIAL, documented way to pass native Android data
// into a Trusted Web Activity's URL (per Chrome's own developer
// docs: "Passing Information to a Trusted Web Activity using Query
// Parameters"). Extending LauncherActivity and overriding
// getLaunchingUrl() lets us append our shared deviceId, so the real
// visible app and the floating bubble's hidden WebView -- two
// otherwise completely separate storage engines with no shared
// localStorage -- end up using the exact same identity.
public class VoxxChatLauncherActivity extends LauncherActivity {

    private static final String PREFS_NAME = "voxx_chat_shared_prefs";
    private static final String PREF_DEVICE_ID = "device_id";

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

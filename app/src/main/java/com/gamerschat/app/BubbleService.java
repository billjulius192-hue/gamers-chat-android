package com.gamerschat.app;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

// Step 2 of the floating-bubble feature: a Foreground Service that
// draws a small circular bubble on top of whatever else is on
// screen, including a running game. This is the actual visual thing
// the whole Android-wrapper effort was for.
//
// Why a Service and not just an Activity: an Activity's window
// disappears the moment you switch to another app (that's the
// entire point of "leaving the game" we're trying to avoid). A
// Service has no window of its own by default, but CAN create and
// manage its own WindowManager-level view that persists across app
// switches -- that persistent view is the bubble.
public class BubbleService extends Service {

    private static final String CHANNEL_ID = "voxx_chat_bubble_channel";
    private static final int FOREGROUND_NOTIFICATION_ID = 2001;

    private WindowManager windowManager;
    private View bubbleView;
    private WindowManager.LayoutParams bubbleParams;

    // The hidden WebView that actually runs your real, already-working
    // PWA in the background -- this is what makes the bubble able to
    // reflect and control REAL call state instead of being a
    // disconnected native mockup. It's added to the window manager
    // with zero size (invisible) rather than not attached at all,
    // because an unattached WebView on some Android versions gets
    // throttled/suspended much more aggressively.
    private WebView hiddenWebView;

    // Tracks what the bubble should currently show. Updated by
    // JS calling back into bridgeToAndroid.updateBubbleState(...).
    private volatile String currentCallState = "idle"; // idle | ringing | in_call

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Android requires a Foreground Service to show a persistent
        // notification -- this is a real Android platform rule, not
        // something we're choosing, and it's what makes the service
        // (and therefore the bubble) allowed to keep running even
        // when the app itself isn't in the foreground.
        //
        // The explicit type argument (matching the manifest's
        // foregroundServiceType="specialUse") is required on Android
        // 10+ alongside the manifest declaration -- omitting it here
        // can still throw on some API levels even with the manifest
        // entry present.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    buildForegroundNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification());
        }

        if (bubbleView == null) {
            showBubble();
        }

        if (hiddenWebView == null) {
            setupHiddenWebView();
        }

        // If Android kills this service to free memory, restart it
        // with the same intent rather than leaving the bubble gone
        // silently.
        return START_STICKY;
    }

    private void showBubble() {
        if (!Settings.canDrawOverlays(this)) {
            // Safety check: if permission was somehow revoked after
            // this service started, don't attempt to draw -- doing so
            // would throw and crash the whole service.
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        // A real circle (not a square with a background color) using
        // a GradientDrawable shaped as an oval -- with equal width
        // and height, an oval renders as a perfect circle.
        GradientDrawable circleShape = new GradientDrawable();
        circleShape.setShape(GradientDrawable.OVAL);
        circleShape.setColor(Color.parseColor("#3ddc97"));

        TextView bubble = new TextView(this);
        bubble.setText("🎙️");
        bubble.setTextSize(18); // was 28 -- too large for a small bubble
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(circleShape);
        bubbleView = bubble;

        // Size based on actual screen density (dp -> px) instead of a
        // raw pixel number, so it's a consistent, genuinely small
        // size across different phones rather than looking huge on
        // higher-density screens. 48dp is a standard, comfortable
        // touch-target size, not an oversized block.
        int bubbleSizePx = (int) (48 * getResources().getDisplayMetrics().density);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        bubbleParams = new WindowManager.LayoutParams(
                bubbleSizePx, bubbleSizePx, // 48dp, converted to real pixels for this screen
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 0;
        bubbleParams.y = 300;

        // Makes the bubble draggable anywhere on screen, and treats a
        // tap-without-drag as a click (currently just logs; will
        // expand the bubble to show call controls in a later step).
        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            isDragging = true;
                        }
                        bubbleParams.x = initialX + deltaX;
                        bubbleParams.y = initialY + deltaY;
                        windowManager.updateViewLayout(bubbleView, bubbleParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            // TODO (next step): expand to show call
                            // controls / the send-request shortcut
                            // instead of just this placeholder.
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(bubbleView, bubbleParams);
    }

    // Loads your real, existing PWA into an invisible WebView running
    // in the background. This is the actual bridge: the web app's
    // JavaScript (already built, already working) can call into
    // Android via bridgeToAndroid.*, and Android can call back into
    // the page's JS via evaluateJavascript(...) below.
    @SuppressLint("SetJavaScriptEnabled")
    private void setupHiddenWebView() {
        hiddenWebView = new WebView(this);

        WebSettings settings = hiddenWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true); // needed for localStorage (deviceId, session data)
        settings.setMediaPlaybackRequiresUserGesture(false); // needed for the ringtone / call audio to play without a tap

        hiddenWebView.addJavascriptInterface(new AndroidBridge(), "bridgeToAndroid");

        hiddenWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Confirms to us (via logcat/Crashlytics-style logging
                // later) that the page actually loaded successfully.
                injectBridgeReadyFlag();
            }
        });

        hiddenWebView.loadUrl(getString(R.string.twa_launch_url));

        // Attach it to the window manager with zero visible size --
        // present in the view hierarchy (so it isn't paused as
        // aggressively as a fully detached WebView) but genuinely
        // invisible to the user.
        WindowManager.LayoutParams hiddenParams = new WindowManager.LayoutParams(
                1, 1,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        hiddenParams.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(hiddenWebView, hiddenParams);
    }

    // A small, deliberately simple marker so the web page's own JS
    // can detect "I'm running inside the native bubble service" and
    // adjust behavior if useful later (e.g. skip showing its own
    // install banner).
    private void injectBridgeReadyFlag() {
        runOnWebView("window.__voxxBubbleBridgeReady = true;");
    }

    private void runOnWebView(String js) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (hiddenWebView != null) {
                hiddenWebView.evaluateJavascript(js, null);
            }
        });
    }

    // The actual bridge object exposed to the page's JavaScript as
    // `window.bridgeToAndroid`. Methods here are deliberately small
    // and specific -- each one is a single fact the web app already
    // knows and needs to hand to native code, not a general-purpose
    // remote-control surface.
    private class AndroidBridge {

        // Called by the PWA's JS whenever call state changes (idle,
        // an incoming request is ringing, or a call is actively
        // connected). Runs on a background JS thread, so we hop back
        // to the main thread before touching any UI.
        @JavascriptInterface
        public void updateBubbleState(String state) {
            currentCallState = state;
            new Handler(Looper.getMainLooper()).post(() -> updateBubbleAppearance(state));
        }
    }

    // Changes the bubble's color/icon to reflect real call state,
    // called from the JS bridge above. Kept separate from
    // showBubble()'s initial creation so it can be called repeatedly
    // without recreating the whole view.
    private void updateBubbleAppearance(String state) {
        if (!(bubbleView instanceof TextView)) return;
        TextView bubble = (TextView) bubbleView;

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);

        switch (state) {
            case "ringing":
                shape.setColor(Color.parseColor("#ff8c42")); // orange -- matches incoming-request color elsewhere in the app
                bubble.setText("📞");
                break;
            case "in_call":
                shape.setColor(Color.parseColor("#3ddc97")); // mint -- active call
                bubble.setText("🎙️");
                break;
            default: // idle
                shape.setColor(Color.parseColor("#444444")); // dim gray -- nothing happening
                bubble.setText("🎙️");
                break;
        }
        bubble.setBackground(shape);
    }

    private Notification buildForegroundNotification() {
        Intent notificationIntent = new Intent(this, OverlayPermissionActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Voxx Chat bubble active")
                .setContentText("Tap to manage the floating bubble.")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Voxx Chat Bubble",
                    NotificationManager.IMPORTANCE_LOW // low = no sound/vibration, just persistent
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bubbleView != null && windowManager != null) {
            windowManager.removeView(bubbleView);
            bubbleView = null;
        }
        if (hiddenWebView != null && windowManager != null) {
            windowManager.removeView(hiddenWebView);
            hiddenWebView.destroy();
            hiddenWebView = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bindable service -- only started/stopped.
    }
}

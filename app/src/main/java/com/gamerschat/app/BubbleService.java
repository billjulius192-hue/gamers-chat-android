package com.gamerschat.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
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

        // Simple placeholder bubble for now: a colored circle with
        // text. This gets replaced with a proper icon/mic-state
        // indicator once this basic version is confirmed working.
        TextView bubble = new TextView(this);
        bubble.setText("🎙️");
        bubble.setTextSize(28);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackgroundColor(Color.parseColor("#3ddc97"));
        bubbleView = bubble;

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        bubbleParams = new WindowManager.LayoutParams(
                160, 160, // size in pixels -- roughly a fingertip-sized circle
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
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bindable service -- only started/stopped.
    }
}

package com.gamerschat.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

// TEMPORARY DIAGNOSTIC ACTIVITY. Serves two purposes:
// 1. As the launcher for this test build, it deliberately does
//    nothing except display a simple message -- no TWA library, no
//    network calls, nothing that could fail. If this screen appears
//    when you tap the app icon, the basic app/build/signing is fine,
//    and the problem is specifically inside the TWA launch flow.
// 2. If GamersChatApplication's crash handler catches an uncaught
//    exception anywhere else in the app, it also routes here with
//    the full error text, so this same screen can show that instead.
public class MinimalTestActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String crashDetails = getIntent().getStringExtra("crash_details");

        TextView textView = new TextView(this);
        if (crashDetails != null) {
            textView.setText("VOXX CHAT CRASHED\n\nHere is the exact error:\n\n" + crashDetails);
        } else {
            textView.setText("If you can see this text, the basic app launch works fine.\n\nThe problem is specifically in the TWA (website-loading) part, not the app itself.");
        }
        textView.setTextColor(Color.WHITE);
        textView.setBackgroundColor(Color.parseColor("#0a0a0a"));
        textView.setTextIsSelectable(true);
        textView.setTextSize(14);
        textView.setPadding(48, 96, 48, 48);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(textView);
        scrollView.setBackgroundColor(Color.parseColor("#0a0a0a"));

        setContentView(scrollView);
    }
}



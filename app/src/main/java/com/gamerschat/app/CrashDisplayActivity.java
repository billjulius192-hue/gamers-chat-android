package com.gamerschat.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

// Deliberately as simple as possible: this activity has exactly one
// job, show whatever crash text it's given, in a way that's easy to
// screenshot and read. No dependencies on the TWA library or anything
// that could itself fail.
public class CrashDisplayActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String crashDetails = getIntent().getStringExtra("crash_details");
        if (crashDetails == null) {
            crashDetails = "No crash details were captured.";
        }

        TextView textView = new TextView(this);
        textView.setText("VOXX CHAT CRASHED\n\nHere is the exact error:\n\n" + crashDetails);
        textView.setTextColor(Color.WHITE);
        textView.setBackgroundColor(Color.BLACK);
        textView.setTextIsSelectable(true);
        textView.setPadding(32, 64, 32, 32);
        textView.setTextSize(12);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(textView);
        scrollView.setBackgroundColor(Color.BLACK);

        setContentView(scrollView);
    }
}

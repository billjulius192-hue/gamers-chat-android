package com.gamerschat.app;

import android.app.Application;
import android.content.Intent;
import java.io.PrintWriter;
import java.io.StringWriter;

// This replaces Android's default "app just disappears" behavior with
// something we can actually see: any uncaught crash, anywhere in the
// app, gets captured here and handed to MinimalTestActivity, which
// shows the full error text directly on screen. This is a temporary
// diagnostic tool -- once we know the real cause, this can be removed.
public class GamersChatApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                String fullError = sw.toString();

                Intent intent = new Intent(getApplicationContext(), MinimalTestActivity.class);
                intent.putExtra("crash_details", fullError);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getApplicationContext().startActivity(intent);
            } catch (Throwable ignored) {
                // If even the crash handler fails, fall through to the
                // default handler below rather than leave the app in
                // an undefined state.
            }

            // Give the crash-display activity a moment to actually
            // launch before Android tears down the crashing process.
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                System.exit(1);
            }
        });
    }
}

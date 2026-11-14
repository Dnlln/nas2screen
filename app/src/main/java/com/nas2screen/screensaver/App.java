package com.nas2screen.screensaver;

import android.app.Application;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Diag.init(this);
        Prefs.get(this);
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Diag.e("Crash", "Uncaught in " + thread.getName(), throwable);
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }
}

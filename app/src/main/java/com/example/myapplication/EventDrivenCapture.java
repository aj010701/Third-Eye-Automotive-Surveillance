package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class EventDrivenCapture extends Service {
    private static final String TAG = "CaptureService";
    private VibrationSensorManager vibrationSensorManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "EventDrivenCapture onCreate");
        vibrationSensorManager = new VibrationSensorManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "CaptureService onStartCommand");
        vibrationSensorManager.register();

        // Return START_STICKY to ensure the service restarts if it gets terminated
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "CaptureService onDestroy");

        vibrationSensorManager.unregister();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void startService(Context context) {
        Intent intent = new Intent(context, EventDrivenCapture.class);
        context.startService(intent);
    }

    public static void stopService(Context context) {
        Intent intent = new Intent(context, EventDrivenCapture.class);
        context.stopService(intent);
    }
}

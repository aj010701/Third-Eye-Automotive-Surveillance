package com.example.myapplication;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.example.myapplication.helpers.CameraHelper;


public class VibrationSensorManager implements SensorEventListener {

    private final SensorManager sensorManager;
    private Sensor accelerometer;
    private final Context context;
    private static final float VIBRATION_THRESHOLD = 2.5f;
    private final CameraHelper cameraHelper;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable captureImageRunnable = new Runnable() {
        @Override
        public void run() {
            cameraHelper.captureImage();
            handler.postDelayed(this, 2000); // Capture every 2 seconds
        }
    };
    private boolean isListenerPaused = false;
    private final long PAUSE_DURATION_MS = 60000;

    public VibrationSensorManager(Context context) {
        this.context = context;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        cameraHelper = new CameraHelper(context, null, null);
        cameraHelper.startCamera();
    }

    public void register() {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    public void unregister() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (cameraHelper != null) {
            cameraHelper.stopCamera();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isListenerPaused && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            // Calculate the magnitude of the acceleration vector
            double magnitude = Math.abs(Math.sqrt(x * x + y * y + z * z) - 9.8);
            if (magnitude > VIBRATION_THRESHOLD) {
                onVibrationDetected();
            }
        }
    }

    private void pauseListener() {
        isListenerPaused = true;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                isListenerPaused = false;
            }
        }, PAUSE_DURATION_MS);
        sensorManager.unregisterListener(this);
    }

    private void resumeListener() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }


    private void onVibrationDetected() {
        pauseListener();
        Toast.makeText(context, "Vibration detected!", Toast.LENGTH_SHORT).show();
        startCapturingImages();
    }

    private void startCapturingImages() {
        handler.postDelayed(captureImageRunnable, 0); // Start capturing images immediately
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                handler.removeCallbacks(captureImageRunnable); // Stop capturing after 1 minute
                resumeListener();
            }
        }, PAUSE_DURATION_MS);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}

package com.example.womensafety;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.widget.Toast;

public class ShakeDetector implements SensorEventListener {

    private static final float SHAKE_THRESHOLD_GRAVITY = 3.0F; // Threshold for shake detection
    private static final int SHAKE_TIME_MS = 5000; // Cooldown period between shakes

    private final Context context;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private long lastShakeTime;
    private boolean isActive = false;

    private final String username;

    public ShakeDetector(Context context, String username) {
        this.context = context;
        this.username = username;

        // Initialize SensorManager and Accelerometer
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer == null) {
                Toast.makeText(context, "Accelerometer sensor not available!", Toast.LENGTH_SHORT).show();
                Log.e("ShakeDetector", "Accelerometer sensor not found.");
            }
        } else {
            accelerometer = null;
            Toast.makeText(context, "Sensor service not available!", Toast.LENGTH_SHORT).show();
            Log.e("ShakeDetector", "SensorManager is null.");
        }
    }

    public void start() {
        if (accelerometer != null && !isActive) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            isActive = true;
            Log.d("ShakeDetector", "Shake detection started for user: " + username);
        } else {
            Log.e("ShakeDetector", "Cannot start shake detection: Accelerometer is null or already active.");
        }
    }

    public void stop() {
        if (isActive) {
            sensorManager.unregisterListener(this);
            isActive = false;
            Log.d("ShakeDetector", "Shake detection stopped for user: " + username);
        }
    }

    public boolean isActive() {
        return isActive;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float gX = x / SensorManager.GRAVITY_EARTH;
            float gY = y / SensorManager.GRAVITY_EARTH;
            float gZ = z / SensorManager.GRAVITY_EARTH;


            float gForce = (float) Math.sqrt(gX * gX + gY * gY + gZ * gZ);

            if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                long now = System.currentTimeMillis();
                if (lastShakeTime + SHAKE_TIME_MS > now) {
                    return;
                }
                lastShakeTime = now;

                Log.d("ShakeDetector", "Shake detected for user: " + username);


                Intent intent = new Intent(context, SosActivity.class);
                intent.putExtra("username", username);
                context.startActivity(intent);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d("ShakeDetector", "Sensor accuracy changed: " + accuracy);
    }
}
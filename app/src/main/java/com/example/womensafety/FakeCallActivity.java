package com.example.womensafety;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class FakeCallActivity extends AppCompatActivity {

    private TextView tvCallerName, tvCallStatus;
    private ImageView  ivAccept, ivReject;
    private Ringtone ringtone;
    private MediaPlayer mediaPlayer;
    private Handler handler;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fake_call);

        // Initialize views
        tvCallerName = findViewById(R.id.tvCallerName);
        tvCallStatus = findViewById(R.id.tvCallStatus);
        ivAccept = findViewById(R.id.ivAccept);
        ivReject = findViewById(R.id.ivReject);


        String callerName = getSharedPreferences("fake_call_prefs", MODE_PRIVATE)
                .getString("caller_name", "Mom");
        tvCallerName.setText(callerName);


        startRinging();


        setupCallActions();
    }

    private void startRinging() {
        try {
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(getApplicationContext(), ringtoneUri);

            if (ringtone != null) {
                ringtone.play();
            }

            // Vibrate phone for realism
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(new long[]{0, 1000, 500}, 0); // Vibrate pattern
                } else {
                    vibrator.vibrate(1000); // Vibrate for 1 second
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupCallActions() {

        ivAccept.setOnClickListener(v -> {
            stopRinging();

            tvCallStatus.setText("On Call");
            ivAccept.setVisibility(View.GONE);
            ivReject.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);

            playPrerecordedAudio();

            handler = new Handler();
            handler.postDelayed(this::endCall, 30000);
        });


        ivReject.setOnClickListener(v -> endCall());
    }

    private void playPrerecordedAudio() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.call);

            if (mediaPlayer != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mediaPlayer.setAudioAttributes(
                            new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                    );
                } else {
                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
                }

                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.release();
                    mediaPlayer = null;
                });
                mediaPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopRinging() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }

        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.cancel();
            }
        }
    }

    private void endCall() {
        stopRinging();

        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        endCall();
    }
}

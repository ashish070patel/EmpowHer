package com.example.womensafety;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class SosActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int SMS_PERMISSION_REQUEST_CODE = 1002;
    private static final int CALL_PERMISSION_REQUEST_CODE = 1003;

    private TextView tvCountdown;
    private Button btnCancel, btnEmergencyCall;
    private FusedLocationProviderClient fusedLocationClient;
    private CountDownTimer countDownTimer;
    private boolean isCountdownActive = false;
    private Location currentLocation;

    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sos);

        Intent intent = getIntent();
        username = intent.getStringExtra("username");

        if (username == null) {
            Toast.makeText(this, "User data not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);


        initViews();

        setupUI();


        checkPermissions();

        startCountdown();
    }

    private void initViews() {
        tvCountdown = findViewById(R.id.tvCountdown);
        btnCancel = findViewById(R.id.btnCancel);
        btnEmergencyCall = findViewById(R.id.btnEmergencyCall);
    }

    private void setupUI() {
        btnCancel.setOnClickListener(v -> {
            cancelSos();
            finish();
        });

        btnEmergencyCall.setOnClickListener(v -> {
            makeEmergencyCall();
        });
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getCurrentLocation();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS},
                    SMS_PERMISSION_REQUEST_CODE);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CALL_PHONE},
                    CALL_PERMISSION_REQUEST_CODE);
        }
    }

    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    currentLocation = location;
                } else {
                    Log.d("Location", "Location is null");
                }
            });
        } else {
            Log.d("Location", "Location permission not granted");
        }
    }

    private void startCountdown() {
        isCountdownActive = true;

        countDownTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsRemaining = (int) (millisUntilFinished / 1000);
                tvCountdown.setText(String.valueOf(secondsRemaining));
            }

            @Override
            public void onFinish() {
                isCountdownActive = false;
                tvCountdown.setText("0");
                sendSosAlerts();
            }
        }.start();
    }

    private void cancelSos() {
        if (isCountdownActive && countDownTimer != null) {
            countDownTimer.cancel();
            isCountdownActive = false;
        }
    }

    private void sendSosAlerts() {
        DatabaseReference trustedContactsRef = FirebaseDatabase.getInstance().getReference()
                .child("users")
                .child(username)
                .child("closeones");

        trustedContactsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    List<Contact> trustedContacts = new ArrayList<>();

                    for (DataSnapshot contactSnapshot : snapshot.getChildren()) {
                        String name = contactSnapshot.child("name").getValue(String.class);
                        String phone = contactSnapshot.child("phone").getValue(String.class);

                        if (name != null && phone != null) {
                            trustedContacts.add(new Contact(name, phone));
                        }
                    }

                    if (trustedContacts.isEmpty()) {
                        Toast.makeText(SosActivity.this, "No trusted contacts found. Add contacts in profile.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        sendSmsToContacts(trustedContacts);
                    }
                } else {
                    Toast.makeText(SosActivity.this, "No trusted contacts found. Add contacts in profile.",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SosActivity.this, "Failed to load trusted contacts",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendSmsToContacts(List<Contact> trustedContacts) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            SmsManager smsManager = SmsManager.getDefault();
            String message = "🚨 EMERGENCY SOS ALERT! I need help!";
            if (currentLocation != null) {
                message += "\n📍 My location: https://maps.google.com/?q=" +
                        currentLocation.getLatitude() + "," + currentLocation.getLongitude();
            } else {
                message += "\n📍 Location unavailable";
            }
            for (Contact contact : trustedContacts) {
                try {
                    smsManager.sendTextMessage(
                            contact.getPhoneNumber(),
                            null,
                            message,
                            null,
                            null);
                    Log.d("SMS Sent", "Message sent to: " + contact.getPhoneNumber());
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("SMS Failed", "Failed to send SMS to: " + contact.getPhoneNumber(), e);
                    Toast.makeText(this, "Failed to send SMS to " + contact.getPhoneNumber(),
                            Toast.LENGTH_SHORT).show();
                }
            }

            Toast.makeText(this, "✅ SOS alerts sent to " + trustedContacts.size() + " contacts",
                    Toast.LENGTH_SHORT).show();
        } else {
            Log.d("SMS Permission", "SMS permission not granted");
            Toast.makeText(this, "❌ SMS permission required to send SOS alerts",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void makeEmergencyCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:112"));

            try {
                startActivity(callIntent);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "❌ Could not make emergency call", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.d("Call Permission", "Call permission not granted");
            Toast.makeText(this, "❌ Call permission required for emergency calls",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            }
        } else if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                Toast.makeText(this, "❌ SMS permission is required for SOS alerts",
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == CALL_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                makeEmergencyCall();
            } else {
                Toast.makeText(this, "❌ Call permission is required for emergency calls",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        super.onDestroy();
    }
    public static class Contact {
        private String name;
        private String phoneNumber;

        public Contact(String name, String phoneNumber) {
            this.name = name;
            this.phoneNumber = phoneNumber;
        }

        public String getName() {
            return name;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }
    }
}
package com.example.womensafety;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
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

    // Permission request codes
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int SMS_PERMISSION_REQUEST_CODE = 1002;
    private static final int CALL_PERMISSION_REQUEST_CODE = 1003;
    private static final int SMS_SENT_REQUEST_CODE = 1004;
    private static final int SMS_DELIVERED_REQUEST_CODE = 1005;

    // UI Components
    private TextView tvCountdown;
    private Button btnCancel, btnEmergencyCall;

    // Location services
    private FusedLocationProviderClient fusedLocationClient;
    private Location currentLocation;

    // SMS and timing
    private CountDownTimer countDownTimer;
    private boolean isCountdownActive = false;
    private String username;

    // Broadcast receivers for SMS delivery tracking
    private BroadcastReceiver smsSentReceiver, smsDeliveredReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sos);

        // Initialize UI components
        initViews();
        setupUI();

        // Get username from intent
        Intent intent = getIntent();
        username = intent.getStringExtra("username");
        if (username == null) {
            Toast.makeText(this, "User data not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Register SMS delivery receivers
        registerSmsReceivers();

        // Check permissions and start countdown
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

        btnEmergencyCall.setOnClickListener(v -> makeEmergencyCall());
    }

    private void checkPermissions() {
        // Location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getCurrentLocation();
        }

        // SMS permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS},
                    SMS_PERMISSION_REQUEST_CODE);
        }

        // Call permission
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
                    Log.d("Location", "Location obtained: " + location.getLatitude() + "," + location.getLongitude());
                } else {
                    Log.d("Location", "Last location is null");
                }
            });
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
                        showToast("No trusted contacts found. Add contacts in profile.");
                    } else {
                        sendSmsToContacts(trustedContacts);
                    }
                } else {
                    showToast("No trusted contacts found. Add contacts in profile.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showToast("Failed to load trusted contacts");
                Log.e("Firebase", "Error loading contacts", error.toException());
            }
        });
    }

    private void sendSmsToContacts(List<Contact> trustedContacts) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            showToast("SMS permission required");
            return;
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            showToast("Device doesn't support SMS");
            return;
        }

        String message = buildSosMessage();
        int successfulSends = 0;

        for (Contact contact : trustedContacts) {
            try {
                String phoneNumber = contact.getPhoneNumber();

                // Validate phone number
                if (!PhoneNumberUtils.isGlobalPhoneNumber(phoneNumber)) {
                    Log.e("SMS", "Invalid number: " + phoneNumber);
                    continue;
                }

                // Get appropriate SmsManager (handles dual SIM)
                SmsManager smsManager = getSmsManager();

                // Create intents for tracking delivery
                PendingIntent sentIntent = PendingIntent.getBroadcast(this,
                        SMS_SENT_REQUEST_CODE,
                        new Intent("SMS_SENT_" + phoneNumber),
                        PendingIntent.FLAG_IMMUTABLE);

                PendingIntent deliveredIntent = PendingIntent.getBroadcast(this,
                        SMS_DELIVERED_REQUEST_CODE,
                        new Intent("SMS_DELIVERED_" + phoneNumber),
                        PendingIntent.FLAG_IMMUTABLE);

                // Send SMS
                ArrayList<String> parts = smsManager.divideMessage(message);
                if (parts.size() > 1) {
                    smsManager.sendMultipartTextMessage(
                            phoneNumber,
                            null,
                            parts,
                            null,
                            null);
                } else {
                    smsManager.sendTextMessage(
                            phoneNumber,
                            null,
                            message,
                            sentIntent,
                            deliveredIntent);
                }

                successfulSends++;
                Log.d("SMS", "Message queued for: " + phoneNumber);

            } catch (Exception e) {
                Log.e("SMS", "Failed to send to " + contact.getPhoneNumber(), e);
                runOnUiThread(() -> showToast("Failed to send to " + contact.getName()));
            }
        }

        // Show summary
        if (successfulSends > 0) {
            showToast("SOS alerts sent to " + successfulSends + "/" + trustedContacts.size() + " contacts");
        } else {
            showToast("Failed to send to all contacts");
            // Fallback to SMS intent if no messages sent
            sendViaSmsIntent(trustedContacts);
        }
    }

    private SmsManager getSmsManager() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            int subscriptionId = getDefaultSmsSubscriptionId();
            if (subscriptionId != -1) {
                return SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
            }
        }
        return SmsManager.getDefault();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    private int getDefaultSmsSubscriptionId() {
        SubscriptionManager subManager = (SubscriptionManager)
                getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        if (subManager != null) {
            return subManager.getDefaultSmsSubscriptionId();
        }
        return -1;
    }

    private String buildSosMessage() {
        String message = "🚨 EMERGENCY SOS ALERT! I need help!";
        if (currentLocation != null) {
            message += "\n📍 My location: https://maps.google.com/?q=" +
                    currentLocation.getLatitude() + "," + currentLocation.getLongitude();
        } else {
            message += "\n📍 Location unavailable";
        }
        return message;
    }

    private void sendViaSmsIntent(List<Contact> contacts) {
        String message = buildSosMessage();
        for (Contact contact : contacts) {
            try {
                Intent smsIntent = new Intent(Intent.ACTION_VIEW);
                smsIntent.setData(Uri.parse("smsto:" + contact.getPhoneNumber()));
                smsIntent.putExtra("sms_body", message);
                startActivity(smsIntent);
            } catch (Exception e) {
                Log.e("SMS", "Failed to launch SMS intent for " + contact.getPhoneNumber(), e);
            }
        }
    }

    private void makeEmergencyCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            showToast("Call permission required");
            return;
        }

        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:112"));

        try {
            startActivity(callIntent);
        } catch (Exception e) {
            Log.e("Call", "Emergency call failed", e);
            showToast("Could not make emergency call");
        }
    }

    private void registerSmsReceivers() {
        // Receiver for sent status
        smsSentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String phoneNumber = intent.getAction().replace("SMS_SENT_", "");
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        Log.d("SMS", "SMS sent to " + phoneNumber);
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        Log.e("SMS", "Generic failure for " + phoneNumber);
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        Log.e("SMS", "No service for " + phoneNumber);
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        Log.e("SMS", "Null PDU for " + phoneNumber);
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        Log.e("SMS", "Radio off for " + phoneNumber);
                        break;
                }
            }
        };

        // Receiver for delivery status
        smsDeliveredReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String phoneNumber = intent.getAction().replace("SMS_DELIVERED_", "");
                Log.d("SMS", "SMS delivered to " + phoneNumber + ": " +
                        (getResultCode() == Activity.RESULT_OK));
            }
        };

        // Register the receivers
        registerReceiver(smsSentReceiver, new IntentFilter("SMS_SENT_.*"));
        registerReceiver(smsDeliveredReceiver, new IntentFilter("SMS_DELIVERED_.*"));
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(SosActivity.this, message, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length == 0) return;

        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getCurrentLocation();
                }
                break;
            case SMS_PERMISSION_REQUEST_CODE:
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    showToast("SMS permission is required for SOS alerts");
                }
                break;
            case CALL_PERMISSION_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    makeEmergencyCall();
                } else {
                    showToast("Call permission is required for emergency calls");
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        // Clean up receivers
        if (smsSentReceiver != null) unregisterReceiver(smsSentReceiver);
        if (smsDeliveredReceiver != null) unregisterReceiver(smsDeliveredReceiver);

        // Cancel countdown
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        super.onDestroy();
    }

    public static class Contact {
        private final String name;
        private final String phoneNumber;

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
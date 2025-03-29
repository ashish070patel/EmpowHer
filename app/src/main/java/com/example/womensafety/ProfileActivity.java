package com.example.womensafety;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.location.LocationManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private static final String POLICE_STATION_QUERY = "police station";
    private static final String HOSPITAL_QUERY = "hospital";

    private static final int LOCATION_UPDATE_INTERVAL_MS = 10000;
    private static final int FASTEST_LOCATION_UPDATE_MS = 5000;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int LOCATION_SETTINGS_REQUEST_CODE = 1002;
    private static final int SMS_PERMISSION_REQUEST_CODE = 1003;
    private static final int DEFAULT_SMS_APP_REQUEST_CODE = 1004;


    private static final String SMS_SENT = "SMS_SENT";
    private static final String SMS_DELIVERED = "SMS_DELIVERED";
    private static final int LOCATION_TIMEOUT_MS = 30000;
    private static final int MAX_SMS_LENGTH = 160;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private FloatingActionButton sosButton;
    private BottomNavigationView bottomNav;
    private TextView tvUserName;
    private MaterialButton btnAddFriends;
    private MaterialCardView btnFakeCall, btnShareLocation;
    private DatabaseReference userRef;
    private AlertDialog progressDialog;
    private Handler locationTimeoutHandler;

    private String userName, userEmail, userUsername;
    private ShakeDetector shakeDetector;
    private MaterialCardView btnPoliceStation, btnHospital;

    private String pendingPlaceSearch = null;

    private int smsSentCount = 0;
    private int smsDeliveredCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        locationTimeoutHandler = new Handler(Looper.getMainLooper());


        Intent intent = getIntent();
        userName = intent.getStringExtra("name");
        userEmail = intent.getStringExtra("email");
        userUsername = intent.getStringExtra("username");

        if (userName == null || userEmail == null || userUsername == null) {
            showErrorAndFinish("User data not found!");
            return;
        }


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize ShakeDetector
        initializeShakeDetector();

        // Initialize Firebase
        initializeFirebase();

        // Initialize UI components
        initializeViews();

        // Set up event listeners
        setupEventListeners();

        // Register SMS delivery receivers
        registerSmsReceivers();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerSmsReceivers() {
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode()) {
                    case RESULT_OK:
                        smsSentCount++;
                        Log.d("SMS", "SMS sent successfully");
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        Log.e("SMS", "Generic failure");
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        Log.e("SMS", "No service");
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        Log.e("SMS", "Null PDU");
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        Log.e("SMS", "Radio off");
                        break;
                }
            }
        }, new IntentFilter(SMS_SENT));

        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode()) {
                    case RESULT_OK:
                        smsDeliveredCount++;
                        Log.d("SMS", "SMS delivered");
                        break;
                    case RESULT_CANCELED:
                        Log.e("SMS", "SMS not delivered");
                        break;
                }
            }
        }, new IntentFilter(SMS_DELIVERED));
    }

    private void showErrorAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void initializeShakeDetector() {
        try {
            shakeDetector = new ShakeDetector(this, userUsername);
            shakeDetector.start();
        } catch (Exception e) {
            Log.e("ProfileActivity", "Error initializing shake detector", e);
        }
    }

    private void initializeFirebase() {
        try {
            userRef = FirebaseDatabase.getInstance().getReference("users").child(userUsername);
        } catch (Exception e) {
            Log.e("ProfileActivity", "Firebase initialization failed", e);
            Toast.makeText(this, "Failed to initialize database", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeViews() {
        sosButton = findViewById(R.id.sos_button);
        bottomNav = findViewById(R.id.bottom_navigation);

        tvUserName = findViewById(R.id.tvUserName);
        btnAddFriends = findViewById(R.id.btnAddFriends);
        btnFakeCall = findViewById(R.id.cardFakeCall);
        btnShareLocation = findViewById(R.id.cardShareLocation);
        btnPoliceStation = findViewById(R.id.cardPoliceStation);
        btnHospital = findViewById(R.id.cardHospital);
        tvUserName.setText(userName);
    }

    private void setupEventListeners() {

        btnAddFriends.setOnClickListener(v -> openAddPersonDialog());

        btnFakeCall.setOnClickListener(v -> {
            Toast.makeText(this, "Calling Mom", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(ProfileActivity.this, FakeCallActivity.class));
        });


        btnShareLocation.setOnClickListener(v -> checkLocationPermissionAndShare());

        // SOS Button Click
        sosButton.setOnClickListener(v -> activateSOS());

        btnPoliceStation.setOnClickListener(v -> {
            checkLocationPermissionAndFindPlaces(POLICE_STATION_QUERY);
        });


        btnHospital.setOnClickListener(v -> {
            checkLocationPermissionAndFindPlaces(HOSPITAL_QUERY);
        });

        // Bottom Navigation
        bottomNav.setOnNavigationItemSelectedListener(item -> {  //How this is depre..
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_home) {
                return true;
            }else if (itemId == R.id.navigation_profile) {
                startActivity(new Intent(ProfileActivity.this, EditProfileActivity.class));
                return true;
            }

            return false;
        });
    }

    private void activateSOS() {
        Toast.makeText(this, "SOS Activated!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(ProfileActivity.this, SosActivity.class);
        intent.putExtra("username", userUsername);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupResources();
    }

    private void cleanupResources() {
        if (shakeDetector != null) {
            shakeDetector.stop();
        }

        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        if (locationTimeoutHandler != null) {
            locationTimeoutHandler.removeCallbacksAndMessages(null);
        }

        dismissProgressDialog();

        try {
            unregisterReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {}
            });
            unregisterReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {}
            });
        } catch (IllegalArgumentException e) {
            Log.e("ProfileActivity", "Receiver not registered", e);
        }
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void showProgressDialog(String message) {
        dismissProgressDialog(); // Dismiss any existing dialog
        progressDialog = new AlertDialog.Builder(this)
                .setMessage(message)
                .setCancelable(false)
                .show();
    }

    private void shareLocationWithContacts(@Nullable Location location) {
        dismissProgressDialog();

        if (location == null) {
            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show();
            return;
        }

        double latitude = location.getLatitude();
        double longitude = location.getLongitude();


        String locationMessage = formatLocationMessage(latitude, longitude);

        fetchContactsAndSendLocation(locationMessage, latitude, longitude);
    }

    private String formatLocationMessage(double lat, double lng) {

        String googleMapsUrl = String.format(Locale.US,
                "https://maps.google.com/?q=%.6f,%.6f",
                lat, lng);

        if (isGoogleMessagesDefault() && isMessageTooLong(googleMapsUrl)) {
            return String.format(Locale.US,
                    "Emergency Location:\n" +
                            "Coordinates: %.6f, %.6f\n" +
                            "Open in Maps: (paste together)\n" +
                            "maps.google.com/?q=%.6f,%.6f",
                    lat, lng, lat, lng);
        }

        return String.format(Locale.US,
                "Emergency Location:\n" +
                        "Coordinates: %.6f, %.6f\n" +
                        "Google Maps: %s",
                lat, lng, googleMapsUrl);
    }

    private boolean isGoogleMessagesDefault() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            String defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(this);
            return defaultSmsApp != null &&
                    defaultSmsApp.equals("com.google.android.apps.messaging");
        }
        return false;
    }

    private void fetchContactsAndSendLocation(String message, double latitude, double longitude) {
        if (userRef == null) {
            Toast.makeText(this, "Database not available", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgressDialog("Preparing to share location...");

        userRef.child("closeones").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                dismissProgressDialog();
                List<Contact> contacts = new ArrayList<>();

                for (DataSnapshot contactSnapshot : snapshot.getChildren()) {
                    String id = contactSnapshot.getKey();
                    String name = contactSnapshot.child("name").getValue(String.class);
                    String phone = contactSnapshot.child("phone").getValue(String.class);

                    if (id != null && name != null && phone != null) {
                        contacts.add(new Contact(id, name, phone));
                    }
                }

                if (contacts.isEmpty()) {
                    Toast.makeText(ProfileActivity.this,
                            "No contacts found to share with",
                            Toast.LENGTH_SHORT).show();
                } else {
                    sendLocationToContacts(contacts, message, latitude, longitude);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                dismissProgressDialog();
                Toast.makeText(ProfileActivity.this,
                        "Failed to load contacts",
                        Toast.LENGTH_SHORT).show();
                Log.e("Firebase", "Error loading contacts", error.toException());
            }
        });
    }

    private void sendLocationToContacts(List<Contact> contacts, String message,
                                        double latitude, double longitude) {
        if (!hasSmsPermission()) {
            requestSMSPermission();
            return;
        }

        int successfulSends = 0;
        int failedSends = 0;
        List<String> failedNumbers = new ArrayList<>();

        for (Contact contact : contacts) {
            String phoneNumber = validatePhoneNumber(contact.getPhoneNumber());
            if (phoneNumber == null) {
                failedSends++;
                failedNumbers.add(contact.getPhoneNumber());
                continue;
            }

            if (sendSmsWithWorkarounds(phoneNumber, message)) {
                successfulSends++;
                Log.d("SMS", "Successfully sent to: " + phoneNumber);
            } else {
                failedSends++;
                failedNumbers.add(phoneNumber);
                Log.e("SMS", "Failed to send to: " + phoneNumber);
            }
        }


        saveLocationToFirebase(latitude, longitude);


        showSmsResult(successfulSends, failedSends, failedNumbers);
    }

    private boolean sendSmsWithWorkarounds(String phoneNumber, String message) {

        return tryStandardSms(phoneNumber, message) ||
                tryAlternativeSmsManager(phoneNumber, message) ||
                trySmsIntent(phoneNumber, message);
    }

    private boolean tryStandardSms(String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
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
                        null,
                        null);
            }
            return true;
        } catch (Exception e) {
            Log.e("SMS", "Standard SMS failed", e);
            return false;
        }
    }

    private boolean tryAlternativeSmsManager(String phoneNumber, String message) {
        try {
            // Try with specific subscription ID
            SmsManager smsManager = SmsManager.getSmsManagerForSubscriptionId(
                    getDefaultSmsSubscriptionId());

            smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    message,
                    null,
                    null);
            return true;
        } catch (Exception e) {
            Log.e("SMS", "Alternative SMS manager failed", e);
            return false;
        }
    }

    private boolean trySmsIntent(String phoneNumber, String message) {
        try {
            Intent smsIntent = new Intent(Intent.ACTION_VIEW);
            smsIntent.setData(Uri.parse("smsto:" + phoneNumber));
            smsIntent.putExtra("sms_body", message);

            if (isGoogleMessagesDefault()) {
                Intent chooser = Intent.createChooser(smsIntent, "Send SMS via");
                List<ResolveInfo> resInfo = getPackageManager().queryIntentActivities(smsIntent, 0);
                for (ResolveInfo resolveInfo : resInfo) {
                    if (resolveInfo.activityInfo.packageName.equals("com.google.android.apps.messaging")) {
                        chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,
                                new ComponentName[] {
                                        new ComponentName(resolveInfo.activityInfo.packageName,
                                                resolveInfo.activityInfo.name)
                                });
                        break;
                    }
                }
                startActivity(chooser);
            } else {
                startActivity(smsIntent);
            }
            return true;
        } catch (Exception e) {
            Log.e("SMS", "SMS intent failed", e);
            return false;
        }
    }

    private int getDefaultSmsSubscriptionId() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            SubscriptionManager subManager = (SubscriptionManager)
                    getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (subManager != null) {
                return subManager.getDefaultSmsSubscriptionId();
            }
        }
        return -1;
    }

    private String validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;
        String digitsOnly = phoneNumber.replaceAll("[^0-9+]", "");
        if (digitsOnly.length() < 6) {
            Log.e("SMS", "Invalid phone number: " + phoneNumber);
            return null;
        }
        if (!digitsOnly.startsWith("+") && digitsOnly.length() == 10) {
            digitsOnly = "+91" + digitsOnly;
        }

        return digitsOnly;
    }

    private void showSmsResult(int successfulSends, int failedSends, List<String> failedNumbers) {
        String resultMsg = "Location shared with " + successfulSends + " contacts";
        if (failedSends > 0) {
            resultMsg += "\nFailed to send to " + failedSends + " contacts";
            if (!failedNumbers.isEmpty()) {
                resultMsg += "\nFailed numbers: " + failedNumbers.toString();
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("SMS Result")
                .setMessage(resultMsg)
                .setPositiveButton("OK", null)
                .show();
    }

    private boolean hasSmsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSMSPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.SEND_SMS)) {
            new AlertDialog.Builder(this)
                    .setTitle("SMS Permission Needed")
                    .setMessage("This app needs SMS permission to send your location to emergency contacts")
                    .setPositiveButton("OK", (dialog, which) -> ActivityCompat.requestPermissions(
                            this,
                            new String[]{Manifest.permission.SEND_SMS},
                            SMS_PERMISSION_REQUEST_CODE))
                    .setNegativeButton("Cancel", null)
                    .create()
                    .show();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.SEND_SMS},
                    SMS_PERMISSION_REQUEST_CODE);
        }
    }

    private boolean isMessageTooLong(String message) {
        try {
            return message.getBytes("UTF-8").length > MAX_SMS_LENGTH;
        } catch (UnsupportedEncodingException e) {
            return message.length() > MAX_SMS_LENGTH;
        }
    }

    private void saveLocationToFirebase(double latitude, double longitude) {
        if (userRef == null) {
            Log.e("Firebase", "Database reference is null");
            return;
        }

        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", latitude);
        locationData.put("longitude", longitude);
        locationData.put("timestamp", System.currentTimeMillis());

        userRef.child("lastLocation").setValue(locationData)
                .addOnSuccessListener(aVoid -> Log.d("Firebase", "Location saved to Firebase"))
                .addOnFailureListener(e -> Log.e("Firebase", "Failed to save location", e));
    }

    private void openAddPersonDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Emergency Contact");

        View view = getLayoutInflater().inflate(R.layout.activity_add_friends, null);
        EditText etName = view.findViewById(R.id.etPersonName);
        EditText etPhone = view.findViewById(R.id.etPersonPhone);
        builder.setView(view);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();

            if (!name.isEmpty() && !phone.isEmpty()) {
                addCloseOneToFirebase(name, phone);
            } else {
                Toast.makeText(this, "Both fields are required!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void addCloseOneToFirebase(String name, String phone) {
        if (userRef == null) {
            Toast.makeText(this, "Database not available", Toast.LENGTH_SHORT).show();
            return;
        }

        String closeOneId = userRef.child("closeones").push().getKey();
        if (closeOneId == null) {
            Toast.makeText(this, "Failed to generate ID", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, String> closeOneData = new HashMap<>();
        closeOneData.put("name", name);
        closeOneData.put("phone", phone);

        userRef.child("closeones").child(closeOneId).setValue(closeOneData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Contact added successfully!", Toast.LENGTH_SHORT).show();
                    Log.d("Firebase", "Contact added: " + name + ", " + phone);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to add contact", Toast.LENGTH_SHORT).show();
                    Log.e("Firebase", "Error adding contact", e);
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length == 0) {
            return;
        }

        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (pendingPlaceSearch != null) {
                        findNearbyPlaces(pendingPlaceSearch);
                        pendingPlaceSearch = null;
                    } else {
                        shareCurrentLocation();
                    }
                } else {
                    dismissProgressDialog();
                    Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show();
                }
                break;
            case SMS_PERMISSION_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Retry location sharing since we now have SMS permission
                    shareCurrentLocation();
                } else {
                    Toast.makeText(this, "SMS permission required to share location",
                            Toast.LENGTH_LONG).show();
                }
                break;
            case DEFAULT_SMS_APP_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Please try sending again", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOCATION_SETTINGS_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                shareCurrentLocation();
            } else {
                dismissProgressDialog();
                Toast.makeText(this, "Location services must be enabled to share location",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void shareCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        showProgressDialog("Getting your location...");
        locationTimeoutHandler.postDelayed(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                dismissProgressDialog();
                Toast.makeText(this, "Location request timed out", Toast.LENGTH_SHORT).show();
                if (fusedLocationClient != null && locationCallback != null) {
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                }
            }
        }, LOCATION_TIMEOUT_MS);
        LocationRequest locationRequest = new LocationRequest.Builder(10000)
                .setMinUpdateIntervalMillis(5000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                locationTimeoutHandler.removeCallbacksAndMessages(null);
                if (locationResult == null) {
                    dismissProgressDialog();
                    Toast.makeText(ProfileActivity.this, "Unable to get location", Toast.LENGTH_SHORT).show();
                    return;
                }

                Location location = locationResult.getLastLocation();
                if (location != null) {
                    fusedLocationClient.removeLocationUpdates(this);
                    shareLocationWithContacts(location);
                }
            }
        };
        checkLocationSettings(locationRequest);
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        locationTimeoutHandler.removeCallbacksAndMessages(null);
                        if (locationCallback != null) {
                            fusedLocationClient.removeLocationUpdates(locationCallback);
                        }
                        shareLocationWithContacts(location);
                    }
                })
                .addOnFailureListener(e -> Log.e("Location", "Error getting last location", e));
    }

    private void checkLocationSettings(LocationRequest locationRequest) {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, response -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            fusedLocationClient.requestLocationUpdates(locationRequest,
                    locationCallback,
                    Looper.getMainLooper());
        });

        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    resolvable.startResolutionForResult(this, LOCATION_SETTINGS_REQUEST_CODE);
                } catch (IntentSender.SendIntentException sendEx) {
                    dismissProgressDialog();
                    Toast.makeText(this, "Error enabling location services", Toast.LENGTH_SHORT).show();
                }
            } else {
                dismissProgressDialog();
                Toast.makeText(this, "Location services must be enabled", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkLocationPermissionAndShare() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            shareCurrentLocation();
        } else {
            requestLocationPermission();
        }
    }

    private void requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            new AlertDialog.Builder(this)
                    .setTitle("Location Permission Needed")
                    .setMessage("This app needs location permission to share your location in emergencies")
                    .setPositiveButton("OK", (dialog, which) -> ActivityCompat.requestPermissions(
                            this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            LOCATION_PERMISSION_REQUEST_CODE))
                    .setNegativeButton("Cancel", null)
                    .create()
                    .show();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }
    private void diagnoseSmsIssues() {
        StringBuilder report = new StringBuilder();

        String defaultSms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ?
                Telephony.Sms.getDefaultSmsPackage(this) : "Unknown";
        report.append("Default SMS app: ").append(defaultSms).append("\n");

        try {
            SmsManager smsManager = SmsManager.getDefault();
            report.append("SMS manager: ").append(smsManager.getClass().getName()).append("\n");
        } catch (Exception e) {
            report.append("SMS manager error: ").append(e.getMessage()).append("\n");
        }

        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (tm != null) {
            report.append("SIM state: ").append(tm.getSimState()).append("\n")
                    .append("Network operator: ").append(tm.getNetworkOperatorName()).append("\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("SMS Diagnostic Report")
                .setMessage(report.toString())
                .setPositiveButton("OK", null)
                .show();

        Log.d("SMS_DIAGNOSTICS", report.toString());
    }


    private void checkLocationPermissionAndFindPlaces(String placeType) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            if (!isLocationEnabled()) {
                showLocationSettingsDialog(placeType);
            } else {
                findNearbyPlaces(placeType);
            }
        } else {
            requestLocationPermissionForPlaces(placeType);
        }
    }

    private void requestLocationPermissionForPlaces(String placeType) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            new AlertDialog.Builder(this)
                    .setTitle("Location Permission Needed")
                    .setMessage("This app needs location permission to find nearby " +
                            (placeType.equals(POLICE_STATION_QUERY) ? "police stations" : "hospitals"))
                    .setPositiveButton("OK", (dialog, which) -> {
                        ActivityCompat.requestPermissions(
                                this,
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                LOCATION_PERMISSION_REQUEST_CODE);
                        pendingPlaceSearch = placeType;
                    })
                    .setNegativeButton("Cancel", null)
                    .create()
                    .show();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            pendingPlaceSearch = placeType;
        }
    }
    private void findNearbyPlaces(String placeType) {
        showProgressDialog("Finding nearby " +
                (placeType.equals(POLICE_STATION_QUERY) ? "police stations..." : "hospitals..."));

        locationTimeoutHandler.postDelayed(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                dismissProgressDialog();
                Toast.makeText(this, "Location request timed out", Toast.LENGTH_SHORT).show();
                if (fusedLocationClient != null && locationCallback != null) {
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                }
            }
        }, LOCATION_TIMEOUT_MS);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            dismissProgressDialog();
            requestLocationPermissionForPlaces(placeType);
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    locationTimeoutHandler.removeCallbacksAndMessages(null);
                    if (location != null) {
                        openMapsWithPlaceSearch(location, placeType);
                    } else {
                        requestFreshLocationUpdates(placeType);
                    }
                })
                .addOnFailureListener(e -> {
                    locationTimeoutHandler.removeCallbacksAndMessages(null);
                    dismissProgressDialog();
                    Log.e("Location", "Error getting last location", e);
                    Toast.makeText(this, "Error getting location. Trying again...", Toast.LENGTH_SHORT).show();
                    requestFreshLocationUpdates(placeType);
                });
    }

    private void openMapsWithPlaceSearch(Location location, String placeType) {
        dismissProgressDialog();

        if (location == null) {
            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String query = URLEncoder.encode(placeType, "UTF-8");
            String uriString = String.format(Locale.US,
                    "geo:%f,%f?q=%s",
                    location.getLatitude(),
                    location.getLongitude(),
                    query);

            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriString));
            mapIntent.setPackage("com.google.android.apps.maps");

            // Verify the intent will resolve to an activity
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                // Fallback to web browser
                String webUrl = String.format(Locale.US,
                        "https://www.google.com/maps/search/%s/@%f,%f,15z",
                        query,
                        location.getLatitude(),
                        location.getLongitude());
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
                startActivity(webIntent);
            }
        } catch (UnsupportedEncodingException e) {
            Log.e("Maps", "Error encoding place type", e);
            Toast.makeText(this, "Error opening maps", Toast.LENGTH_SHORT).show();
        }
    }
    private void requestFreshLocationUpdates(String placeType) {
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                LOCATION_UPDATE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(FASTEST_LOCATION_UPDATE_MS)
                .setWaitForAccurateLocation(true)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                locationTimeoutHandler.removeCallbacksAndMessages(null);
                if (locationResult == null) {
                    dismissProgressDialog();
                    Toast.makeText(ProfileActivity.this,
                            "Unable to get location", Toast.LENGTH_SHORT).show();
                    return;
                }

                Location location = locationResult.getLastLocation();
                if (location != null) {
                    fusedLocationClient.removeLocationUpdates(this);
                    openMapsWithPlaceSearch(location, placeType);
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest,
                    locationCallback, Looper.getMainLooper());
        } else {
            dismissProgressDialog();
            requestLocationPermissionForPlaces(placeType);
        }
    }
    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }
    private void showLocationSettingsDialog(String placeType) {
        new AlertDialog.Builder(this)
                .setTitle("Location Required")
                .setMessage("Please enable location services to find nearby places")
                .setPositiveButton("Enable", (dialog, which) -> {
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    pendingPlaceSearch = placeType;
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

}
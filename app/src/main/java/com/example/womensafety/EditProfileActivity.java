package com.example.womensafety;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";


    EditText editName, editEmail, editUsername, editPassword;
    Button saveButton;

    String nameUser, emailUser, usernameUser, passwordUser;

    DatabaseReference reference;

    ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);


        reference = FirebaseDatabase.getInstance("https://women-safety-52e23-default-rtdb.firebaseio.com/")
                .getReference("users");

        initializeViews();
        showData();
        setupSaveButton();
    }

    private void initializeViews() {
        editName = findViewById(R.id.editName);
        editEmail = findViewById(R.id.editEmail);
        editUsername = findViewById(R.id.editUsername);
        editPassword = findViewById(R.id.editPassword);
        saveButton = findViewById(R.id.saveButton);

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Saving Changes");
        progressDialog.setMessage("Please wait...");
        progressDialog.setCancelable(false);
    }

    private void showData() {
        Intent intent = getIntent();


        nameUser = intent.getStringExtra("name") != null ? intent.getStringExtra("name") : "";
        emailUser = intent.getStringExtra("email") != null ? intent.getStringExtra("email") : "";
        usernameUser = intent.getStringExtra("username") != null ? intent.getStringExtra("username") : "";
        passwordUser = intent.getStringExtra("password") != null ? intent.getStringExtra("password") : "";

        editName.setText(nameUser);
        editEmail.setText(emailUser);
        editUsername.setText(usernameUser);
        editPassword.setText(passwordUser);


        editUsername.setEnabled(false);
    }

    private void setupSaveButton() {
        saveButton.setOnClickListener(v -> {
            progressDialog.show();

            boolean changed = false;


            if (isNameChanged()) changed = true;
            if (isEmailChanged()) changed = true;
            if (isPasswordChanged()) changed = true;

            if (changed) {
                Toast.makeText(EditProfileActivity.this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                Intent resultIntent = new Intent();
                resultIntent.putExtra("name", nameUser);
                resultIntent.putExtra("email", emailUser);
                resultIntent.putExtra("password", passwordUser);
                setResult(RESULT_OK, resultIntent);
            } else {
                Toast.makeText(EditProfileActivity.this, "No changes detected", Toast.LENGTH_SHORT).show();
            }

            progressDialog.dismiss();
            finish();
        });
    }

    private boolean isNameChanged() {
        String newName = editName.getText().toString().trim();
        if (!nameUser.equals(newName) && !newName.isEmpty()) {
            reference.child(usernameUser).child("name").setValue(newName)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to update name", e);
                        Toast.makeText(this, "Failed to update name", Toast.LENGTH_SHORT).show();
                    });
            nameUser = newName;
            return true;
        }
        return false;
    }

    private boolean isEmailChanged() {
        String newEmail = editEmail.getText().toString().trim();
        if (!emailUser.equals(newEmail) && !newEmail.isEmpty()) {

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                editEmail.setError("Invalid email format");
                return false;
            }

            reference.child(usernameUser).child("email").setValue(newEmail)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to update email", e);
                        Toast.makeText(this, "Failed to update email", Toast.LENGTH_SHORT).show();
                    });
            emailUser = newEmail;
            return true;
        }
        return false;
    }

    private boolean isPasswordChanged() {
        String newPassword = editPassword.getText().toString().trim();
        if (!passwordUser.equals(newPassword) && !newPassword.isEmpty()) {
            if (newPassword.length() < 6) {
                editPassword.setError("Password must be at least 6 characters");
                return false;
            }

            reference.child(usernameUser).child("password").setValue(newPassword)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to update password", e);
                        Toast.makeText(this, "Failed to update password", Toast.LENGTH_SHORT).show();
                    });
            passwordUser = newPassword;
            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        super.onDestroy();
    }
}
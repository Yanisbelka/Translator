package com.miladghouila.proscanai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private SharedPreferences authPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Trigger background download of all translation models
        LanguageUtils.downloadAllLanguages();

        authPrefs = getSharedPreferences("auth", MODE_PRIVATE);
        if (authPrefs.getBoolean("is_logged_in", false)) {
            startActivity(new Intent(this, StartActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);

        findViewById(R.id.btn_login).setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pass = etPassword.getText().toString().trim();

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }

            // Simulated authentication
            authPrefs.edit().putBoolean("is_logged_in", true).apply();
            startActivity(new Intent(this, StartActivity.class));
            finish();
        });

        findViewById(R.id.btn_google).setOnClickListener(v -> {
            Toast.makeText(this, "Google Sign-In coming soon! Requires Firebase configuration.", Toast.LENGTH_LONG).show();
        });

        findViewById(R.id.btn_facebook).setOnClickListener(v -> {
            Toast.makeText(this, "Facebook Login coming soon! Requires Facebook SDK setup.", Toast.LENGTH_LONG).show();
        });

        findViewById(R.id.btn_register).setOnClickListener(v -> {
            Toast.makeText(this, "Registration feature coming soon!", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_forgot_password).setOnClickListener(v -> {
            Toast.makeText(this, "Password recovery sent to your email", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_anonymous).setOnClickListener(v -> {
            authPrefs.edit().putBoolean("is_logged_in", true).apply();
            startActivity(new Intent(this, StartActivity.class));
            finish();
        });
    }
}
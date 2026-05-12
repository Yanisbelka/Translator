package com.miladghouila.proscanai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class StartActivity extends AppCompatActivity {

    View cameraButton, screenButton, textButton, archiveButton;
    ImageButton btnThemeToggle;
    TextView btnContact;
    boolean isDarkMode;

    private ActivityResultLauncher<Intent> projectionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        isDarkMode = prefs.getBoolean("dark_mode", false);
        applyTheme(isDarkMode);

        super.onCreate(savedInstanceState);

        // Trigger background download of all translation models
        LanguageUtils.downloadAllLanguages();

        setContentView(R.layout.activity_start);

        cameraButton = findViewById(R.id.cameraButton);
        screenButton = findViewById(R.id.screenButton);
        textButton = findViewById(R.id.textButton);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        archiveButton = findViewById(R.id.archiveButton);
        btnContact = findViewById(R.id.btnContact);

        updateThemeIcon();

        // Initialize Projection Launcher
        projectionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent serviceIntent = new Intent(this, FloatingControlService.class);
                        serviceIntent.putExtra("CODE", result.getResultCode());
                        serviceIntent.putExtra("DATA", result.getData());
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent);
                        } else {
                            startService(serviceIntent);
                        }
                        // Minimize app to show the widget on home screen
                        moveTaskToBack(true);
                    } else {
                        Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        archiveButton.setOnClickListener(v -> {
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                Intent intent = new Intent(this, HistoryActivity.class);
                startActivity(intent);
            }).start();
        });

        btnThemeToggle.setOnClickListener(v -> {
            isDarkMode = !isDarkMode;
            prefs.edit().putBoolean("dark_mode", isDarkMode).apply();
            applyTheme(isDarkMode);
            updateThemeIcon();
            recreate();
        });

        // Contact Us
        btnContact.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:mghouila57@gmail.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "ProScan AI Feedback");
            try {
                startActivity(Intent.createChooser(intent, "Send email..."));
            } catch (android.content.ActivityNotFoundException ex) {
                Toast.makeText(this, "No email clients installed.", Toast.LENGTH_SHORT).show();
            }
        });

        // Go to camera translator
        cameraButton.setOnClickListener(v -> {
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                Intent intent = new Intent(StartActivity.this, MainActivity.class);
                startActivity(intent);
            }).start();
        });

        // Screen Translation
        screenButton.setOnClickListener(v -> {
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                checkOverlayAndStart();
            }).start();
        });

        // Text Translation
        textButton.setOnClickListener(v -> {
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                Intent intent = new Intent(this, TextTranslationActivity.class);
                startActivity(intent);
            }).start();
        });
    }

    private void checkOverlayAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, "Enable 'Display over other apps' to use Screen Magic", Toast.LENGTH_LONG).show();
                return;
            }
        }
        
        MediaProjectionManager projectionManager = (MediaProjectionManager) 
                getSystemService(MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            projectionLauncher.launch(projectionManager.createScreenCaptureIntent());
        } else {
            Toast.makeText(this, "Screen capture not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyTheme(boolean dark) {
        if (dark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    private void updateThemeIcon() {
        if (btnThemeToggle != null) {
            btnThemeToggle.setImageResource(isDarkMode ? 
                R.drawable.ic_sun : 
                R.drawable.ic_moon);
        }
    }
}
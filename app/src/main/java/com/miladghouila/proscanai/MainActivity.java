package com.miladghouila.proscanai;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private MediaProjectionManager pm;
    private static final int REQ_OVERLAY = 101;
    private static final int REQ_CAPTURE = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        findViewById(R.id.btnStartService).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQ_OVERLAY);
            } else {
                startActivityForResult(pm.createScreenCaptureIntent(), REQ_CAPTURE);
            }
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OVERLAY && Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(this)) {
            startActivityForResult(pm.createScreenCaptureIntent(), REQ_CAPTURE);
        } else if (req == REQ_CAPTURE && res == RESULT_OK) {
            Intent intent = new Intent(this, FloatingControlService.class);
            intent.putExtra("CODE", res);
            intent.putExtra("DATA", data);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            finish();
        }
    }
}
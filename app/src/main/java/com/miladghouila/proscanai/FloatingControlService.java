package com.miladghouila.proscanai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.vision.text.Text;

public class FloatingControlService extends Service implements FloatingUIManager.UIActionListener {

    private FloatingUIManager uiManager;
    private OcrManager ocrManager;
    private TranslationManager translationManager;
    private ScreenCaptureManager screenCaptureManager;

    private String targetLanguage = TranslateLanguage.FRENCH;
    private int currentOcrMode = 0;
    private boolean isFullScreenMode = true;

    private int resultCode;
    private Intent resultData;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static boolean isRunning = false;
    private boolean isBusy = false;

    @Override
    public void onCreate() {
        super.onCreate();

        uiManager = new FloatingUIManager(this, this);
        ocrManager = new OcrManager();
        translationManager = new TranslationManager();
        screenCaptureManager = new ScreenCaptureManager(this);

        translationManager.downloadCommonModels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && intent.hasExtra("CODE")) {
            resultCode = intent.getIntExtra("CODE", -1);
            resultData = intent.getParcelableExtra("DATA");

            if (!isRunning) {
                setupNotification();
                isRunning = true;
            }
        }

        return START_STICKY;
    }

    private void setupNotification() {

        String channelId = "proscan_screen_v1";

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Screen Translator",
                    NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Screen Translator Active")
                .setContentText("Tap bubble to translate screen")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, notification);
        }
    }

    @Override
    public void onTranslateClicked() {

        if (isBusy) return;

        if (resultData == null) {
            Toast.makeText(this, "Permission not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        isBusy = true;

        uiManager.setStatusText("Capturing screen...");
        uiManager.hideUIForCapture();

        screenCaptureManager.takeScreenshot(
                resultCode,
                resultData,
                mainHandler,
                new ScreenCaptureManager.CaptureCallback() {

                    @Override
                    public void onBitmapCaptured(Bitmap bitmap) {
                        processScreenshot(bitmap);
                    }

                    @Override
                    public void onError(String message) {
                        forceReset(message);
                    }
                }
        );
    }

    private void processScreenshot(Bitmap fullBitmap) {

        Bitmap inputBitmap;

        try {
            if (isFullScreenMode) {
                inputBitmap = fullBitmap;
            } else {

                Rect rect = uiManager.getSelectionRect();

                int x = Math.max(0, rect.left);
                int y = Math.max(0, rect.top);
                int w = Math.min(rect.width(), fullBitmap.getWidth() - x);
                int h = Math.min(rect.height(), fullBitmap.getHeight() - y);

                if (w <= 0 || h <= 0) {
                    fullBitmap.recycle();
                    forceReset("Invalid scan area");
                    return;
                }

                inputBitmap = Bitmap.createBitmap(fullBitmap, x, y, w, h);
                fullBitmap.recycle();
            }

            uiManager.setStatusText("Reading text...");

            ocrManager.processImage(
                    inputBitmap,
                    this::handleOcrSuccess,
                    this::handleOcrFailure
            );

        } catch (Exception e) {
            forceReset("Processing error: " + e.getMessage());
        }
    }

    private void handleOcrSuccess(Text text) {

        String content = text.getText();

        if (content == null || content.trim().isEmpty()) {
            forceReset("No text found");
            return;
        }

        content = content.replaceAll("[^\\p{L}\\p{N}\\s.,!?;:'\"\\-()\\n]", "");

        translateText(content.trim());
    }

    private void handleOcrFailure(Exception e) {
        forceReset("OCR Error: " + e.getMessage());
    }

    private void translateText(String input) {

        uiManager.setStatusText("Translating...");

        translationManager.identifyLanguage(input, code -> {

            String source = code.equals("und") ? "en" : code;

            translationManager.translate(
                    input,
                    source,
                    targetLanguage,
                    this::handleTranslationSuccess,
                    this::handleTranslationFailure
            );

        }, e -> {
            translationManager.translate(
                    input,
                    "en",
                    targetLanguage,
                    this::handleTranslationSuccess,
                    this::handleTranslationFailure
            );
        });
    }

    private void handleTranslationSuccess(String result) {

        uiManager.setStatusText(result);
        HistoryManager.saveTranslation(this, result);

        forceReset(null);
    }

    private void handleTranslationFailure(Exception e) {
        forceReset("Translation failed: " + e.getMessage());
    }

    /**
     * 🔥 THIS FIXES YOUR ISSUE COMPLETELY
     * UI ALWAYS COMES BACK
     */
    private void forceReset(String message) {

        mainHandler.post(() -> {

            if (message != null) {
                uiManager.setStatusText(message);
            }

            uiManager.restoreUI();
            isBusy = false;
        });
    }

    @Override
    public void onScanModeChanged(boolean isFullScreen) {
        this.isFullScreenMode = isFullScreen;
    }

    @Override
    public void onOcrModeChanged() {

        currentOcrMode = (currentOcrMode + 1) % 5;


        String mode;

        switch (currentOcrMode) {
            case 1: mode = "Chinese"; break;
            case 2: mode = "Japanese"; break;
            case 3: mode = "Korean"; break;
            case 4: mode = "Devanagari"; break;
            default: mode = "Latin"; break;
        }

        Toast.makeText(this, "OCR Mode: " + mode, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLanguageSelected(String code) {
        targetLanguage = code;
    }

    @Override
    public void onBackClicked() {
        stopSelf();
    }

    @Override
    public void onHistoryClicked() {
        Intent intent = new Intent(this, HistoryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        isRunning = false;
        isBusy = false;

        if (uiManager != null) uiManager.onDestroy();
        if (ocrManager != null) ocrManager.close();
        if (translationManager != null) translationManager.close();
        if (screenCaptureManager != null) screenCaptureManager.stop();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
package com.miladghouila.proscanai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.vision.text.Text;

import java.util.Locale;

public class FloatingControlService extends Service implements FloatingUIManager.UIActionListener {

    private FloatingUIManager uiManager;
    private OcrManager ocrManager;
    private TranslationManager translationManager;
    private ScreenCaptureManager screenCaptureManager;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private TextToSpeech tts;

    private String targetLanguage = TranslateLanguage.FRENCH;
    private int currentOcrMode = 0;
    private boolean isFullScreenMode = true;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isBusy = false;

    @Override
    public void onCreate() {
        super.onCreate();
        uiManager = new FloatingUIManager(this, this);
        ocrManager = new OcrManager();
        translationManager = new TranslationManager();
        screenCaptureManager = new ScreenCaptureManager(this);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        translationManager.downloadCommonModels();

        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                tts.setLanguage(new Locale(targetLanguage));
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("CODE")) {
            int resultCode = intent.getIntExtra("CODE", -1);
            Intent resultData = intent.getParcelableExtra("DATA");

            // 1. Setup notification first (requirement for foreground)
            setupNotification();

            // 2. Obtain MediaProjection immediately
            if (resultData != null) {
                try {
                    if (mediaProjection != null) {
                        mediaProjection.stop();
                    }
                    mediaProjection = projectionManager.getMediaProjection(resultCode, resultData);
                    
                    if (mediaProjection != null) {
                        mediaProjection.registerCallback(new MediaProjection.Callback() {
                            @Override
                            public void onStop() {
                                super.onStop();
                                mediaProjection = null;
                                screenCaptureManager.stop();
                            }
                        }, mainHandler);

                        // 3. START CAPTURE IMMEDIATELY to avoid Android 14 timeout
                        screenCaptureManager.initPersistentCapture(mediaProjection, mainHandler);
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "System blocked screen access. Please restart Screen Magic.", Toast.LENGTH_LONG).show();
                }
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
                .setContentTitle("LinguScan: Screen Magic Active")
                .setContentText("Tap the floating icon to translate")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, notification);
        }
    }

    @Override
    public void onTranslateClicked() {
        if (isBusy) return;

        if (mediaProjection == null) {
            Toast.makeText(this, "Capture session expired. Please re-enable Screen Magic in the app.", Toast.LENGTH_LONG).show();
            return;
        }

        isBusy = true;
        uiManager.setStatusText("Reading screen...");
        uiManager.hideUIForCapture();

        // Use the persistent frame grabber
        screenCaptureManager.captureCurrentFrame(new ScreenCaptureManager.CaptureCallback() {
            @Override
            public void onBitmapCaptured(Bitmap bitmap) {
                processScreenshot(bitmap);
            }

            @Override
            public void onError(String message) {
                forceReset(message);
            }
        });
    }

    private void processScreenshot(Bitmap fullBitmap) {
        try {
            Bitmap inputBitmap;
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
                    forceReset("Selection area is invalid");
                    return;
                }
                inputBitmap = Bitmap.createBitmap(fullBitmap, x, y, w, h);
                if (inputBitmap != fullBitmap) fullBitmap.recycle();
            }

            ocrManager.processImage(inputBitmap, this::handleOcrSuccess, this::handleOcrFailure);
        } catch (Exception e) {
            forceReset("Error: " + e.getMessage());
        }
    }

    private void handleOcrSuccess(Text text) {
        String content = LanguageUtils.cleanTextForTranslation(text.getText());
        if (content.isEmpty()) {
            forceReset("No text found on screen");
            return;
        }
        translateText(content);
    }

    private void handleOcrFailure(Exception e) {
        forceReset("Recognition failed");
    }

    private void translateText(String input) {
        uiManager.setStatusText("Translating...");
        translationManager.identifyLanguage(input, code -> {
            String source = code.equals("und") ? "en" : code;
            translationManager.translate(input, source, targetLanguage,
                    this::handleTranslationSuccess, this::handleTranslationFailure);
        }, e -> {
            translationManager.translate(input, "en", targetLanguage,
                    this::handleTranslationSuccess, this::handleTranslationFailure);
        });
    }

    private void handleTranslationSuccess(String result) {
        uiManager.setStatusText(result);
        HistoryManager.saveTranslation(this, result);
        forceReset(null);
    }

    private void handleTranslationFailure(Exception e) {
        forceReset("Translation error");
    }

    private void forceReset(String message) {
        mainHandler.post(() -> {
            if (message != null) uiManager.setStatusText(message);
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
        ocrManager.setMode(currentOcrMode);
        String mode;
        switch (currentOcrMode) {
            case 1: mode = "Chinese"; break;
            case 2: mode = "Japanese"; break;
            case 3: mode = "Korean"; break;
            case 4: mode = "Devanagari"; break;
            default: mode = "Latin"; break;
        }
        Toast.makeText(this, "Mode: " + mode, Toast.LENGTH_SHORT).show();
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
    public void onCopyClicked(String text) {
        android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cb.setPrimaryClip(android.content.ClipData.newPlainText("LinguScan", text));
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSpeakClicked(String text) {
        if (tts != null) {
            tts.setLanguage(new Locale(targetLanguage));
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isBusy = false;
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (uiManager != null) uiManager.onDestroy();
        if (ocrManager != null) ocrManager.close();
        if (translationManager != null) translationManager.close();
        if (screenCaptureManager != null) screenCaptureManager.stop();
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
package com.miladghouila.proscanai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class FloatingControlService extends Service {
    private WindowManager windowManager;
    private View floatingView, resultView, languageView;
    private ImageView deleteView;
    private WindowManager.LayoutParams params, resultParams, deleteParams, languageParams;
    private MediaProjectionManager projectionManager;
    private MediaProjection currentProjection;
    private int resultCode, screenWidth, screenHeight, screenDensity;
    private Intent resultData;
    private Translator translator;
    private List<String> languageList = TranslateLanguage.getAllLanguages();
    private TextView txtResult;
    private Vibrator vibrator;
    private boolean isMenuOpen = false;
    private LinearLayout menuContainer;

    private ArrayList<String> historyList = new ArrayList<>();
    private final Handler actionHandler = new Handler(Looper.getMainLooper());
    private static boolean isRunning = false;
    private Spinner listFrom;
    private Spinner listTo;

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
        String channelId = "proscan_v1";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(channelId, "ProScan Active", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Notification n = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("ProScan AI Active").setSmallIcon(R.mipmap.ic_launcher).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(1, n);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(dm);
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
        screenDensity = dm.densityDpi;

        prepareTranslator(TranslateLanguage.ENGLISH,TranslateLanguage.ARABIC);
        setupDeleteZone();
        setupFloatingView();
        setupResultView();
    }
    private void prepareTranslator(String langFrom,String langTo) {
        if(translator != null){
            translator.close();
        }
        translator = Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(langFrom).setTargetLanguage(langTo).build());
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build());
    }
    private void setupDeleteZone() {
        deleteView = new ImageView(this);
        deleteView.setImageResource(R.drawable.main_button_circle);
        deleteView.setPadding(16, 16, 16, 16);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Color.parseColor("#AA000000"));
        deleteView.setBackground(gd);
        deleteView.setColorFilter(Color.WHITE);

        deleteParams = new WindowManager.LayoutParams(110, 110, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        deleteParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        deleteParams.y = 100;
        deleteView.setVisibility(View.GONE);
        windowManager.addView(deleteView, deleteParams);
    }

    private void setupFloatingView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null);
        menuContainer = floatingView.findViewById(R.id.menu_container);
        View mainBubble = floatingView.findViewById(R.id.main_bubble);

        params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100; params.y = 500;
        windowManager.addView(floatingView, params);

        mainBubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;
            private boolean isDeleting = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isDeleting) return false;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        isDragging = false;
                        deleteView.setVisibility(View.VISIBLE);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dX = event.getRawX() - initialTouchX;
                        float dY = event.getRawY() - initialTouchY;
                        if (Math.abs(dX) > 10 || Math.abs(dY) > 10) {
                            isDragging = true;
                            params.x = initialX + (int) dX;
                            params.y = initialY + (int) dY;
                            windowManager.updateViewLayout(floatingView, params);
                            if (event.getRawY() > (screenHeight - 350)) {
                                isDeleting = true;
                                vibrate(100);
                                floatingView.setVisibility(View.GONE);
                                deleteView.animate().scaleX(1.5f).scaleY(1.5f).setDuration(100).withEndAction(() -> {
                                    deleteView.animate().scaleX(0f).scaleY(0f).setDuration(100).withEndAction(() -> stopSelf()).start();
                                }).start();
                                return true;
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        deleteView.setVisibility(View.GONE);
                        if (!isDragging) toggleMenu();
                        isDragging = false;
                        return true;
                }
                return false;
            }
        });

        floatingView.findViewById(R.id.btnTranslate).setOnClickListener(v -> {
            toggleMenu();
            takeScreenshotAndProcess();
        });

        floatingView.findViewById(R.id.btnHistory).setOnClickListener(v -> {
            toggleMenu();
            showHistory();
        });

        floatingView.findViewById(R.id.btnLanguage).setOnClickListener(v ->{
            toggleMenu();
            languageMenu();
        });
    }

    // THE DAMN SCREENSHOT LOGIC
    private void takeScreenshotAndProcess() {
        if (resultData == null) {
            Toast.makeText(this, "Permission Error", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Hide the bubble so it's not in the screenshot
        floatingView.setVisibility(View.GONE);

        // 2. Wait a bit for the UI to actually disappear
        actionHandler.postDelayed(() -> {
            try {
                currentProjection = projectionManager.getMediaProjection(resultCode, (Intent) resultData.clone());

                // Set up the reader with screen dimensions
                final ImageReader reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
                VirtualDisplay virtualDisplay = currentProjection.createVirtualDisplay("ScreenCapture",
                        screenWidth, screenHeight, screenDensity,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        reader.getSurface(), null, null);

                reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Image image = reader.acquireLatestImage();
                        if (image != null) {
                            processScreenshot(image);
                            image.close();
                            reader.close();
                            virtualDisplay.release();
                            currentProjection.stop();
                        }
                    }
                }, actionHandler);
            } catch (Exception e) {
                floatingView.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Capture Failed", Toast.LENGTH_SHORT).show();
            }
        }, 300);
    }

    private void processScreenshot(Image image) {
        // Convert Image to Bitmap
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;

        Bitmap bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);

        // Crop it to exact screen size (removes stride padding)
        Bitmap finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);

        // Feed to ML Kit
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(InputImage.fromBitmap(finalBitmap, 0))
                .addOnSuccessListener(visionText -> {
                    floatingView.setVisibility(View.VISIBLE); // Bring bubble back
                    if (!visionText.getText().isEmpty()) {
                        translateText(visionText.getText());
                    } else {
                        Toast.makeText(this, "No text found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    floatingView.setVisibility(View.VISIBLE);
                });
    }

    private void translateText(String text) {
        translator.translate(text).addOnSuccessListener(translatedText -> {
            historyList.add(translatedText);
            txtResult.setText(translatedText);
            if (resultView.getParent() != null) windowManager.removeView(resultView);
            windowManager.addView(resultView, resultParams);
        });
    }

    private void showHistory() {
        if (historyList.isEmpty()) {
            Toast.makeText(this, "Empty history", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder("Recent:\n");
        int start = Math.max(0, historyList.size() - 5);
        for (int i = historyList.size() - 1; i >= start; i--) {
            sb.append("• ").append(historyList.get(i)).append("\n");
        }
        txtResult.setText(sb.toString());
        if (resultView.getParent() != null) windowManager.removeView(resultView);
        windowManager.addView(resultView, resultParams);
    }

    private void languageMenu(){
        languageView = LayoutInflater.from(this).inflate(R.layout.layout_language_list, null);
        setupSpinner();
        languageParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        languageParams.gravity = Gravity.TOP;
        windowManager.addView(languageView, languageParams);
        languageView.findViewById(R.id.btnConfirm).setOnClickListener(view -> {
            prepareTranslator(listFrom.getSelectedItem().toString(), listTo.getSelectedItem().toString());
            windowManager.removeView(languageView);
            Toast.makeText(this, "Language changed", Toast.LENGTH_SHORT).show();
        });
    }
    private void setupSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, languageList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        listFrom = languageView.findViewById(R.id.listFrom);
        listFrom.setAdapter(adapter);
        listFrom.setSelection(12);
        listTo = languageView.findViewById(R.id.listTo);
        listTo.setAdapter(adapter);
        listTo.setSelection(2);
    }
    private void toggleMenu() {
        if (!isMenuOpen) {
            menuContainer.setVisibility(View.VISIBLE);
            menuContainer.setAlpha(0f);
            menuContainer.animate().alpha(1f).setDuration(200).start();
        } else {
            menuContainer.setVisibility(View.GONE);
        }
        isMenuOpen = !isMenuOpen;
    }

    private void setupResultView() {
        resultView = LayoutInflater.from(this).inflate(R.layout.layout_translation_result, null);
        resultParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        resultParams.gravity = Gravity.BOTTOM;
        txtResult = resultView.findViewById(R.id.txtTranslatedResult);
        resultView.findViewById(R.id.btnCopy).setOnClickListener(v -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("ProScan", txtResult.getText()));
            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
        });
        resultView.findViewById(R.id.btnHide).setOnClickListener(view -> {
            windowManager.removeView(resultView);
        });
        resultView.setOnClickListener(v -> { if(resultView.getParent() != null) windowManager.removeView(resultView); });
    }


    private void vibrate(int ms) {
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(ms, 150));
            else vibrator.vibrate(ms);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (floatingView != null && floatingView.getParent() != null) windowManager.removeView(floatingView);
        if (deleteView != null && deleteView.getParent() != null) windowManager.removeView(deleteView);
        if (resultView != null && resultView.getParent() != null) windowManager.removeView(resultView);
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
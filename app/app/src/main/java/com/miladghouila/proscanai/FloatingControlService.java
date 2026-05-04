package com.miladghouila.proscanai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
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
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.List;

public class FloatingControlService extends Service {

    private WindowManager windowManager;
    private View mainLayout, selectionBox, bubbleView;
    private WindowManager.LayoutParams mainParams, boxParams, bubbleParams;

    private TextView textResult;
    private Spinner languageSpinner;
    private View btnTranslate, btnLanguage, btnHistory, cardResult;
    private ImageButton btnBack;

    private String targetLanguage = TranslateLanguage.FRENCH;
    private TextRecognizer recognizer;
    private LanguageIdentifier languageIdentifier;

    private MediaProjectionManager projectionManager;
    private MediaProjection currentProjection;
    private int resultCode, screenWidth, screenHeight, screenDensity;
    private Intent resultData;

    private final Handler actionHandler = new Handler(Looper.getMainLooper());
    private static boolean isRunning = false;
    private boolean isInterfaceVisible = false;

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
            NotificationChannel channel = new NotificationChannel(channelId, "Screen Translator", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Notification n = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Screen Translator Active")
                .setContentText("Tap the floating bubble to show/hide translator")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, n);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(dm);
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
        screenDensity = dm.densityDpi;

        setupUI();
        
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        languageIdentifier = LanguageIdentification.getClient();
        downloadCommonModels();
    }

    private void setupUI() {
        ContextThemeWrapper contextThemeWrapper = new ContextThemeWrapper(this, R.style.Theme_ProScanAI);
        LayoutInflater inflater = LayoutInflater.from(contextThemeWrapper);

        // 1. Setup Selection Box Window
        selectionBox = inflater.inflate(R.layout.layout_selection_box, null);
        boxParams = new WindowManager.LayoutParams(
                600, 400,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        boxParams.gravity = Gravity.TOP | Gravity.START;
        boxParams.x = (screenWidth - 600) / 2;
        boxParams.y = screenHeight / 4;
        selectionBox.setVisibility(View.GONE);
        windowManager.addView(selectionBox, boxParams);
        setupBoxTouchListeners();

        // 2. Setup Controls Window (Bottom)
        mainLayout = inflater.inflate(R.layout.layout_floating_screen, null);
        mainParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        mainParams.gravity = Gravity.BOTTOM;
        mainLayout.setVisibility(View.GONE);
        windowManager.addView(mainLayout, mainParams);

        // 3. Setup Floating Bubble Window
        bubbleView = inflater.inflate(R.layout.layout_floating_widget, null);
        bubbleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 0;
        bubbleParams.y = screenHeight / 2;
        windowManager.addView(bubbleView, bubbleParams);
        setupBubbleTouchListener();

        // UI Mapping
        textResult = mainLayout.findViewById(R.id.textResult);
        cardResult = mainLayout.findViewById(R.id.cardResult);
        languageSpinner = mainLayout.findViewById(R.id.languageSpinner);
        btnTranslate = mainLayout.findViewById(R.id.btnTranslate);
        btnLanguage = mainLayout.findViewById(R.id.btnLanguage);
        btnHistory = mainLayout.findViewById(R.id.btnHistory);
        btnBack = mainLayout.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> stopSelf());

        // Language Spinner Setup
        List<LanguageUtils.LanguageItem> allLanguages = LanguageUtils.getSupportedLanguages(false);
        ArrayAdapter<LanguageUtils.LanguageItem> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, allLanguages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);

        for (int i = 0; i < allLanguages.size(); i++) {
            if (allLanguages.get(i).code.equals("fr")) {
                languageSpinner.setSelection(i);
                break;
            }
        }

        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                targetLanguage = allLanguages.get(position).code;
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnLanguage.setOnClickListener(v -> {
            mainParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(mainLayout, mainParams);
            languageSpinner.performClick();
        });

        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        btnTranslate.setOnClickListener(v -> {
            textResult.setText("Scanning area...");
            takeScreenshotAndProcess();
        });

        setupResizableResultCard();
    }

    private void setupBubbleTouchListener() {
        View mainBubble = bubbleView.findViewById(R.id.main_bubble);
        mainBubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        bubbleParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        bubbleParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(bubbleView, bubbleParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - touchStartTime;
                        float deltaX = Math.abs(event.getRawX() - initialTouchX);
                        float deltaY = Math.abs(event.getRawY() - initialTouchY);
                        
                        if (duration < 200 && deltaX < 10 && deltaY < 10) {
                            toggleInterface();
                            v.performClick();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void toggleInterface() {
        isInterfaceVisible = !isInterfaceVisible;
        if (isInterfaceVisible) {
            mainLayout.setVisibility(View.VISIBLE);
            selectionBox.setVisibility(View.VISIBLE);
            ((android.widget.ImageView)bubbleView.findViewById(R.id.main_bubble)).setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        } else {
            mainLayout.setVisibility(View.GONE);
            selectionBox.setVisibility(View.GONE);
            ((android.widget.ImageView)bubbleView.findViewById(R.id.main_bubble)).setImageResource(android.R.drawable.ic_menu_add);
        }
    }

    private void setupBoxTouchListeners() {
        View resizeHandle = selectionBox.findViewById(R.id.resizeHandle);
        View boxRoot = selectionBox.findViewById(R.id.boxRoot);

        boxRoot.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = boxParams.x;
                        initialY = boxParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        boxParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        boxParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(selectionBox, boxParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.performClick();
                        return true;
                }
                return false;
            }
        });

        resizeHandle.setOnTouchListener(new View.OnTouchListener() {
            private int initialWidth, initialHeight;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialWidth = boxParams.width;
                        initialHeight = boxParams.height;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        boxParams.width = Math.max(200, initialWidth + (int) (event.getRawX() - initialTouchX));
                        boxParams.height = Math.max(150, initialHeight + (int) (event.getRawY() - initialTouchY));
                        windowManager.updateViewLayout(selectionBox, boxParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.performClick();
                        return true;
                }
                return false;
            }
        });
    }

    private void setupResizableResultCard() {
        cardResult.setOnTouchListener(new View.OnTouchListener() {
            private int initialWidth, initialHeight;
            private float initialTouchX, initialTouchY;
            private boolean isResizing = false;
            private static final int TOUCH_THRESHOLD = 60;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        float x = event.getX();
                        float y = event.getY();
                        if (x > v.getWidth() - TOUCH_THRESHOLD || x < TOUCH_THRESHOLD || y < TOUCH_THRESHOLD) {
                            isResizing = true;
                            initialWidth = v.getWidth();
                            initialHeight = v.getHeight();
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            mainParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                            windowManager.updateViewLayout(mainLayout, mainParams);
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (isResizing) {
                            int newWidth = initialWidth + (int) (Math.abs(event.getRawX() - initialTouchX) * 2);
                            int newHeight = initialHeight + (int) (initialTouchY - event.getRawY());
                            ViewGroup.LayoutParams lp = v.getLayoutParams();
                            lp.width = Math.min(newWidth, screenWidth - 40);
                            lp.height = Math.max(200, newHeight);
                            v.setLayoutParams(lp);
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        isResizing = false;
                        mainParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                        windowManager.updateViewLayout(mainLayout, mainParams);
                        v.performClick();
                        return true;
                }
                return false;
            }
        });
    }

    private void takeScreenshotAndProcess() {
        if (resultData == null) return;
        mainLayout.setVisibility(View.GONE);
        selectionBox.setVisibility(View.GONE);
        bubbleView.setVisibility(View.GONE);

        actionHandler.postDelayed(() -> {
            try {
                currentProjection = projectionManager.getMediaProjection(resultCode, (Intent) resultData.clone());
                if (currentProjection == null) {
                    restoreUI();
                    return;
                }

                final ImageReader reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
                VirtualDisplay virtualDisplay = currentProjection.createVirtualDisplay("Capture", screenWidth, screenHeight, screenDensity,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, null);

                reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                    private boolean captured = false;
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        if (captured) return;
                        Image img = null;
                        try {
                            img = reader.acquireLatestImage();
                            if (img != null) {
                                captured = true;
                                processScreenshot(img);
                                img.close(); reader.close(); virtualDisplay.release();
                                currentProjection.stop(); currentProjection = null;
                            }
                        } catch (Exception e) { if (img != null) img.close(); }
                    }
                }, actionHandler);
            } catch (Exception e) { restoreUI(); }
        }, 300);
    }

    private void restoreUI() {
        if (isInterfaceVisible) {
            mainLayout.setVisibility(View.VISIBLE);
            selectionBox.setVisibility(View.VISIBLE);
        }
        bubbleView.setVisibility(View.VISIBLE);
    }

    private void processScreenshot(Image image) {
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;

            Bitmap fullBitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888);
            fullBitmap.copyPixelsFromBuffer(buffer);

            int cropX = Math.max(0, boxParams.x);
            int cropY = Math.max(0, boxParams.y);
            int cropW = Math.min(boxParams.width, screenWidth - cropX);
            int cropH = Math.min(boxParams.height, screenHeight - cropY);

            Bitmap croppedBitmap = Bitmap.createBitmap(fullBitmap, cropX, cropY, cropW, cropH);
            fullBitmap.recycle();

            InputImage inputImage = InputImage.fromBitmap(croppedBitmap, 0);
            recognizer.process(inputImage)
                    .addOnSuccessListener(text -> {
                        restoreUI();
                        String content = text.getText();
                        if (content != null && !content.trim().isEmpty()) {
                            translateText(content);
                        } else {
                            textResult.setText("No text found in Scan Area");
                            cardResult.setVisibility(View.VISIBLE);
                        }
                    })
                    .addOnFailureListener(e -> {
                        restoreUI();
                        textResult.setText("Scan failed");
                    });
        } catch (Exception e) { restoreUI(); }
    }

    private void translateText(String input) {
        languageIdentifier.identifyLanguage(input)
                .addOnSuccessListener(code -> performTranslation(input, code.equals("und") ? "en" : code))
                .addOnFailureListener(e -> performTranslation(input, "en"));
    }

    private void performTranslation(String input, String sourceCode) {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode).setTargetLanguage(targetLanguage).build();
        Translator t = Translation.getClient(options);
        t.downloadModelIfNeeded().addOnSuccessListener(unused -> {
            t.translate(input).addOnSuccessListener(result -> {
                textResult.setText(result);
                cardResult.setVisibility(View.VISIBLE);
                HistoryManager.saveTranslation(this, result);
                t.close();
            }).addOnFailureListener(e -> {
                textResult.setText(input);
                cardResult.setVisibility(View.VISIBLE);
                t.close();
            });
        }).addOnFailureListener(e -> t.close());
    }

    private void downloadCommonModels() {
        RemoteModelManager modelManager = RemoteModelManager.getInstance();
        DownloadConditions conditions = new DownloadConditions.Builder().requireWifi().build();
        for (String code : LanguageUtils.getCommonLanguageCodes()) {
            modelManager.download(new TranslateRemoteModel.Builder(code).build(), conditions);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (mainLayout != null) windowManager.removeView(mainLayout);
        if (selectionBox != null) windowManager.removeView(selectionBox);
        if (bubbleView != null) windowManager.removeView(bubbleView);
        if (recognizer != null) recognizer.close();
        if (languageIdentifier != null) languageIdentifier.close();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
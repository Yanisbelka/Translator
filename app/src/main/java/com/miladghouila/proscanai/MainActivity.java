package com.miladghouila.proscanai;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView textResult, txtCurrentLang;
    private Spinner languageSpinner;
    private View btnTranslate, btnLanguage, btnHistory, cardResult, btnOcrScript;
    private ImageButton btnBack;

    private String targetLanguage = TranslateLanguage.FRENCH;
    private int currentOcrMode = 0; // 0: Latin, 1: Chinese, 2: Japanese, 3: Korean, 4: Devanagari
    private TextRecognizer recognizer;
    private LanguageIdentifier languageIdentifier;

    // Flag to trigger processing on button click
    private final AtomicBoolean shouldTranslate = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI
        previewView = findViewById(R.id.previewView);
        textResult = findViewById(R.id.textResult);
        cardResult = findViewById(R.id.cardResult);
        txtCurrentLang = findViewById(R.id.txtCurrentLang);
        languageSpinner = findViewById(R.id.languageSpinner);
        btnTranslate = findViewById(R.id.btnTranslate);
        btnLanguage = findViewById(R.id.btnLanguage);
        btnHistory = findViewById(R.id.btnHistory);
        btnBack = findViewById(R.id.btnBack);
        btnOcrScript = findViewById(R.id.btnOcrScript);

        btnBack.setOnClickListener(v -> finish());

        btnOcrScript.setOnClickListener(v -> {
            currentOcrMode = (currentOcrMode + 1) % 5;
            if (recognizer != null) recognizer.close();

            String modeName = "Latin";
            switch (currentOcrMode) {
                case 0:
                    recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                    modeName = "Latin (Western)";
                    break;
                case 1:
                    recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
                    modeName = "Chinese";
                    break;
                case 2:
                    recognizer = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
                    modeName = "Japanese";
                    break;
                case 3:
                    recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
                    modeName = "Korean";
                    break;
                case 4:
                    recognizer = TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build());
                    modeName = "Devanagari (Hindi)";
                    break;
            }
            Toast.makeText(this, "Camera OCR: " + modeName, Toast.LENGTH_SHORT).show();
        });

        // Setup language spinner with ALL languages
        List<LanguageUtils.LanguageItem> allLanguages = LanguageUtils.getSupportedLanguages(false);
        ArrayAdapter<LanguageUtils.LanguageItem> adapter = new ArrayAdapter<>(this,
                R.layout.spinner_item, allLanguages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);

        // Default to French
        for (int i = 0; i < allLanguages.size(); i++) {
            if (allLanguages.get(i).code.equals("fr")) {
                languageSpinner.setSelection(i);
                if (txtCurrentLang != null) txtCurrentLang.setText("FR");
                break;
            }
        }

        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                targetLanguage = allLanguages.get(position).code;
                if (txtCurrentLang != null) {
                    txtCurrentLang.setText(targetLanguage.toUpperCase());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Trigger spinner on icon click
        btnLanguage.setOnClickListener(v -> languageSpinner.performClick());

        // Initialize Identifier
        languageIdentifier = LanguageIdentification.getClient();

        // History logic
        btnHistory.setOnClickListener(v -> {
            java.util.List<String> history = HistoryManager.getHistory(this);
            if (history.isEmpty()) {
                Toast.makeText(this, "No history yet", Toast.LENGTH_SHORT).show();
            } else {
                StringBuilder sb = new StringBuilder("Recent Translations:\n\n");
                for (int i = 0; i < Math.min(history.size(), 3); i++) {
                    sb.append("• ").append(history.get(i)).append("\n\n");
                }
                textResult.setText(sb.toString());
                Toast.makeText(this, "Long-press for full history", Toast.LENGTH_SHORT).show();
            }
        });

        btnHistory.setOnLongClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivity(intent);
            return true;
        });

        // Initialize recognizer (Latin by default, supports French, Spanish, German, etc.)
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // Button Click Listener
        btnTranslate.setOnClickListener(v -> {
            textResult.setText("Processing...");
            shouldTranslate.set(true);
        });

        // Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, 1);
        }

        setupResizableCard();
        downloadCommonModels();
    }

    private void downloadCommonModels() {
        RemoteModelManager modelManager = RemoteModelManager.getInstance();
        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi()
                .build();

        for (String code : LanguageUtils.getCommonLanguageCodes()) {
            TranslateRemoteModel model = new TranslateRemoteModel.Builder(code).build();
            modelManager.download(model, conditions);
        }
    }

    private void setupResizableCard() {
        cardResult.setOnTouchListener(new View.OnTouchListener() {
            private float initialTouchX, initialTouchY;
            private int initialWidth, initialHeight;
            private boolean isResizing = false;
            private static final int TOUCH_THRESHOLD = 60; // Near edges

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Get screen dimensions
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                int screenWidth = dm.widthPixels;
                int screenHeight = dm.heightPixels;
                int horizontalPadding = (int)(24 * dm.density); // Sum of horizontal margins/padding

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        float x = event.getX();
                        float y = event.getY();
                        int w = v.getWidth();
                        int h = v.getHeight();

                        if (x > w - TOUCH_THRESHOLD || x < TOUCH_THRESHOLD || y < TOUCH_THRESHOLD) {
                            isResizing = true;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            initialWidth = w;
                            initialHeight = h;
                            return true;
                        }
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (isResizing) {
                            float deltaX = event.getRawX() - initialTouchX;
                            float deltaY = initialTouchY - event.getRawY();

                            ViewGroup.LayoutParams params = v.getLayoutParams();

                            // Horizontal Resize: Restrict width to screen width minus margins
                            int newWidth = Math.max(300, initialWidth + (int)(Math.abs(deltaX) * 2));
                            params.width = Math.min(newWidth, screenWidth - horizontalPadding);

                            // Vertical Resize: Restrict height to half screen height (or reasonable limit)
                            int newHeight = Math.max(200, initialHeight + (int)deltaY);
                            params.height = Math.min(newHeight, screenHeight / 2);

                            v.setLayoutParams(params);
                            return true;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        isResizing = false;
                        if (v.performClick()) return true;
                        break;
                }
                return false;
            }
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis =
                        new ImageAnalysis.Builder()
                                .setTargetResolution(new Size(1280, 720))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build();

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this),
                        this::processImage);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this,
                        cameraSelector,
                        preview,
                        imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processImage(ImageProxy imageProxy) {
        if (shouldTranslate.getAndSet(false) && imageProxy.getImage() != null) {

            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        String fullText = text.getText();
                        if (fullText != null && !fullText.trim().isEmpty()) {
                            // Professional cleaning: Keep only letters, numbers, spaces, and basic punctuation
                            fullText = fullText.replaceAll("[^\\p{L}\\p{N}\\s.,!?;:'\"\\-()\\n]", "");
                            translateText(fullText.trim());
                        } else {
                            textResult.setText("No text detected in view");
                        }
                    })
                    .addOnFailureListener(e -> {
                        textResult.setText("Recognition failed");
                        e.printStackTrace();
                    })
                    .addOnCompleteListener(task -> imageProxy.close());

        } else {
            imageProxy.close();
        }
    }

    private void translateText(String input) {
        if (input == null || input.trim().isEmpty()) return;

        // Step 1: Detect Language Automatically
        languageIdentifier.identifyLanguage(input)
                .addOnSuccessListener(languageCode -> {
                    if (languageCode.equals("und")) {
                        performTranslation(input, TranslateLanguage.ENGLISH);
                    } else {
                        performTranslation(input, languageCode);
                    }
                })
                .addOnFailureListener(e -> performTranslation(input, TranslateLanguage.ENGLISH));
    }

    private void performTranslation(String input, String sourceCode) {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode)
                .setTargetLanguage(targetLanguage)
                .build();

        Translator t = Translation.getClient(options);
        t.downloadModelIfNeeded().addOnSuccessListener(unused -> {
            String[] lines = input.split("\n");
            final String[] translatedLines = new String[lines.length];
            final java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);

            for (int i = 0; i < lines.length; i++) {
                final int index = i;
                String line = lines[i];
                if (line.trim().isEmpty()) {
                    translatedLines[index] = line;
                    if (count.incrementAndGet() == lines.length) {
                        finishTranslation(translatedLines, t);
                    }
                } else {
                    t.translate(line)
                            .addOnSuccessListener(result -> {
                                translatedLines[index] = result;
                                if (count.incrementAndGet() == lines.length) {
                                    finishTranslation(translatedLines, t);
                                }
                            })
                            .addOnFailureListener(e -> {
                                translatedLines[index] = line; // fallback to original
                                if (count.incrementAndGet() == lines.length) {
                                    finishTranslation(translatedLines, t);
                                }
                            });
                }
            }
        }).addOnFailureListener(e -> {
            textResult.setText("Model download failed");
            t.close();
        });
    }

    private void finishTranslation(String[] lines, Translator t) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(lines[i]);
            if (i < lines.length - 1) sb.append("\n");
        }
        String finalResult = sb.toString();
        textResult.setText(finalResult);
        HistoryManager.saveTranslation(this, finalResult);
        t.close();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }
}
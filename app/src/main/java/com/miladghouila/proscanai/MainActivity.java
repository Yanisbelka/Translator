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
    private View btnTranslate, btnLanguage, btnHistory, cardResult, btnOcrScript, btnGallery, scanLine;
    private ImageButton btnBack, btnFlashlight;

    private String targetLanguage = TranslateLanguage.FRENCH;
    private int currentOcrMode = 0; // 0: Latin, 1: Chinese, 2: Japanese, 3: Korean, 4: Devanagari
    private TextRecognizer recognizer;
    private LanguageIdentifier languageIdentifier;
    private androidx.camera.core.Camera camera;
    private boolean isFlashlightOn = false;
    private final androidx.activity.result.ActivityResultLauncher<String> galleryLauncher = 
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                processGalleryImage(uri);
            }
        });

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
        btnFlashlight = findViewById(R.id.btnFlashlight);
        btnGallery = findViewById(R.id.btnGallery);
        scanLine = findViewById(R.id.scanLine);

        btnBack.setOnClickListener(v -> finish());

        btnFlashlight.setOnClickListener(v -> {
            if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
                isFlashlightOn = !isFlashlightOn;
                camera.getCameraControl().enableTorch(isFlashlightOn);
                btnFlashlight.setImageResource(isFlashlightOn ? 
                    android.R.drawable.btn_star_big_on : 
                    android.R.drawable.btn_star_big_off);
            } else {
                Toast.makeText(this, "Flashlight not available", Toast.LENGTH_SHORT).show();
            }
        });

        btnGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));

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
            startScanAnimation();
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
        View handle = findViewById(R.id.resizeHandle);
        handle.setOnTouchListener(new View.OnTouchListener() {
            private int initialHeight;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialHeight = cardResult.getHeight();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaY = initialTouchY - event.getRawY();
                        int newHeight = (int) (initialHeight + deltaY);
                        
                        // Limits: 150dp to 60% of screen height
                        int minHeight = (int)(150 * getResources().getDisplayMetrics().density);
                        int maxHeight = (int)(getResources().getDisplayMetrics().heightPixels * 0.6f);
                        
                        if (newHeight >= minHeight && newHeight <= maxHeight) {
                            ViewGroup.LayoutParams params = cardResult.getLayoutParams();
                            params.height = newHeight;
                            cardResult.setLayoutParams(params);
                        }
                        return true;
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
                camera = cameraProvider.bindToLifecycle(this,
                        cameraSelector,
                        preview,
                        imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void startScanAnimation() {
        scanLine.setVisibility(View.VISIBLE);
        scanLine.setTranslationY(0);
        scanLine.animate()
                .translationY(previewView.getHeight())
                .setDuration(1500)
                .withEndAction(() -> scanLine.setVisibility(View.GONE))
                .start();
    }

    private void processImage(ImageProxy imageProxy) {
        if (shouldTranslate.getAndSet(false) && imageProxy.getImage() != null) {

            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        String fullText = LanguageUtils.cleanTextForTranslation(text.getText());
                        if (!fullText.isEmpty()) {
                            translateText(fullText);
                        } else {
                            textResult.setText("No clear text detected");
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

    private void processGalleryImage(android.net.Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            textResult.setText("Extracting text from image...");
            cardResult.setVisibility(View.VISIBLE);
            
            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        String fullText = LanguageUtils.cleanTextForTranslation(text.getText());
                        if (!fullText.isEmpty()) {
                            translateText(fullText);
                        } else {
                            textResult.setText("No readable text found in image");
                        }
                    })
                    .addOnFailureListener(e -> {
                        textResult.setText("Failed to process image");
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } catch (java.io.IOException e) {
            e.printStackTrace();
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
        if (sourceCode.equals(targetLanguage)) {
            textResult.setText(input);
            HistoryManager.saveTranslation(this, input);
            return;
        }

        // Use high-quality block translation with aggressive model checking
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode)
                .setTargetLanguage(targetLanguage)
                .build();

        Translator t = Translation.getClient(options);
        
        t.downloadModelIfNeeded().addOnSuccessListener(unused -> {
            t.translate(input)
                    .addOnSuccessListener(result -> {
                        String finalResult = result.trim();
                        textResult.setText(finalResult);
                        HistoryManager.saveTranslation(this, finalResult);
                        t.close();
                    })
                    .addOnFailureListener(e -> {
                        textResult.setText("Translation failed: " + e.getLocalizedMessage());
                        t.close();
                    });
        }).addOnFailureListener(e -> {
            textResult.setText("Model missing. Ensure Wifi is on for background download.");
            t.close();
        });
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
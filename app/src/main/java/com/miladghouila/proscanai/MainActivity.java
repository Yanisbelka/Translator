package com.miladghouila.proscanai;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.speech.tts.TextToSpeech;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

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
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private View btnTranslate, btnLanguage, btnHistory, cardResult, btnGallery, scanLine;
    private ImageButton btnBack, btnFlashlight, btnCopy, btnTTS;
    private TextToSpeech textToSpeech;

    private TextView textResult, txtCurrentLang, txtSourceLang;
    private Spinner languageSpinner, sourceSpinner;
    private String sourceLanguage = "auto";
    private String targetLanguage = TranslateLanguage.FRENCH;
    private OcrManager ocrManager;
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
        textResult.setTextColor(0xFFFFFFFF); // All white as requested
        cardResult = findViewById(R.id.cardResult);
        txtCurrentLang = findViewById(R.id.txtCurrentLang);
        txtSourceLang = findViewById(R.id.txtSourceLang);
        languageSpinner = findViewById(R.id.languageSpinner);
        sourceSpinner = findViewById(R.id.sourceSpinner);
        btnTranslate = findViewById(R.id.btnTranslate);
        btnLanguage = findViewById(R.id.btnLanguage);
        btnHistory = findViewById(R.id.btnHistory);
        btnBack = findViewById(R.id.btnBack);
        btnFlashlight = findViewById(R.id.btnFlashlight);
        btnGallery = findViewById(R.id.btnGallery);
        scanLine = findViewById(R.id.scanLine);
        btnCopy = findViewById(R.id.btnCopy);
        btnTTS = findViewById(R.id.btnTTS);

        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Translation", textResult.getText().toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        textToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) {
                btnTTS.setEnabled(false);
            }
        });

        btnTTS.setOnClickListener(v -> {
            String text = textResult.getText().toString();
            if (!text.isEmpty()) {
                // Professional TTS Configuration
                java.util.Locale locale = new java.util.Locale(targetLanguage);
                
                // Special handling for scripts that need specific locales for better quality
                if (targetLanguage.equals("zh")) locale = java.util.Locale.SIMPLIFIED_CHINESE;
                
                textToSpeech.setLanguage(locale);
                textToSpeech.setPitch(1.0f);
                textToSpeech.setSpeechRate(0.95f); // Slightly slower for better clarity

                int result = textToSpeech.setLanguage(locale);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "Optimized voice not found. Using system default.", Toast.LENGTH_SHORT).show();
                }
                
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "LinguScanTTS");
            }
        });

        btnBack.setOnClickListener(v -> {
            // Use onBackPressed to ensure standard behavior
            onBackPressed();
        });

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

        // Setup language spinners
        List<LanguageUtils.LanguageItem> allLanguages = LanguageUtils.getSupportedLanguages(false);
        List<LanguageUtils.LanguageItem> sourceLanguages = LanguageUtils.getSupportedLanguages(true);

        ArrayAdapter<LanguageUtils.LanguageItem> adapter = new ArrayAdapter<>(this,
                R.layout.spinner_item, allLanguages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);

        ArrayAdapter<LanguageUtils.LanguageItem> sourceAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_item, sourceLanguages);
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceSpinner.setAdapter(sourceAdapter);

        // Defaults
        for (int i = 0; i < allLanguages.size(); i++) {
            if (allLanguages.get(i).code.equals("fr")) {
                languageSpinner.setSelection(i);
                if (txtCurrentLang != null) txtCurrentLang.setText("FR");
                break;
            }
        }
        sourceSpinner.setSelection(0); // Auto

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

        sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sourceLanguage = sourceLanguages.get(position).code;
                if (txtSourceLang != null) {
                    txtSourceLang.setText(sourceLanguage.equalsIgnoreCase("auto") ? "AUTO" : sourceLanguage.toUpperCase());
                }
                if (ocrManager != null) {
                    // Critical: Set OCR engine based on the language we are LOOKING AT
                    ocrManager.setModeForSourceLanguage(sourceLanguage);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Trigger spinners
        btnLanguage.setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Translation Pair")
                .setItems(new String[]{"Source Language: " + txtSourceLang.getText(), "Target Language: " + txtCurrentLang.getText()}, (dialog, which) -> {
                    if (which == 0) sourceSpinner.performClick();
                    else languageSpinner.performClick();
                })
                .show();
        });

        // Initialize Identifiers
        languageIdentifier = LanguageIdentification.getClient();
        ocrManager = new OcrManager();
        // Correct initialization: use sourceLanguage (default auto/latin)
        ocrManager.setModeForSourceLanguage(sourceLanguage);

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

        // Initialize recognizer
        // recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS); // Removed in favor of ocrManager

        // Button Click Listener
        btnTranslate.setOnClickListener(v -> {
            setControlsEnabled(false); // Disable all controls
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

        // Show a one-time dialog or option to download all languages for full offline support
        checkAndDownloadAllLanguages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensure controls are enabled when returning to activity
        setControlsEnabled(true);
    }

    private void setControlsEnabled(boolean enabled) {
        if (btnTranslate != null) btnTranslate.setEnabled(enabled);
        if (btnGallery != null) btnGallery.setEnabled(enabled);
        if (btnHistory != null) btnHistory.setEnabled(enabled);
        if (btnLanguage != null) btnLanguage.setEnabled(enabled);
    }

    private void checkAndDownloadAllLanguages() {
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        boolean allDownloaded = prefs.getBoolean("all_languages_downloaded", false);

        if (!allDownloaded) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Full Offline Support")
                .setMessage("Would you like to download all translation models (approx. 2GB) to translate everything offline? This works best on Wi-Fi.")
                .setPositiveButton("Download Now", (dialog, which) -> {
                    Toast.makeText(this, "Downloading all languages in background...", Toast.LENGTH_LONG).show();
                    LanguageUtils.downloadAllLanguages();
                    prefs.edit().putBoolean("all_languages_downloaded", true).apply();
                })
                .setNegativeButton("Maybe Later", null)
                .show();
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
                        imageProxy -> processImage(imageProxy));

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

    private boolean isTooDark(ImageProxy image) {
        if (image.getPlanes().length == 0) return false;
        java.nio.ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        long sum = 0;
        for (byte b : data) sum += (b & 0xFF);
        double avg = (double) sum / data.length;
        return avg < 40; // Threshold for dark image
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

    @androidx.camera.core.ExperimentalGetImage
    private void processImage(ImageProxy imageProxy) {
        try {
            if (shouldTranslate.getAndSet(false) && imageProxy.getImage() != null) {

                // Quality Check: Brightness
                if (isTooDark(imageProxy)) {
                    runOnUiThread(() -> Toast.makeText(this, "🔦 Too dark! Try using the flashlight.", Toast.LENGTH_SHORT).show());
                }

                InputImage image = InputImage.fromMediaImage(
                        imageProxy.getImage(),
                        imageProxy.getImageInfo().getRotationDegrees()
                );

                ocrManager.processWithVerification(image, text -> {
                    // Smart Script Conflict Warning
                    String conflict = LanguageUtils.getScriptConflict(sourceLanguage, text);
                    if (conflict != null) {
                        Toast.makeText(this, "⚠️ Camera sees " + conflict + ". Switch source for accuracy.", Toast.LENGTH_LONG).show();
                    }

                    // UNIVERSAL PIPELINE: Recognition -> Filtering -> Refinement
                    String cleanText = LanguageUtils.processUniversalPipeline(text, sourceLanguage);

                    if (!cleanText.isEmpty()) {
                        translateText(cleanText);
                    } else {
                        textResult.setText("No clear text detected");
                        cardResult.setVisibility(View.VISIBLE);
                        setControlsEnabled(true);
                    }
                    imageProxy.close();
                }, e -> {
                    textResult.setText("Recognition failed");
                    setControlsEnabled(true);
                    e.printStackTrace();
                    imageProxy.close();
                });

            } else {
                imageProxy.close();
            }
        } catch (Exception e) {
            imageProxy.close();
            e.printStackTrace();
            textResult.setText("Error during scan");
            setControlsEnabled(true);
        }
    }


    private void processGalleryImage(android.net.Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            textResult.setText("Extracting text from image...");
            cardResult.setVisibility(View.VISIBLE);

            ocrManager.process(image, text -> {
                String cleanText = LanguageUtils.processUniversalPipeline(text, sourceLanguage);

                if (!cleanText.isEmpty()) {
                    translateText(cleanText);
                } else {
                    textResult.setText("No readable text found in image");
                }
            }, e -> {
                textResult.setText("Failed to process image");
            });
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    private void translateText(String input) {
        if (input == null || input.trim().isEmpty()) return;

        // Show the result card immediately with a smooth fade-in animation
        if (cardResult.getVisibility() != View.VISIBLE) {
            cardResult.setAlpha(0f);
            cardResult.setVisibility(View.VISIBLE);
            cardResult.animate().alpha(1f).setDuration(400).start();
        }

        // Optimization: If the user manually selected a source language, use it directly.
        // This avoids detection errors caused by OCR noise/hallucinations.
        if (sourceLanguage != null && !sourceLanguage.equalsIgnoreCase("auto")) {
            performTranslation(input, sourceLanguage);
            return;
        }

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
            formatAndShowText(input);
            HistoryManager.saveTranslation(this, input);
            setControlsEnabled(true);
            return;
        }

        // Use high-quality block translation with verification enabled if not AUTO
        boolean verify = !sourceCode.equalsIgnoreCase("auto");
        
        TranslationManager tm = new TranslationManager(); // Temporary manager or reuse instance
        tm.translate(input, sourceCode, targetLanguage, verify, result -> {
            String finalResult = result.trim();
            formatAndShowText(finalResult);
            HistoryManager.saveTranslation(this, finalResult);
            tm.close();
            setControlsEnabled(true);
        }, e -> {
            textResult.setText("Translation failed");
            tm.close();
            setControlsEnabled(true);
        });
    }

    /**
     * Shows the text using the default system font with clear spacing.
     * Respects the original line structure and punctuation.
     * Automatically handles LTR and RTL text direction.
     */
    private void formatAndShowText(String text) {
        if (text == null || text.trim().isEmpty()) return;

        // Auto-detect direction and alignment for the result view
        textResult.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        textResult.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        String[] lines = text.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            int start = ssb.length();

            // Simple Header Recognition: Bold the first line or short all-caps lines
            // Also check if it's not a RTL script before forcing upper case
            boolean isRtl = LanguageUtils.isRtl(line);
            boolean isHeader = (i == 0) ||
                              (!isRtl && line.length() < 50 && line.equals(line.toUpperCase()) && line.length() > 3);

            if (isHeader && !isRtl) {
                ssb.append(line.toUpperCase()).append("\n\n");
                int end = ssb.length();
                ssb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                ssb.append(line).append("\n"); // Respect original single line breaks
            }
        }

        textResult.setText(ssb);
        textResult.setTextColor(0xFFFFFFFF); // All White
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ocrManager != null) ocrManager.close();
        if (languageIdentifier != null) languageIdentifier.close();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
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
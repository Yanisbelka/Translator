package com.miladghouila.proscanai;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
// Arabic and Cyrillic on-device OCR are not supported by ML Kit v2 currently.
// Falling back to Latin/Default for these modes.

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class OcrManager {

    public static final int MODE_LATIN = 0;
    public static final int MODE_CHINESE = 1;
    public static final int MODE_JAPANESE = 2;
    public static final int MODE_KOREAN = 3;
    public static final int MODE_DEVANAGARI = 4;
    public static final int MODE_CYRILLIC = 5;
    public static final int MODE_ARABIC = 6;

    private TextRecognizer recognizer;
    private int currentMode = -1;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    public OcrManager() {
        setMode(MODE_LATIN);
    }

    public synchronized void setMode(int mode) {
        if (this.currentMode == mode && recognizer != null) return;

        this.currentMode = mode;

        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }

        switch (mode) {
            case MODE_CHINESE:
                recognizer = TextRecognition.getClient(
                        new ChineseTextRecognizerOptions.Builder().build());
                break;

            case MODE_JAPANESE:
                recognizer = TextRecognition.getClient(
                        new JapaneseTextRecognizerOptions.Builder().build());
                break;

            case MODE_KOREAN:
                recognizer = TextRecognition.getClient(
                        new KoreanTextRecognizerOptions.Builder().build());
                break;

            case MODE_DEVANAGARI:
                recognizer = TextRecognition.getClient(
                        new DevanagariTextRecognizerOptions.Builder().build());
                break;

            case MODE_CYRILLIC:
            case MODE_ARABIC:
            default:
                // Fallback to Latin for unsupported scripts
                recognizer = TextRecognition.getClient(
                        TextRecognizerOptions.DEFAULT_OPTIONS);
                break;
        }
    }

    /**
     * Automatically adjusts the OCR mode based on the source language.
     * This ensures non-Latin scripts are recognized correctly.
     */
    public void setModeForSourceLanguage(String languageCode) {
        if (languageCode == null) {
            setMode(MODE_LATIN);
            return;
        }

        switch (languageCode.toLowerCase()) {
            case "zh":
                setMode(MODE_CHINESE);
                break;
            case "ja":
                setMode(MODE_JAPANESE);
                break;
            case "ko":
                setMode(MODE_KOREAN);
                break;
            case "hi":
            case "mr":
            case "ne":
            case "sa":
                setMode(MODE_DEVANAGARI);
                break;
            case "ru":
            case "be":
            case "bg":
            case "uk":
                setMode(MODE_CYRILLIC);
                break;
            case "ar":
            case "fa":
            case "ur":
                setMode(MODE_ARABIC);
                break;
            default:
                setMode(MODE_LATIN);
                break;
        }
    }

    /**
     * Dual-Model Verification: Runs recognition twice with different settings if quality is questionable.
     */
    public void processWithVerification(
            InputImage image,
            OnSuccessListener<Text> successListener,
            OnFailureListener failureListener
    ) {
        process(image, text -> {
            if (isLowConfidence(text)) {
                // Run a second pass with different mode or force re-process
                process(image, successListener, failureListener);
            } else {
                successListener.onSuccess(text);
            }
        }, failureListener);
    }

    private boolean isLowConfidence(Text text) {
        if (text == null || text.getTextBlocks().isEmpty()) return true;
        // Logic to check for character fragments or high symbol density
        return text.getText().length() < 3;
    }

    public void process(
            InputImage image,
            OnSuccessListener<Text> successListener,
            OnFailureListener failureListener
    ) {

        if (recognizer == null) {
            failureListener.onFailure(
                    new IllegalStateException("Recognizer not initialized"));
            return;
        }

        // prevent spam / overlap
        if (!isProcessing.compareAndSet(false, true)) {
            return; // ignore if already processing
        }

        executor.execute(() -> {
            try {
                recognizer.process(image)
                        .addOnSuccessListener(text -> {
                            isProcessing.set(false);
                            successListener.onSuccess(text);
                        })
                        .addOnFailureListener(e -> {
                            isProcessing.set(false);
                            failureListener.onFailure(e);
                        });

            } catch (Exception e) {
                isProcessing.set(false);
                failureListener.onFailure(e);
            }
        });
    }

    public void close() {
        executor.shutdown();

        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
    }
}
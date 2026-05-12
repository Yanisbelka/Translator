package com.miladghouila.proscanai;

import android.graphics.Bitmap;

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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class OcrManager {

    private TextRecognizer recognizer;
    private int currentMode = -1;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    public OcrManager() {
        setMode(0);
    }

    public synchronized void setMode(int mode) {
        if (this.currentMode == mode && recognizer != null) return;

        this.currentMode = mode;

        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }

        switch (mode) {
            case 1:
                recognizer = TextRecognition.getClient(
                        new ChineseTextRecognizerOptions.Builder().build());
                break;

            case 2:
                recognizer = TextRecognition.getClient(
                        new JapaneseTextRecognizerOptions.Builder().build());
                break;

            case 3:
                recognizer = TextRecognition.getClient(
                        new KoreanTextRecognizerOptions.Builder().build());
                break;

            case 4:
                recognizer = TextRecognition.getClient(
                        new DevanagariTextRecognizerOptions.Builder().build());
                break;

            default:
                recognizer = TextRecognition.getClient(
                        TextRecognizerOptions.DEFAULT_OPTIONS);
                break;
        }
    }

    public void processImage(
            Bitmap bitmap,
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
                InputImage image = InputImage.fromBitmap(bitmap, 0);

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
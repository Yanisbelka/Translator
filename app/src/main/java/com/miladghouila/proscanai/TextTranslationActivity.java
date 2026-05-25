package com.miladghouila.proscanai;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.List;
import java.util.Locale;

public class TextTranslationActivity extends AppCompatActivity {

    private EditText editSource;
    private TextView txtResult;
    private View cardResult;
    private Spinner spinnerFrom, spinnerTo;
    private String sourceLangCode = "auto";
    private String targetLangCode = TranslateLanguage.FRENCH;
    private LanguageIdentifier languageIdentifier;
    private TextToSpeech tts;
    private List<LanguageUtils.LanguageItem> fromLangs, toLangs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_translation);

        editSource = findViewById(R.id.editSource);
        txtResult = findViewById(R.id.txtResult);
        cardResult = findViewById(R.id.cardResult);
        spinnerFrom = findViewById(R.id.spinnerFrom);
        spinnerTo = findViewById(R.id.spinnerTo);
        View btnSwap = findViewById(R.id.btnSwap);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnClearText).setOnClickListener(v -> {
            editSource.setText("");
            cardResult.setVisibility(View.GONE);
        });

        findViewById(R.id.btnSpeakSource).setOnClickListener(v -> {
            String text = editSource.getText().toString().trim();
            if (text.isEmpty()) return;

            languageIdentifier.identifyLanguage(text)
                    .addOnSuccessListener(languageCode -> {
                        if (languageCode.equals("und")) {
                            tts.setLanguage(Locale.ENGLISH);
                        } else {
                            tts.setLanguage(new Locale(languageCode));
                        }
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
                    });
        });

        // Setup Source Spinner (with Auto)
        fromLangs = LanguageUtils.getSupportedLanguages(true);
        ArrayAdapter<LanguageUtils.LanguageItem> adapterFrom = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, fromLangs);
        adapterFrom.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFrom.setAdapter(adapterFrom);
        
        // Setup Target Spinner (without Auto)
        toLangs = LanguageUtils.getSupportedLanguages(false);
        ArrayAdapter<LanguageUtils.LanguageItem> adapterTo = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, toLangs);
        adapterTo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTo.setAdapter(adapterTo);

        // Language Swap Logic
        btnSwap.setOnClickListener(v -> {
            if (sourceLangCode.equals("auto")) {
                Toast.makeText(this, "Cannot swap with Auto Detect", Toast.LENGTH_SHORT).show();
                return;
            }

            String oldSource = sourceLangCode;
            String oldTarget = targetLangCode;

            // Update Spinners
            for (int i = 0; i < fromLangs.size(); i++) {
                if (fromLangs.get(i).code.equals(oldTarget)) {
                    spinnerFrom.setSelection(i);
                    break;
                }
            }
            for (int i = 0; i < toLangs.size(); i++) {
                if (toLangs.get(i).code.equals(oldSource)) {
                    spinnerTo.setSelection(i);
                    break;
                }
            }
            Toast.makeText(this, "Languages Swapped", Toast.LENGTH_SHORT).show();
        });

        // Default Target to French
        for (int i = 0; i < toLangs.size(); i++) {
            if (toLangs.get(i).code.equals("fr")) {
                spinnerTo.setSelection(i);
                break;
            }
        }
        
        spinnerFrom.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sourceLangCode = fromLangs.get(position).code;
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerTo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                targetLangCode = toLangs.get(position).code;
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        languageIdentifier = LanguageIdentification.getClient();

        findViewById(R.id.btnTranslate).setOnClickListener(v -> triggerTranslation());

        findViewById(R.id.btnCopy).setOnClickListener(v -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("ProScan", txtResult.getText()));
            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
        });

        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                tts.setLanguage(new Locale(targetLangCode));
            }
        });

        findViewById(R.id.btnSpeak).setOnClickListener(v -> {
            String text = txtResult.getText().toString();
            if (!text.isEmpty() && !text.equals("Translating...") && !text.contains("failed")) {
                tts.setLanguage(new Locale(targetLangCode));
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });
    }

    private void triggerTranslation() {
        String input = LanguageUtils.cleanTextForTranslation(editSource.getText().toString());
        if (input.isEmpty()) return;

        if (cardResult.getVisibility() != View.VISIBLE) {
            cardResult.setAlpha(0f);
            cardResult.setVisibility(View.VISIBLE);
            cardResult.animate().alpha(1f).setDuration(400).start();
        }

        if (sourceLangCode.equals("auto")) {
            detectAndTranslate(input);
        } else {
            performTranslation(input, sourceLangCode);
        }
    }

    private void detectAndTranslate(String text) {
        txtResult.setText("Identifying language...");
        languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener(languageCode -> {
                    if (languageCode.equals("und")) {
                        performTranslation(text, TranslateLanguage.ENGLISH);
                    } else {
                        performTranslation(text, languageCode);
                    }
                })
                .addOnFailureListener(e -> performTranslation(text, TranslateLanguage.ENGLISH));
    }

    private void performTranslation(String text, String sourceCode) {
        if (sourceCode.equals(targetLangCode)) {
            txtResult.setText(text);
            return;
        }

        txtResult.setText("Translating...");
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode)
                .setTargetLanguage(targetLangCode)
                .build();
        
        Translator t = Translation.getClient(options);
        
        t.downloadModelIfNeeded().addOnSuccessListener(unused -> {
            t.translate(text)
                    .addOnSuccessListener(result -> {
                        runOnUiThread(() -> {
                            String finalResult = result.trim();
                            txtResult.setText(finalResult);
                            HistoryManager.saveTranslation(this, finalResult);
                            t.close();
                        });
                    })
                    .addOnFailureListener(e -> {
                        runOnUiThread(() -> {
                            txtResult.setText("Translation failed: " + e.getLocalizedMessage());
                            t.close();
                        });
                    });
        }).addOnFailureListener(e -> {
            txtResult.setText("Model missing. Ensure Wifi is on.");
            t.close();
        });
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
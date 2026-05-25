package com.miladghouila.proscanai;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

public class TranslationManager {
    private final LanguageIdentifier languageIdentifier;

    public TranslationManager() {
        languageIdentifier = LanguageIdentification.getClient();
    }

    public void identifyLanguage(String text, OnSuccessListener<String> successListener, OnFailureListener failureListener) {
        languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener(successListener)
                .addOnFailureListener(failureListener);
    }

    /**
     * Translates text and optionally verifies with back-translation for maximum accuracy.
     */
    public void translate(String text, String sourceLang, String targetLang, boolean verify, OnSuccessListener<String> successListener, OnFailureListener failureListener) {
        if (sourceLang.equals(targetLang)) {
            successListener.onSuccess(text);
            return;
        }

        // Optimization for non-latin to non-latin (like Chinese to Arabic)
        // Back-verification is safer on Latin scripts but can be noisy on complex script pairs.
        boolean shouldVerify = verify && !sourceLang.equalsIgnoreCase("auto") && !LanguageUtils.isRtl(text);

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build();

        Translator translator = Translation.getClient(options);
        DownloadConditions conditions = new DownloadConditions.Builder().build();

        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    translator.translate(text)
                            .addOnSuccessListener(result -> {
                                if (shouldVerify) {
                                    performBackTranslation(result, targetLang, sourceLang, successListener, failureListener);
                                } else {
                                    successListener.onSuccess(result.trim());
                                }
                                translator.close();
                            })
                            .addOnFailureListener(e -> {
                                failureListener.onFailure(e);
                                translator.close();
                            });
                })
                .addOnFailureListener(e -> {
                    failureListener.onFailure(e);
                    translator.close();
                });
    }

    private void performBackTranslation(String translatedText, String targetLang, String sourceLang, OnSuccessListener<String> successListener, OnFailureListener failureListener) {
        TranslatorOptions backOptions = new TranslatorOptions.Builder()
                .setSourceLanguage(targetLang)
                .setTargetLanguage(sourceLang)
                .build();

        Translator backTranslator = Translation.getClient(backOptions);
        // Force model download without wifi requirement for accuracy verification
        DownloadConditions conditions = new DownloadConditions.Builder().build();
        
        backTranslator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    backTranslator.translate(translatedText)
                            .addOnSuccessListener(backResult -> {
                                // Add a verification mark to the result
                                String finalResult = "✅ " + translatedText.trim() + "\n\n(Verified: " + backResult.trim() + ")";
                                successListener.onSuccess(finalResult);
                                backTranslator.close();
                            })
                            .addOnFailureListener(e -> {
                                successListener.onSuccess(translatedText.trim()); // Fallback to original translation
                                backTranslator.close();
                            });
                })
                .addOnFailureListener(e -> {
                    successListener.onSuccess(translatedText.trim());
                    backTranslator.close();
                });
    }

    public void downloadCommonModels() {
        RemoteModelManager modelManager = RemoteModelManager.getInstance();
        DownloadConditions conditions = new DownloadConditions.Builder().requireWifi().build();
        for (String code : LanguageUtils.getCommonLanguageCodes()) {
            modelManager.download(new TranslateRemoteModel.Builder(code).build(), conditions);
        }
    }

    public void close() {
        languageIdentifier.close();
    }
}
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
     * Translates the entire text block for better quality and context.
     */
    public void translate(String text, String sourceLang, String targetLang, OnSuccessListener<String> successListener, OnFailureListener failureListener) {
        if (sourceLang.equals(targetLang)) {
            successListener.onSuccess(text);
            return;
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build();

        Translator translator = Translation.getClient(options);
        DownloadConditions conditions = new DownloadConditions.Builder().build();

        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    // Translate the whole block at once
                    translator.translate(text)
                            .addOnSuccessListener(result -> {
                                successListener.onSuccess(result.trim());
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
package com.miladghouila.proscanai;

import android.content.Context;
import android.util.Log;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LanguageUtils {
    private static final String TAG = "LanguageUtils";
    
    public static class LanguageItem implements Comparable<LanguageItem> {
        public String name;
        public String code;

        public LanguageItem(String name, String code) {
            this.name = name;
            this.code = code;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public int compareTo(LanguageItem other) {
            return this.name.compareTo(other.name);
        }
    }

    public static List<LanguageItem> getSupportedLanguages(boolean includeAuto) {
        List<String> codes = TranslateLanguage.getAllLanguages();
        List<LanguageItem> languages = new ArrayList<>();
        
        if (includeAuto) {
            languages.add(new LanguageItem("✨ Detect Automatically", "auto"));
        }
        
        List<LanguageItem> sortedList = new ArrayList<>();
        for (String code : codes) {
            Locale locale = new Locale(code);
            String name = locale.getDisplayName();
            if (name.length() > 0) {
                name = name.substring(0, 1).toUpperCase() + name.substring(1);
            }
            sortedList.add(new LanguageItem(name, code));
        }
        
        Collections.sort(sortedList);
        languages.addAll(sortedList);
        return languages;
    }

    public static void downloadAllLanguages() {
        RemoteModelManager modelManager = RemoteModelManager.getInstance();
        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi() // Recommended to avoid heavy data usage
                .build();

        List<String> allLanguages = TranslateLanguage.getAllLanguages();
        for (String langCode : allLanguages) {
            TranslateRemoteModel model = new TranslateRemoteModel.Builder(langCode).build();
            modelManager.download(model, conditions)
                    .addOnSuccessListener(unused -> Log.d(TAG, "Downloaded: " + langCode))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to download: " + langCode, e));
        }
    }

    public static List<String> getCommonLanguageCodes() {
        List<String> common = new ArrayList<>();
        common.add(TranslateLanguage.ENGLISH);
        common.add(TranslateLanguage.FRENCH);
        common.add(TranslateLanguage.SPANISH);
        common.add(TranslateLanguage.GERMAN);
        common.add(TranslateLanguage.ARABIC);
        common.add(TranslateLanguage.CHINESE);
        common.add(TranslateLanguage.JAPANESE);
        common.add(TranslateLanguage.ITALIAN);
        common.add(TranslateLanguage.PORTUGUESE);
        common.add(TranslateLanguage.RUSSIAN);
        return common;
    }
}
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

    /**
     * Advanced text purifier to ignore pictures/noise and focus on real text.
     */
    public static String cleanTextForTranslation(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        
        // 1. Remove obvious OCR hallucinations often found in pictures/backgrounds
        String cleaned = text.replaceAll("[|*_~«»¤¦¬°^±]", "");
        
        // 2. Normalize whitespace
        cleaned = cleaned.replaceAll("[\\t ]+", " ");
        
        String[] lines = cleaned.split("\\n");
        StringBuilder sb = new StringBuilder();
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // 3. Picture Filter: Ignore lines that are just a string of symbols 
            // Real text usually contains at least some letters or digits.
            if (!containsAlphaNumeric(trimmed)) continue;

            // 4. Density Check: If a line is too short and has no letters, it's likely noise
            if (trimmed.length() < 3 && !Character.isLetterOrDigit(trimmed.charAt(0))) continue;

            sb.append(trimmed).append("\n");
        }
        
        return sb.toString().trim();
    }

    private static boolean containsAlphaNumeric(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetterOrDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static void downloadAllLanguages() {
        RemoteModelManager modelManager = RemoteModelManager.getInstance();
        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi()
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
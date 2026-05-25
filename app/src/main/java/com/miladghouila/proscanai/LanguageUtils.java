package com.miladghouila.proscanai;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * UniversalTextPipeline: The definitive structure for ProScan AI translation.
 * Coordinates recognition data, filtering, and linguistic refinement.
 */
public class LanguageUtils {

    // --- RECOGNITION & PIPELINE CORE ---

    /**
     * Master method for cleaning raw user input from EditTexts or other strings.
     */
    public static String cleanTextForTranslation(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        String purified = applyDeepFiltering(text);
        return refineByScript(purified);
    }

    /**
     * The Master Pipeline: From raw OCR data to user-ready clean text.
     */
    public static String processUniversalPipeline(Text visionText, String sourceLangCode) {
        if (visionText == null || visionText.getTextBlocks().isEmpty()) return "";

        // 1. Structure Analysis (Maintain logical reading order and columns)
        String structuredText = extractStructuredLayout(visionText);

        // 2. Intelligence Pass (Detect if we are looking at garbage/mismatched script)
        if (isLikelyGarbage(structuredText)) {
            return "[⚠️ Low Confidence: Please check if source language matches.]";
        }

        // 3. Multi-Stage Filtering
        String purified = applyDeepFiltering(structuredText);

        // 4. Linguistic Refinement (Script-specific spacing and punctuation)
        return refineByScript(purified);
    }

    private static String extractStructuredLayout(Text text) {
        List<Text.TextBlock> blocks = new ArrayList<>(text.getTextBlocks());

        // Geometric Sorting: First by Column (X), then by Row (Y)
        // This handles multi-column text like newspapers or menus perfectly.
        Collections.sort(blocks, (b1, b2) -> {
            int x1 = b1.getBoundingBox() != null ? b1.getBoundingBox().left : 0;
            int x2 = b2.getBoundingBox() != null ? b2.getBoundingBox().left : 0;
            
            // If they are in the same horizontal "column area" (within 100px), sort by vertical
            if (Math.abs(x1 - x2) < 100) {
                int y1 = b1.getBoundingBox() != null ? b1.getBoundingBox().top : 0;
                int y2 = b2.getBoundingBox() != null ? b2.getBoundingBox().top : 0;
                return Integer.compare(y1, y2);
            }
            return Integer.compare(x1, x2);
        });

        StringBuilder sb = new StringBuilder();
        for (Text.TextBlock block : blocks) {
            String bText = block.getText().trim();
            if (bText.isEmpty()) continue;
            
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(bText);
        }
        return sb.toString();
    }

    // --- DEEP FILTERING ENGINE ---

    private static String applyDeepFiltering(String text) {
        // Stage A: Digital Junk Purge (URLs, Domains, Emails)
        String result = text.replaceAll("(?i)\\b((https?|ftp|file)://|www\\.)[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]", " ");
        result = result.replaceAll("(?i)\\b[a-z0-9.-]+\\.(com|net|org|edu|gov|io|info|me|co|biz|tv|app|dev|sh|ai|so|link|xyz|site|online|store|web|icu|top|win|bid|loan|date|party|space|review|website)\\b(/[\\w.-]*)*", " ");
        result = result.replaceAll("[\\w.-]+@[\\w.-]+\\.[A-Za-z]{2,4}", " ");

        // Stage B: Technical & Serial Scrubbing
        // Removes Hex, long IDs, coordinate strings, and tech-boilerplates
        result = result.replaceAll("\\b(0x[0-9a-fA-F]+|[A-Z0-9]{6,}|[0-9]{5,}[\\-:][0-9]{2,}|[0-9]{8,})\\b", " ");

        // Stage C: UI Clutter Neural Filter
        // Expanded list of common "trash" words that appear in screen captures/UI
        String uiTrash = "(?i)\\b(MENU|HOME|LOGIN|SIGNIN|SIGNUP|SETTINGS|PROFILE|SHARE|FOLLOW|WIKI|SUBSCRIBE|DOWNLOAD|UPLOAD|SYSTEM|WIFI|BATTERY|SIGNAL|CLOCK|ALARM|WEATHER|MAPS|STORES|GALLERY|PHONE|MAIL|WIKIPEDIA|ARTICLE|SOURCE|AD|SPONSORED|COPYRIGHT|COOKIES|ACCEPT|IGNORE|RETRY|LOADING|REFRESH|ERROR|FAILED|ADMIN|PASSWORD|TOKEN|ROOT|USER|VERSION|BUILD|BRANCH|REPO|GIT)\\b";
        result = result.replaceAll(uiTrash, " ");

        // Stage D: Visual Artifact Cleanup
        // Remove random single symbols floating around (often OCR hallucinations)
        // Refined to avoid eating non-Latin script characters.
        result = result.replaceAll("[¤¦¬±·•●□■○△▽▷◁✔✘✓✕✖✨⭐†‡¶]", " ");
        result = result.replaceAll("(?<![\\p{L}\\p{N}])[\\\\/|\\-_~]{1,}(?![\\p{L}\\p{N}])", " ");

        return result;
    }

    // --- LINGUISTIC REFINEMENT ---

    private static String refineByScript(String text) {
        if (text == null || text.trim().isEmpty()) return "";

        // Stage 1: Neural Paragraph Stitching
        // Joins broken lines that are part of the same semantic sentence.
        String stitched = stitchSentences(text);

        boolean isLatin = isPrimarilyLatin(stitched);
        String polished = stitched;

        if (isLatin) {
            // Fix Spacing for LTR languages
            polished = polished.replaceAll("([.,!?:;])([^\\s\\d.,!?:;])", "$1 $2");
            polished = polished.replaceAll("([^\\s])(\\()", "$1 $2");
            polished = polished.replaceAll("(\\))([^\\s.,!?:;])", "$1 $2");
            polished = polished.replaceAll("\\s{2,}", " ");
        } else {
            // Fix CJK Fragmentation: Join Chinese/Japanese/Korean characters that OCR broke apart
            polished = polished.replaceAll("([一-龥ヿ가-힯])\\s+([一-龥ヿ가-힯])", "$1$2");
        }

        // Deduplication pass
        String[] lines = polished.split("\n");
        StringBuilder sb = new StringBuilder();
        LinkedHashSet<String> seenNormalised = new LinkedHashSet<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || isLikelyNoise(trimmed)) continue;
            
            // Deduplicate case-insensitively but preserve original casing for the first occurrence
            // Using lowercase for tracking, but appending the original trimmed line.
            if (seenNormalised.add(trimmed.toLowerCase(Locale.ROOT))) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(trimmed);
            }
        }

        return sb.toString().trim();
    }

    private static String stitchSentences(String text) {
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String current = lines[i].trim();
            if (current.isEmpty()) continue;

            if (sb.length() > 0) {
                char lastChar = sb.charAt(sb.length() - 1);
                // If the previous line doesn't end with a sentence-finishing punctuation, 
                // and the current line doesn't start with a capital/header, stitch them.
                if (".!?।。".indexOf(lastChar) == -1) {
                    sb.append(" ");
                } else {
                    sb.append("\n");
                }
            }
            sb.append(current);
        }
        return sb.toString();
    }

    // --- HELPERS & DETECTION ---

    public static boolean isRtl(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            byte directionality = Character.getDirectionality(c);
            if (directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                return true;
            }
        }
        return false;
    }

    public static boolean isNonLatin(String text) {
        if (text == null || text.isEmpty()) return false;
        int nonLatin = 0, total = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                total++;
                if (c > 'ɏ') nonLatin++;
            }
        }
        return total > 0 && (double) nonLatin / total > 0.3;
    }

    private static boolean isPrimarilyLatin(String text) {
        if (text == null || text.isEmpty()) return false;
        int latin = 0, total = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                total++;
                if (c <= 'ɏ') latin++;
            }
        }
        return total > 0 && (double) latin / total > 0.6;
    }

    private static boolean isLikelyGarbage(String text) {
        if (text == null || text.length() < 5) return false;
        int weird = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // If it's a letter (any script), digit, whitespace, or common punctuation, it's not "weird"
            if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c) && ",.!?()[]{}।。".indexOf(c) == -1) {
                weird++;
            }
        }
        return (double) weird / text.length() > 0.45;
    }

    private static boolean isLikelyNoise(String s) {
        int total = s.length();
        if (total == 0) return true;
        int letterOrDigit = 0;
        for (int i = 0; i < total; i++) {
            int cp = s.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) letterOrDigit++;
            if (Character.isSupplementaryCodePoint(cp)) i++;
        }
        return letterOrDigit == 0 || (total < 3 && letterOrDigit < 1) || (total > 5 && (double) letterOrDigit / total < 0.3);
    }

    // --- SUPPORTED LANGUAGES ---

    public static List<LanguageItem> getSupportedLanguages(boolean includeAuto) {
        List<String> codes = TranslateLanguage.getAllLanguages();
        List<LanguageItem> languages = new ArrayList<>();
        if (includeAuto) languages.add(new LanguageItem("✨ Detect Automatically", "auto"));
        List<LanguageItem> sortedList = new ArrayList<>();
        for (String code : codes) {
            Locale locale = new Locale(code);
            String name = locale.getDisplayName();
            if (name.length() > 0) name = name.substring(0, 1).toUpperCase() + name.substring(1);
            sortedList.add(new LanguageItem(name, code));
        }
        Collections.sort(sortedList);
        languages.addAll(sortedList);
        return languages;
    }

    public static List<String> getCommonLanguageCodes() {
        List<String> common = new ArrayList<>();
        String[] codes = {TranslateLanguage.ENGLISH, TranslateLanguage.FRENCH, TranslateLanguage.SPANISH, TranslateLanguage.GERMAN, TranslateLanguage.ARABIC, TranslateLanguage.CHINESE, TranslateLanguage.JAPANESE, TranslateLanguage.ITALIAN, TranslateLanguage.PORTUGUESE, TranslateLanguage.RUSSIAN};
        Collections.addAll(common, codes);
        return common;
    }

    public static void downloadAllLanguages() {
        RemoteModelManager modelManager = RemoteModelManager.getInstance();
        DownloadConditions conditions = new DownloadConditions.Builder().requireWifi().build();
        for (String langCode : TranslateLanguage.getAllLanguages()) {
            TranslateRemoteModel model = new TranslateRemoteModel.Builder(langCode).build();
            modelManager.download(model, conditions);
        }
    }

    public static String getScriptConflict(String selectedCode, Text text) {
        if (text == null || text.getTextBlocks().isEmpty() || selectedCode.equalsIgnoreCase("auto")) return null;
        int latin = 0, cjk = 0, devanagari = 0, cyrillic = 0, arabic = 0, total = 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (char c : block.getText().toCharArray()) {
                if (!Character.isLetter(c)) continue;
                total++;
                if (c <= 'ɏ') latin++;
                else if ((c >= '一' && c <= '鿿') || (c >= 'ヿ' && c <= 'ヿ') || (c >= '가' && c <= '힯')) cjk++;
                else if (c >= 'ऀ' && c <= 'ॿ') devanagari++;
                else if (c >= 'Ѐ' && c <= 'ӿ') cyrillic++;
                else if (c >= '؀' && c <= 'ۿ') arabic++;
            }
        }
        if (total < 5) return null;
        boolean isLat = selectedCode.matches("en|fr|es|de|it|pt");
        boolean isCjk = selectedCode.matches("zh|ja|ko");
        if (isLat && (double) cjk / total > 0.4) return "Chinese/Japanese/Korean";
        if (isLat && (double) devanagari / total > 0.4) return "Hindi/Devanagari";
        if (isLat && (double) cyrillic / total > 0.4) return "Russian/Cyrillic";
        if (isLat && (double) arabic / total > 0.4) return "Arabic/Persian";
        if (isCjk && (double) latin / total > 0.7) return "Latin/English";
        return null;
    }

    public static class LanguageItem implements Comparable<LanguageItem> {
        public String name, code;
        public LanguageItem(String name, String code) { this.name = name; this.code = code; }
        @Override public String toString() { return name; }
        @Override public int compareTo(LanguageItem other) { return this.name.compareTo(other.name); }
    }
}
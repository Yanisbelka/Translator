package com.miladghouila.proscanai;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HistoryManager {
    private static final String PREF_NAME = "translation_history";
    private static final String KEY_HISTORY = "history_list";
    private static final String KEY_FAVORITES = "favorites_set";
    private static final int MAX_HISTORY_SIZE = 50;

    public static void saveTranslation(Context context, String text) {
        if (text == null || text.trim().isEmpty()) return;
        
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        List<String> history = getHistory(context);
        
        // Remove duplicate if exists to move it to top
        history.remove(text.trim());
        history.add(0, text.trim());
        
        // Keep only the latest entries
        if (history.size() > MAX_HISTORY_SIZE) {
            history = history.subList(0, MAX_HISTORY_SIZE);
        }
        
        JSONArray jsonArray = new JSONArray(history);
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply();
    }

    public static List<String> getHistory(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_HISTORY, "[]");
        List<String> history = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                history.add(jsonArray.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return history;
    }

    public static void clearHistory(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_HISTORY).apply();
    }

    // FAVORITES SYSTEM
    public static void toggleFavorite(Context context, String text) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> favorites = new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
        
        if (favorites.contains(text)) {
            favorites.remove(text);
        } else {
            favorites.add(text);
        }
        
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply();
    }

    public static boolean isFavorite(Context context, String text) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> favorites = prefs.getStringSet(KEY_FAVORITES, new HashSet<>());
        return favorites.contains(text);
    }

    public static List<String> getFavorites(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return new ArrayList<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
    }
}
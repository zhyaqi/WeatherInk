package com.zyq.weatherink;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Preferences {
    private static final String STORE = "weather_ink_preferences";
    private static final String API_HOST = "api_host";
    private static final String API_KEY = "api_key";
    private static final String CITIES = "cities";
    private static final String CURRENT_CITY_ID = "current_city_id";
    private static final String CACHED_CITY_ID = "cached_city_id";
    private static final String CACHED_WEATHER = "cached_weather";

    private Preferences() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    public static String getApiHost(Context context) {
        return prefs(context).getString(API_HOST, "");
    }

    public static String getApiKey(Context context) {
        return prefs(context).getString(API_KEY, "");
    }

    public static void saveApiSettings(Context context, String host, String key) {
        prefs(context).edit().putString(API_HOST, host.trim()).putString(API_KEY, key.trim()).apply();
    }

    public static boolean hasApiSettings(Context context) {
        return !getApiHost(context).isEmpty() && !getApiKey(context).isEmpty();
    }

    public static List<City> loadCities(Context context) {
        List<City> result = new ArrayList<>();
        String raw = prefs(context).getString(CITIES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                result.add(City.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
            // A malformed local cache should not make the app unusable.
        }
        return result;
    }

    public static void saveCities(Context context, List<City> cities) {
        JSONArray array = new JSONArray();
        try {
            for (City city : cities) {
                array.put(city.toJson());
            }
        } catch (JSONException ignored) {
            return;
        }
        prefs(context).edit().putString(CITIES, array.toString()).apply();
    }

    public static void addCity(Context context, City city) {
        List<City> cities = loadCities(context);
        if (!cities.contains(city)) {
            cities.add(city);
            saveCities(context, cities);
        }
        setCurrentCity(context, city);
    }

    public static City getCurrentCity(Context context) {
        String id = prefs(context).getString(CURRENT_CITY_ID, "");
        if (id.isEmpty()) {
            return null;
        }
        for (City city : loadCities(context)) {
            if (city.id.equals(id)) {
                return city;
            }
        }
        return null;
    }

    public static void setCurrentCity(Context context, City city) {
        if (city == null) {
            prefs(context).edit().remove(CURRENT_CITY_ID).apply();
        } else {
            prefs(context).edit().putString(CURRENT_CITY_ID, city.id).apply();
        }
    }

    public static void saveWeather(Context context, City city, WeatherData data) {
        try {
            prefs(context).edit()
                    .putString(CACHED_CITY_ID, city.id)
                    .putString(CACHED_WEATHER, data.toJson().toString())
                    .apply();
        } catch (JSONException ignored) {
            // The live data remains visible even if local caching fails.
        }
    }

    public static WeatherData loadWeather(Context context, City city) {
        SharedPreferences store = prefs(context);
        if (city == null || !city.id.equals(store.getString(CACHED_CITY_ID, ""))) {
            return null;
        }
        String raw = store.getString(CACHED_WEATHER, "");
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return WeatherData.fromJson(new JSONObject(raw));
        } catch (JSONException ignored) {
            return null;
        }
    }

    public static void clearWeatherCache(Context context) {
        prefs(context).edit().remove(CACHED_CITY_ID).remove(CACHED_WEATHER).apply();
    }
}

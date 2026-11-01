package com.zyq.weatherink;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WeatherData {
    public String obsTime = "";
    public String temp = "--";
    public String feelsLike = "--";
    public String text = "未知";
    public String windDir = "--";
    public String windScale = "--";
    public String humidity = "--";
    public String pressure = "--";
    public String vis = "--";
    public String cloud = "--";
    public List<ForecastDay> forecast = Collections.emptyList();

    public JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("obsTime", obsTime);
        root.put("temp", temp);
        root.put("feelsLike", feelsLike);
        root.put("text", text);
        root.put("windDir", windDir);
        root.put("windScale", windScale);
        root.put("humidity", humidity);
        root.put("pressure", pressure);
        root.put("vis", vis);
        root.put("cloud", cloud);

        JSONArray days = new JSONArray();
        for (ForecastDay day : forecast) {
            days.put(day.toJson());
        }
        root.put("forecast", days);
        return root;
    }

    public static WeatherData fromJson(JSONObject root) throws JSONException {
        WeatherData data = new WeatherData();
        data.obsTime = root.optString("obsTime", "");
        data.temp = root.optString("temp", "--");
        data.feelsLike = root.optString("feelsLike", "--");
        data.text = root.optString("text", "未知");
        data.windDir = root.optString("windDir", "--");
        data.windScale = root.optString("windScale", "--");
        data.humidity = root.optString("humidity", "--");
        data.pressure = root.optString("pressure", "--");
        data.vis = root.optString("vis", "--");
        data.cloud = root.optString("cloud", "--");

        List<ForecastDay> days = new ArrayList<>();
        JSONArray array = root.optJSONArray("forecast");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                days.add(ForecastDay.fromJson(array.getJSONObject(i)));
            }
        }
        data.forecast = days;
        return data;
    }

    public static final class ForecastDay {
        public String date = "";
        public String tempMax = "--";
        public String tempMin = "--";
        public String textDay = "";
        public String textNight = "";
        public String precip = "--";
        public String uvIndex = "--";
        public String windDirDay = "--";
        public String windScaleDay = "--";

        public static ForecastDay fromJson(JSONObject object) {
            ForecastDay day = new ForecastDay();
            day.date = object.optString("date", "");
            day.tempMax = object.optString("tempMax", "--");
            day.tempMin = object.optString("tempMin", "--");
            day.textDay = object.optString("textDay", "");
            day.textNight = object.optString("textNight", "");
            day.precip = object.optString("precip", "--");
            day.uvIndex = object.optString("uvIndex", "--");
            day.windDirDay = object.optString("windDirDay", "--");
            day.windScaleDay = object.optString("windScaleDay", "--");
            return day;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject object = new JSONObject();
            object.put("date", date);
            object.put("tempMax", tempMax);
            object.put("tempMin", tempMin);
            object.put("textDay", textDay);
            object.put("textNight", textNight);
            object.put("precip", precip);
            object.put("uvIndex", uvIndex);
            object.put("windDirDay", windDirDay);
            object.put("windScaleDay", windScaleDay);
            return object;
        }
    }
}

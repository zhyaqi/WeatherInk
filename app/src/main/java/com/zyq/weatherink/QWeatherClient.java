package com.zyq.weatherink;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class QWeatherClient {
    public interface WeatherCallback {
        void onSuccess(WeatherData data);

        void onError(String message);
    }

    public interface CitiesCallback {
        void onSuccess(List<City> cities);

        void onError(String message);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final String host;
    private final String apiKey;

    public QWeatherClient(String host, String apiKey) {
        this.host = normalizeHost(host);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public static String normalizeHost(String input) {
        String value = input == null ? "" : input.trim();
        if (value.startsWith("https://")) {
            value = value.substring("https://".length());
        } else if (value.startsWith("http://")) {
            value = value.substring("http://".length());
        }
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        return value.trim();
    }

    public void getWeather(City city, WeatherCallback callback) {
        executor.execute(() -> {
            try {
                if (city.lat.isEmpty() || city.lon.isEmpty()) {
                    throw new ApiException("城市数据来自旧版本，请在城市管理中重新选择一次该城市。");
                }
                WeatherData result = new WeatherData();
                String coordinates = encode(city.lat) + "/" + encode(city.lon);
                JSONObject current = requestJson("/weather/v1/current/" + coordinates);
                JSONObject daily = requestJson("/weather/v1/daily/" + coordinates);

                result.obsTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.CHINA)
                        .format(new Date());
                result.temp = integerValue(current.optJSONObject("temperature"));
                result.feelsLike = integerValue(current.optJSONObject("feelsLike"));
                JSONObject condition = current.optJSONObject("condition");
                result.text = condition == null ? "未知" : condition.optString("text", "未知");
                JSONObject wind = current.optJSONObject("wind");
                if (wind != null) {
                    JSONObject direction = wind.optJSONObject("direction");
                    result.windDir = direction == null
                            ? "--"
                            : compassToChinese(direction.optString("compass", ""));
                    result.windScale = String.valueOf(wind.optInt("scale", 0));
                    if ("0".equals(result.windScale)) {
                        result.windScale = "--";
                    }
                }
                result.humidity = percentValue(current.optDouble("humidity", Double.NaN));
                result.pressure = decimalValue(current.optJSONObject("pressure"));
                result.vis = visibilityValue(current.optJSONObject("visibility"));
                result.cloud = percentValue(current.optDouble("cloudCover", Double.NaN));

                List<WeatherData.ForecastDay> days = new ArrayList<>();
                JSONArray array = daily.optJSONArray("days");
                if (array != null) {
                    for (int i = 0; i < Math.min(3, array.length()); i++) {
                        JSONObject item = array.getJSONObject(i);
                        WeatherData.ForecastDay day = new WeatherData.ForecastDay();
                        day.date = localDate(item.optString("forecastStartTime"), city.utcOffset);
                        day.tempMax = integerValue(item.optJSONObject("temperatureMax"));
                        day.tempMin = integerValue(item.optJSONObject("temperatureMin"));
                        day.uvIndex = numericText(item.optDouble("uvIndexMax", Double.NaN));

                        JSONObject daytime = item.optJSONObject("daytime");
                        JSONObject nighttime = item.optJSONObject("nighttime");
                        if (daytime != null) {
                            JSONObject dayCondition = daytime.optJSONObject("condition");
                            day.textDay = dayCondition == null ? "" : dayCondition.optString("text", "");
                            JSONObject rain = daytime.optJSONObject("precipitation");
                            if (rain != null) {
                                day.precip = decimalValue(rain.optJSONObject("amount"));
                            }
                            JSONObject dayWind = daytime.optJSONObject("wind");
                            if (dayWind != null) {
                                JSONObject direction = dayWind.optJSONObject("direction");
                                day.windDirDay = direction == null
                                        ? "--"
                                        : compassToChinese(direction.optString("compass", ""));
                                day.windScaleDay = String.valueOf(dayWind.optInt("scale", 0));
                            }
                        }
                        if (nighttime != null) {
                            JSONObject nightCondition = nighttime.optJSONObject("condition");
                            day.textNight = nightCondition == null
                                    ? ""
                                    : nightCondition.optString("text", "");
                        }
                        days.add(day);
                    }
                }
                result.forecast = days;
                callback.onSuccess(result);
            } catch (ApiException | JSONException | IOException error) {
                callback.onError(readableError(error));
            }
        });
    }

    public void searchCities(String query, CitiesCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject response = requestJson(
                        "/geo/v2/city/lookup?location=" + encode(query)
                                + "&number=20&lang=zh"
                );
                List<City> cities = new ArrayList<>();
                JSONArray locations = response.optJSONArray("location");
                if (locations != null) {
                    for (int i = 0; i < locations.length(); i++) {
                        City city = City.fromApi(locations.getJSONObject(i));
                        if (!city.id.isEmpty() && !city.name.isEmpty()) {
                            cities.add(city);
                        }
                    }
                }
                callback.onSuccess(cities);
            } catch (ApiException | JSONException | IOException error) {
                callback.onError(readableError(error));
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private JSONObject requestJson(String path) throws IOException, JSONException, ApiException {
        if (host.isEmpty()) {
            throw new ApiException("请先填写 QWeather API Host");
        }
        if (apiKey.isEmpty()) {
            throw new ApiException("请先填写 QWeather API Key");
        }

        URL url = new URL("https://" + host + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(18000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-QW-Api-Key", apiKey);
        connection.setRequestProperty("User-Agent", "WeatherInk/1.0");

        int httpCode = connection.getResponseCode();
        InputStream stream = httpCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String body = readBody(stream);
        connection.disconnect();

        if (body.isEmpty()) {
            throw new ApiException("服务器返回了空响应（HTTP " + httpCode + "）");
        }

        JSONObject response;
        try {
            response = new JSONObject(body);
        } catch (JSONException error) {
            throw new ApiException("服务器响应格式异常（HTTP " + httpCode + "）");
        }

        String code = response.optString("code", "");
        if (!code.isEmpty() && !"200".equals(code)) {
            throw new ApiException(errorCodeMessage(code, httpCode));
        }
        if (code.isEmpty() && !response.has("metadata")) {
            throw new ApiException("服务器响应缺少天气数据。");
        }
        return response;
    }

    private static String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }
        return result.toString();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String integerValue(JSONObject value) {
        if (value == null || !value.has("value")) {
            return "--";
        }
        double number = value.optDouble("value", Double.NaN);
        return Double.isNaN(number) ? "--" : String.valueOf(Math.round(number));
    }

    private static String decimalValue(JSONObject value) {
        if (value == null || !value.has("value")) {
            return "--";
        }
        double number = value.optDouble("value", Double.NaN);
        if (Double.isNaN(number)) {
            return "--";
        }
        if (Math.abs(number - Math.rint(number)) < 0.05) {
            return String.valueOf(Math.round(number));
        }
        return String.format(Locale.CHINA, "%.1f", number);
    }

    private static String visibilityValue(JSONObject value) {
        if (value == null || !value.has("value")) {
            return "--";
        }
        double meters = value.optDouble("value", Double.NaN);
        if (Double.isNaN(meters)) {
            return "--";
        }
        return String.format(Locale.CHINA, "%.1f", meters / 1000.0);
    }

    private static String percentValue(double fraction) {
        return Double.isNaN(fraction) ? "--" : String.valueOf(Math.round(fraction * 100));
    }

    private static String numericText(double number) {
        if (Double.isNaN(number)) {
            return "--";
        }
        return Math.abs(number - Math.rint(number)) < 0.05
                ? String.valueOf(Math.round(number))
                : String.format(Locale.CHINA, "%.1f", number);
    }

    private static String compassToChinese(String compass) {
        String value = compass == null ? "" : compass.toLowerCase(Locale.US);
        switch (value) {
            case "n":
                return "北";
            case "nne":
                return "北东北";
            case "ne":
                return "东北";
            case "ene":
                return "东东北";
            case "e":
                return "东";
            case "ese":
                return "东东南";
            case "se":
                return "东南";
            case "sse":
                return "南东南";
            case "s":
                return "南";
            case "ssw":
                return "南西南";
            case "sw":
                return "西南";
            case "wsw":
                return "西西南";
            case "w":
                return "西";
            case "wnw":
                return "西西北";
            case "nw":
                return "西北";
            case "nnw":
                return "北西北";
            default:
                return "--";
        }
    }

    private static String localDate(String utcTimestamp, String utcOffset) {
        if (utcTimestamp == null || utcTimestamp.isEmpty()) {
            return "";
        }
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US);
            input.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = input.parse(utcTimestamp);
            if (date == null) {
                return utcTimestamp.length() >= 10 ? utcTimestamp.substring(0, 10) : "";
            }
            long offsetMillis = parseOffsetMillis(utcOffset);
            SimpleDateFormat output = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            output.setTimeZone(TimeZone.getTimeZone("UTC"));
            return output.format(new Date(date.getTime() + offsetMillis));
        } catch (ParseException error) {
            return utcTimestamp.length() >= 10 ? utcTimestamp.substring(0, 10) : "";
        }
    }

    private static long parseOffsetMillis(String value) {
        if (value == null || value.length() < 5) {
            return 0;
        }
        try {
            int sign = value.charAt(0) == '-' ? -1 : 1;
            int hours = Integer.parseInt(value.substring(1, 3));
            int minutes = Integer.parseInt(value.substring(3, 5));
            return sign * (hours * 60L + minutes) * 60_000L;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String readableError(Exception error) {
        if (error instanceof ApiException) {
            return error.getMessage();
        }
        if (error instanceof IOException) {
            return "无法连接 QWeather。请检查网络、API Host 和设备时间。";
        }
        return "天气数据解析失败，请稍后重试。";
    }

    private static String errorCodeMessage(String code, int httpCode) {
        if ("204".equals(code)) {
            return "QWeather 暂无该城市的数据。";
        }
        if ("400".equals(code)) {
            return "请求参数有误，请检查城市或 API 配置。";
        }
        if ("401".equals(code) || httpCode == 401) {
            return "认证失败：请检查 API Key 是否正确。";
        }
        if ("402".equals(code)) {
            return "QWeather 免费额度已用完，请查看控制台用量。";
        }
        if ("403".equals(code) || httpCode == 403) {
            return "当前 QWeather 订阅无权访问此接口，请检查控制台权限。";
        }
        if ("404".equals(code) || httpCode == 404) {
            return "未找到城市或接口地址。请检查 Location ID 与 API Host。";
        }
        if ("429".equals(code) || httpCode == 429) {
            return "请求过于频繁，请稍后手动刷新。";
        }
        if (httpCode >= 500) {
            return "QWeather 服务暂时不可用，请稍后重试。";
        }
        return "QWeather 返回错误：" + (code.isEmpty() ? "HTTP " + httpCode : code);
    }

    private static final class ApiException extends Exception {
        ApiException(String message) {
            super(message);
        }
    }
}

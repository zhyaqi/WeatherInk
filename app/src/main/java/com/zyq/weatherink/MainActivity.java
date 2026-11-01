package com.zyq.weatherink;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends InkActivity {
    private TextView cityName;
    private TextView cityRegion;
    private TextView updatedTime;
    private TextView statusText;
    private TextView conditionGlyph;
    private TextView currentTemperature;
    private TextView currentCondition;
    private TextView currentRange;
    private LinearLayout onboardingCard;
    private LinearLayout weatherContent;
    private LinearLayout detailsContainer;
    private LinearLayout forecastContainer;
    private Button refreshButton;

    private QWeatherClient client;
    private String clientHost = "";
    private String clientKey = "";
    private boolean requestPending;
    private boolean autoRefreshOnResume;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cityName = findViewById(R.id.cityName);
        cityRegion = findViewById(R.id.cityRegion);
        updatedTime = findViewById(R.id.updatedTime);
        statusText = findViewById(R.id.statusText);
        conditionGlyph = findViewById(R.id.conditionGlyph);
        currentTemperature = findViewById(R.id.currentTemperature);
        currentCondition = findViewById(R.id.currentCondition);
        currentRange = findViewById(R.id.currentRange);
        onboardingCard = findViewById(R.id.onboardingCard);
        weatherContent = findViewById(R.id.weatherContent);
        detailsContainer = findViewById(R.id.detailsContainer);
        forecastContainer = findViewById(R.id.forecastContainer);
        refreshButton = findViewById(R.id.refreshButton);

        findViewById(R.id.settingsButton).setOnClickListener(view -> openSettings());
        findViewById(R.id.cityButton).setOnClickListener(view -> openCities());
        findViewById(R.id.onboardingSettingsButton).setOnClickListener(view -> openSettings());
        findViewById(R.id.onboardingCityButton).setOnClickListener(view -> openCities());
        refreshButton.setOnClickListener(view -> refreshWeather());
    }

    @Override
    protected void onStart() {
        super.onStart();
        autoRefreshOnResume = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean shouldAutoRefresh = autoRefreshOnResume;
        autoRefreshOnResume = false;
        rebuildClient();
        City city = Preferences.getCurrentCity(this);
        boolean hasApi = Preferences.hasApiSettings(this);

        if (city == null) {
            cityName.setText("尚未选择城市");
            cityRegion.setText("手动添加城市，无需定位");
        } else {
            cityName.setText(city.name);
            cityRegion.setText(city.regionText());
        }

        if (city == null || !hasApi) {
            onboardingCard.setVisibility(View.VISIBLE);
            weatherContent.setVisibility(View.GONE);
            refreshButton.setVisibility(View.GONE);
            refreshButton.setEnabled(false);
            statusText.setText(hasApi
                    ? "下一步：手动搜索并添加城市"
                    : "注册 QWeather 后，在控制台中获取 API Host 与 API Key");
            return;
        }

        onboardingCard.setVisibility(View.GONE);
        weatherContent.setVisibility(View.VISIBLE);
        refreshButton.setVisibility(View.VISIBLE);
        refreshButton.setEnabled(!requestPending);

        WeatherData cached = Preferences.loadWeather(this, city);
        if (cached != null) {
            renderWeather(city, cached, true);
        } else {
            showPlaceholder();
            statusText.setText("已选择城市；点击“刷新天气”获取最新数据");
        }

        if (shouldAutoRefresh) {
            refreshWeather(true);
        }
    }

    private void rebuildClient() {
        String host = Preferences.getApiHost(this);
        String key = Preferences.getApiKey(this);
        if (client != null && host.equals(clientHost) && key.equals(clientKey)) {
            return;
        }
        if (client != null) {
            client.shutdown();
        }
        clientHost = host;
        clientKey = key;
        requestPending = false;
        client = new QWeatherClient(host, key);
    }

    private void refreshWeather() {
        refreshWeather(false);
    }

    private void refreshWeather(boolean automatic) {
        City city = Preferences.getCurrentCity(this);
        if (city == null) {
            if (!automatic) {
                openCities();
            }
            return;
        }
        if (!Preferences.hasApiSettings(this)) {
            if (!automatic) {
                openSettings();
            }
            return;
        }
        if (requestPending) {
            return;
        }

        requestPending = true;
        refreshButton.setEnabled(false);
        refreshButton.setText("正在获取天气…");
        statusText.setText(automatic
                ? "打开应用，正在自动更新天气…"
                : "正在通过 QWeather 获取数据");

        client.getWeather(city, new QWeatherClient.WeatherCallback() {
            @Override
            public void onSuccess(WeatherData data) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    requestPending = false;
                    refreshButton.setEnabled(true);
                    refreshButton.setText(R.string.refresh);
                    Preferences.saveWeather(MainActivity.this, city, data);
                    renderWeather(city, data, false);
                    statusText.setText("已更新 · 应用不会后台自动刷新");
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    requestPending = false;
                    refreshButton.setEnabled(true);
                    refreshButton.setText(R.string.refresh);
                    statusText.setText(message);
                    if (automatic) {
                        statusText.setText("自动刷新失败：" + message);
                    } else {
                        showError(message);
                    }
                });
            }
        });
    }

    private void renderWeather(City city, WeatherData data, boolean cached) {
        currentTemperature.setText(data.temp + "°");
        currentCondition.setText(data.text);
        conditionGlyph.setText(conditionGlyph(data.text));

        String range = "体感 " + data.feelsLike + "°";
        if (!data.forecast.isEmpty()) {
            WeatherData.ForecastDay today = data.forecast.get(0);
            range = "最高 " + today.tempMax + "°  ·  最低 " + today.tempMin + "°  ·  " + range;
        }
        currentRange.setText(range);
        updatedTime.setText("观测时间  " + formatObsTime(data.obsTime));
        populateDetails(data);
        populateForecast(data.forecast);
        cityName.setText(city.name);
        cityRegion.setText(city.regionText());

        if (cached) {
            statusText.setText("当前显示本机缓存，点击“刷新天气”更新");
        }
    }

    private void populateDetails(WeatherData data) {
        detailsContainer.removeAllViews();
        addDetailRow("湿度", data.humidity + "%");
        addDetailRow("风向风力", data.windDir + "  " + data.windScale + " 级");
        addDetailRow("气压", data.pressure + " hPa");
        addDetailRow("能见度", data.vis + " km");
        addDetailRow("云量", data.cloud + "%");
        if (!data.forecast.isEmpty()) {
            WeatherData.ForecastDay today = data.forecast.get(0);
            addDetailRow("降水 / 紫外线", today.precip + " mm  /  " + today.uvIndex);
        }
    }

    private void addDetailRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(11), 0, dp(11));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(getColor(R.color.ink_muted));
        labelView.setTextSize(14);
        labelView.setTypeface(Typeface.SANS_SERIF);
        row.addView(labelView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(getColor(R.color.ink));
        valueView.setTextSize(15);
        valueView.setGravity(Gravity.RIGHT);
        valueView.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        row.addView(valueView);

        detailsContainer.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.wash));
        detailsContainer.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private void populateForecast(List<WeatherData.ForecastDay> forecast) {
        forecastContainer.removeAllViews();
        for (int i = 0; i < forecast.size(); i++) {
            WeatherData.ForecastDay day = forecast.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_row);
            row.setPadding(dp(13), dp(12), dp(13), dp(12));

            TextView date = new TextView(this);
            date.setText(i == 0 ? "今天" : i == 1 ? "明天" : shortDate(day.date));
            date.setTextColor(getColor(R.color.ink));
            date.setTextSize(15);
            date.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
            row.addView(date, new LinearLayout.LayoutParams(dp(62), LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView condition = new TextView(this);
            String conditionText = day.textDay;
            if (!day.textNight.isEmpty() && !day.textNight.equals(day.textDay)) {
                conditionText += " / " + day.textNight;
            }
            condition.setText(conditionText);
            condition.setTextColor(getColor(R.color.ink_muted));
            condition.setTextSize(14);
            row.addView(condition, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView temperature = new TextView(this);
            temperature.setText(day.tempMin + "° / " + day.tempMax + "°");
            temperature.setTextColor(getColor(R.color.ink));
            temperature.setTextSize(15);
            temperature.setGravity(Gravity.RIGHT);
            temperature.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
            row.addView(temperature);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = i == forecast.size() - 1 ? 0 : dp(7);
            forecastContainer.addView(row, rowParams);
        }
    }

    private void showPlaceholder() {
        currentTemperature.setText("--°");
        currentCondition.setText("等待刷新");
        conditionGlyph.setText("墨");
        currentRange.setText("尚无天气数据");
        updatedTime.setText("尚未获取天气");
        detailsContainer.removeAllViews();
        forecastContainer.removeAllViews();
    }

    private void showError(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("刷新失败")
                .setMessage(message + "\n\n不会自动重试，可检查设置后手动刷新。")
                .setNegativeButton("关闭", null);
        if (message.contains("API") || message.contains("认证") || message.contains("权限")) {
            builder.setPositiveButton("检查设置", (dialog, which) -> openSettings());
        }
        builder.show();
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void openCities() {
        startActivity(new Intent(this, CityActivity.class));
    }

    private String conditionGlyph(String condition) {
        if (condition.contains("雷")) return "雷";
        if (condition.contains("雪")) return "雪";
        if (condition.contains("雨")) return "雨";
        if (condition.contains("雾") || condition.contains("霾")) return "雾";
        if (condition.contains("沙") || condition.contains("尘")) return "尘";
        if (condition.contains("风")) return "风";
        if (condition.contains("阴")) return "阴";
        if (condition.contains("云")) return "云";
        if (condition.contains("晴")) return "晴";
        return "天";
    }

    private String formatObsTime(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "未知";
        }
        String value = raw.replace('T', ' ');
        int zone = value.indexOf('+');
        if (zone > 0) {
            value = value.substring(0, zone);
        }
        if (value.length() >= 16) {
            return value.substring(0, 16);
        }
        return value;
    }

    private String shortDate(String isoDate) {
        if (isoDate != null && isoDate.length() >= 10) {
            try {
                Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(isoDate);
                return new SimpleDateFormat("MM月dd日", Locale.CHINA).format(date);
            } catch (Exception ignored) {
                return isoDate.substring(5);
            }
        }
        return isoDate == null ? "" : isoDate;
    }

    @Override
    protected void onDestroy() {
        if (client != null) {
            client.shutdown();
        }
        super.onDestroy();
    }
}

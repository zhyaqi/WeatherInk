package com.zyq.weatherink;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class CityActivity extends InkActivity {
    private EditText searchInput;
    private Button searchButton;
    private TextView statusText;
    private TextView resultsTitle;
    private LinearLayout savedCitiesContainer;
    private LinearLayout resultsContainer;

    private QWeatherClient client;
    private boolean requestPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_city);

        searchInput = findViewById(R.id.searchInput);
        searchButton = findViewById(R.id.searchButton);
        statusText = findViewById(R.id.statusText);
        resultsTitle = findViewById(R.id.resultsTitle);
        savedCitiesContainer = findViewById(R.id.savedCitiesContainer);
        resultsContainer = findViewById(R.id.resultsContainer);

        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        findViewById(R.id.settingsButton).setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class)));
        searchButton.setOnClickListener(view -> search());
        searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (client != null) {
            client.shutdown();
        }
        client = new QWeatherClient(Preferences.getApiHost(this), Preferences.getApiKey(this));
        rebuildSavedCities();
        if (!Preferences.hasApiSettings(this)) {
            statusText.setText("尚未配置 API。天气与城市搜索需要 QWeather 服务。");
        } else if (statusText.getText().length() == 0
                || statusText.getText().toString().startsWith("尚未配置 API")) {
            statusText.setText("输入城市名或 Location ID 后点击搜索");
        }
    }

    private void search() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            statusText.setText("请输入城市名称或 Location ID");
            return;
        }
        if (!Preferences.hasApiSettings(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("需要配置 QWeather")
                    .setMessage("请先注册和风天气开发者账号，并在控制台获取专属 API Host 与 API Key。")
                    .setNegativeButton("稍后", null)
                    .setPositiveButton("去配置", (dialog, which) ->
                            startActivity(new Intent(this, SettingsActivity.class)))
                    .show();
            return;
        }
        if (requestPending) {
            return;
        }

        searchButton.setEnabled(false);
        searchButton.setText("搜索中");
        statusText.setText("正在查询城市…");
        requestPending = true;
        resultsTitle.setVisibility(android.view.View.GONE);
        resultsContainer.removeAllViews();

        client.searchCities(query, new QWeatherClient.CitiesCallback() {
            @Override
            public void onSuccess(List<City> cities) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    finishRequest();
                    showResults(cities);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    finishRequest();
                    statusText.setText(message);
                });
            }
        });
    }

    private void finishRequest() {
        requestPending = false;
        searchButton.setEnabled(true);
        searchButton.setText(R.string.search);
    }

    private void showResults(List<City> cities) {
        resultsContainer.removeAllViews();
        if (cities.isEmpty()) {
            resultsTitle.setVisibility(android.view.View.GONE);
            statusText.setText("没有找到匹配城市，请尝试更完整的名称或 Location ID。");
            return;
        }

        resultsTitle.setVisibility(android.view.View.VISIBLE);
        statusText.setText("找到 " + cities.size() + " 个匹配结果，点击即可添加");
        for (City city : cities) {
            TextView row = makeCityRow(city, false, false);
            row.setOnClickListener(view -> selectAndFinish(city));
            addRow(resultsContainer, row);
        }
    }

    private void rebuildSavedCities() {
        savedCitiesContainer.removeAllViews();
        List<City> cities = Preferences.loadCities(this);
        City current = Preferences.getCurrentCity(this);
        if (cities.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有城市，请在上方搜索添加。");
            empty.setTextColor(getColor(R.color.ink_muted));
            empty.setTextSize(14);
            empty.setPadding(0, dp(10), 0, dp(10));
            savedCitiesContainer.addView(empty);
            return;
        }

        for (City city : cities) {
            boolean isCurrent = current != null && current.id.equals(city.id);
            TextView row = makeCityRow(city, true, isCurrent);
            row.setOnClickListener(view -> {
                Preferences.setCurrentCity(this, city);
                finish();
            });
            row.setOnLongClickListener(view -> {
                confirmDelete(city);
                return true;
            });
            addRow(savedCitiesContainer, row);
        }
    }

    private TextView makeCityRow(City city, boolean saved, boolean current) {
        TextView row = new TextView(this);
        row.setBackgroundResource(R.drawable.bg_row);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinHeight(dp(64));
        row.setPadding(dp(14), dp(9), dp(14), dp(9));
        row.setTextColor(getColor(R.color.ink));
        row.setTextSize(16);
        row.setTypeface(Typeface.SANS_SERIF, current ? Typeface.BOLD : Typeface.NORMAL);

        String prefix = current ? "当前  ·  " : "";
        String suffix = saved ? "" : "    →";
        row.setText(prefix + city.name + "\n" + city.regionText() + suffix);
        row.setLineSpacing(0, 1.05f);
        return row;
    }

    private void addRow(LinearLayout container, TextView row) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(7);
        container.addView(row, params);
    }

    private void selectAndFinish(City city) {
        Preferences.addCity(this, city);
        statusText.setText("已选择 " + city.name);
        finish();
    }

    private void confirmDelete(City city) {
        new AlertDialog.Builder(this)
                .setTitle("删除城市")
                .setMessage("从已保存城市中删除“" + city.name + "”？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    List<City> cities = Preferences.loadCities(this);
                    cities.remove(city);
                    Preferences.saveCities(this, cities);
                    City current = Preferences.getCurrentCity(this);
                    if (current != null && current.id.equals(city.id)) {
                        Preferences.setCurrentCity(this, cities.isEmpty() ? null : cities.get(0));
                    }
                    rebuildSavedCities();
                    statusText.setText("已删除 " + city.name);
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        if (client != null) {
            client.shutdown();
        }
        super.onDestroy();
    }
}

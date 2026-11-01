package com.zyq.weatherink;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends InkActivity {
    private EditText hostInput;
    private EditText apiKeyInput;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        hostInput = findViewById(R.id.hostInput);
        apiKeyInput = findViewById(R.id.apiKeyInput);
        statusText = findViewById(R.id.statusText);

        hostInput.setText(Preferences.getApiHost(this));
        apiKeyInput.setText(Preferences.getApiKey(this));

        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        findViewById(R.id.saveButton).setOnClickListener(view -> save());
        findViewById(R.id.openConsoleButton).setOnClickListener(view -> openConsole());
        findViewById(R.id.clearCacheButton).setOnClickListener(view -> confirmClearCache());
    }

    private void save() {
        String host = QWeatherClient.normalizeHost(hostInput.getText().toString());
        String key = apiKeyInput.getText().toString().trim();
        if (host.isEmpty()) {
            hostInput.setError("请输入 QWeather 控制台中的 API Host");
            hostInput.requestFocus();
            return;
        }
        if (!host.contains(".") || host.contains(" ")) {
            hostInput.setError("请输入有效域名，例如 abcxyz.qweatherapi.com");
            hostInput.requestFocus();
            return;
        }
        if (key.isEmpty()) {
            apiKeyInput.setError("请输入 API Key");
            apiKeyInput.requestFocus();
            return;
        }

        hostInput.setText(host);
        Preferences.saveApiSettings(this, host, key);
        statusText.setText("已保存到本机。返回后添加城市，再手动刷新天气。");
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
    }

    private void openConsole() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://console.qweather.com/")));
        } catch (ActivityNotFoundException error) {
            statusText.setText("设备没有可用浏览器，请在其他设备访问 console.qweather.com");
        }
    }

    private void confirmClearCache() {
        new AlertDialog.Builder(this)
                .setTitle("清除天气缓存")
                .setMessage("不会删除城市、API Host 或 API Key。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (dialog, which) -> {
                    Preferences.clearWeatherCache(this);
                    statusText.setText("天气缓存已清除");
                })
                .show();
    }
}

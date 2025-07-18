package com.have_no_eyes_deer.bleawsgateway;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AwsSettingsActivity extends AppCompatActivity {
    private EditText etEndpoint, etKey, etCredentials;
    private Button btnSave;

    private static final String PREFS_NAME       = "AwsPrefs";
    private static final String KEY_ENDPOINT     = "endpoint";
    private static final String KEY_KEY          = "key";
    private static final String KEY_CREDENTIALS  = "credentials";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aws_settings);

        // 绑定控件
        etEndpoint    = findViewById(R.id.etEndpoint);
        etKey         = findViewById(R.id.etKey);
        etCredentials = findViewById(R.id.etCredentials);
        btnSave       = findViewById(R.id.btnSaveSettings);

        // 读取已有配置
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        etEndpoint.setText(   prefs.getString(KEY_ENDPOINT, "")   );
        etKey.setText(        prefs.getString(KEY_KEY, "")        );
        etCredentials.setText(prefs.getString(KEY_CREDENTIALS, ""));

        // 保存并退出
        btnSave.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_ENDPOINT,    etEndpoint.getText().toString().trim());
            editor.putString(KEY_KEY,         etKey.getText().toString().trim());
            editor.putString(KEY_CREDENTIALS, etCredentials.getText().toString().trim());
            editor.apply();
            Toast.makeText(this, "AWS IoT 配置已保存", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}

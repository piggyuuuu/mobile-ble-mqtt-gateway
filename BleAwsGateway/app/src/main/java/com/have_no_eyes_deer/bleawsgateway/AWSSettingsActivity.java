package com.have_no_eyes_deer.bleawsgateway;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

public class AWSSettingsActivity extends AppCompatActivity {
    private EditText etEndpoint, etClientId, etCertPath, etKeyPath, etCognitoId;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aws_settings);

        etEndpoint = findViewById(R.id.etEndpoint);
        etClientId = findViewById(R.id.etClientId);
        etCertPath = findViewById(R.id.etCertPath);
        etKeyPath  = findViewById(R.id.etKeyPath);
        etCognitoId= findViewById(R.id.etCognitoId);
        btnSave    = findViewById(R.id.btnSaveAwsSettings);

        SharedPreferences prefs = getSharedPreferences("aws_iot", MODE_PRIVATE);
        etEndpoint.setText(prefs.getString("endpoint", ""));
        etClientId.setText(prefs.getString("clientId", ""));
        etCertPath.setText(prefs.getString("certPath", ""));
        etKeyPath.setText(prefs.getString("keyPath", ""));
        etCognitoId.setText(prefs.getString("cognitoId", ""));

        btnSave.setOnClickListener(v -> {
            prefs.edit()
                    .putString("endpoint", etEndpoint.getText().toString())
                    .putString("clientId", etClientId.getText().toString())
                    .putString("certPath", etCertPath.getText().toString())
                    .putString("keyPath", etKeyPath.getText().toString())
                    .putString("cognitoId", etCognitoId.getText().toString())
                    .apply();
            // 返回主界面
            setResult(RESULT_OK);
            finish();
        });
    }
}

package com.have_no_eyes_deer.bleawsgateway;

import android.content.Intent;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class AWSSettingsActivity extends AppCompatActivity {
    private EditText etEndpoint;
    private TextView tvKeyPath, tvCredPath;
    private Button btnSelectKey, btnSelectCred, btnSave;

    private ActivityResultLauncher<String[]> keyPicker;
    private ActivityResultLauncher<String[]> credPicker;

    private Uri keyUri, credUri;

    private static final String PREFS_NAME      = "AwsPrefs";
    private static final String KEY_ENDPOINT    = "endpoint";
    private static final String KEY_KEY_URI     = "keyUri";
    private static final String KEY_CRED_URI    = "credUri";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aws_settings);

        etEndpoint   = findViewById(R.id.etEndpoint);
        tvKeyPath    = findViewById(R.id.tvKeyPath);
        tvCredPath   = findViewById(R.id.tvCredPath);
        btnSelectKey = findViewById(R.id.btnSelectKey);
        btnSelectCred= findViewById(R.id.btnSelectCred);
        btnSave      = findViewById(R.id.btnSaveSettings);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        etEndpoint.setText(prefs.getString(KEY_ENDPOINT, ""));
        String savedKeyUri  = prefs.getString(KEY_KEY_URI, null);
        String savedCredUri = prefs.getString(KEY_CRED_URI, null);
        if (savedKeyUri  != null) { keyUri  = Uri.parse(savedKeyUri);  tvKeyPath.setText(getFileName(keyUri)); }
        if (savedCredUri != null) { credUri = Uri.parse(savedCredUri); tvCredPath.setText(getFileName(credUri)); }

        // 注册文件选择 Launcher
        keyPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        keyUri = uri;
                        tvKeyPath.setText(getFileName(uri));
                    }
                }
        );
        credPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        credUri = uri;
                        tvCredPath.setText(getFileName(uri));
                    }
                }
        );

        btnSelectKey.setOnClickListener(v ->
                keyPicker.launch(new String[]{"*/*"})
        );
        btnSelectCred.setOnClickListener(v ->
                credPicker.launch(new String[]{"*/*"})
        );

        btnSave.setOnClickListener(v -> {
            if (etEndpoint.getText().toString().trim().isEmpty() ||
                    keyUri == null || credUri == null) {
                Toast.makeText(this,
                        "Please fill in Endpoint and select Key, Credentials files",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_ENDPOINT, etEndpoint.getText().toString().trim());
            editor.putString(KEY_KEY_URI,  keyUri.toString());
            editor.putString(KEY_CRED_URI, credUri.toString());
            editor.apply();
            Toast.makeText(this, "AWS IoT configuration saved", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    /** Read display name from Uri */
    private String getFileName(Uri uri) {
        String result = null;
        ContentResolver cr = getContentResolver();
        try (Cursor cursor = cr.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                result = cursor.getString(idx);
            }
        }
        return result != null ? result : uri.getLastPathSegment();
    }
}

package com.example.securenote.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.securenote.R;
import com.example.securenote.data.NoteRepository;
import com.example.securenote.util.PasswordUtil;

public class ChangePasswordActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "MasterLockPrefs";
    private static final String KEY_MASTER_HASH = "master_pin_hash";

    private NoteRepository noteRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        // Get repository instance
        noteRepository = NoteRepository.getInstance(getApplicationContext());

        EditText etCurrent = findViewById(R.id.etCurrentPin);
        EditText etNew = findViewById(R.id.etNewPin);
        EditText etConfirm = findViewById(R.id.etConfirmPin);
        Button btnSave = findViewById(R.id.btnSavePin);
        Button btnCancel = findViewById(R.id.btnCancel);

        // Save Button Click
        btnSave.setOnClickListener(v -> {
            String current = etCurrent.getText().toString().trim();
            String newPin = etNew.getText().toString().trim();
            String confirm = etConfirm.getText().toString().trim();

            if (current.isEmpty() || newPin.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (current.length() != 4 || newPin.length() != 4) {
                Toast.makeText(this, "PIN must be exactly 4 digits", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPin.equals(confirm)) {
                Toast.makeText(this, "New PIN does not match confirmation", Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String storedHash = prefs.getString(KEY_MASTER_HASH, null);

            if (storedHash == null) {
                Toast.makeText(this, "No master PIN set", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean currentOk = PasswordUtil.verifyLockPassword(current, storedHash);
            if (!currentOk) {
                Toast.makeText(this, "Current PIN is incorrect", Toast.LENGTH_SHORT).show();
                return;
            }

            // 1) Hash + save new PIN for future unlocks
            String newHash = PasswordUtil.hashLockPassword(newPin);
            prefs.edit().putString(KEY_MASTER_HASH, newHash).apply();

            Toast.makeText(this, "PIN changed. Notes are being re-encrypted.", Toast.LENGTH_SHORT).show();
            finish();
        });

        // Cancel Button Click - Close activity and return to previous screen
        btnCancel.setOnClickListener(v -> {
            finish();
        });
    }
}
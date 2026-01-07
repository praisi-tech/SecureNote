package com.example.securenote.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.widget.ImageView;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;

import androidx.appcompat.app.AppCompatActivity;

import com.example.securenote.R;
import com.example.securenote.util.PasswordUtil;

public class LockActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "MasterLockPrefs";
    private static final String MASTER_HASH_KEY = "master_pin_hash";

    private SharedPreferences prefs;
    private EditText etPinInput;
    private TextView tvLockTitle;
    private Button btnSubmitPin;
    private TextView tvForgotPin;

    private View dot1, dot2, dot3, dot4;
    private TransitionDrawable t1, t2, t3, t4;

    private boolean d1 = false, d2 = false, d3 = false, d4 = false;

    private TextView btn1, btn2, btn3, btn4, btn5, btn6, btn7, btn8, btn9, btn0;
    private ImageView btnDelete;

    private StringBuilder pinBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_master_lock);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        etPinInput = findViewById(R.id.etPinInput);
        tvLockTitle = findViewById(R.id.tvLockTitle);
        btnSubmitPin = findViewById(R.id.btnSubmitPin);
        tvForgotPin = findViewById(R.id.tvForgotPin);

        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        dot3 = findViewById(R.id.dot3);
        dot4 = findViewById(R.id.dot4);

        t1 = (TransitionDrawable) dot1.getBackground();
        t2 = (TransitionDrawable) dot2.getBackground();
        t3 = (TransitionDrawable) dot3.getBackground();
        t4 = (TransitionDrawable) dot4.getBackground();

        t1.resetTransition();
        t2.resetTransition();
        t3.resetTransition();
        t4.resetTransition();

        btn1 = findViewById(R.id.btn1);
        btn2 = findViewById(R.id.btn2);
        btn3 = findViewById(R.id.btn3);
        btn4 = findViewById(R.id.btn4);
        btn5 = findViewById(R.id.btn5);
        btn6 = findViewById(R.id.btn6);
        btn7 = findViewById(R.id.btn7);
        btn8 = findViewById(R.id.btn8);
        btn9 = findViewById(R.id.btn9);
        btn0 = findViewById(R.id.btn0);
        btnDelete = findViewById(R.id.btnDelete);

        setupNumberPad();
        checkLockState();
    }

    private void setupNumberPad() {
        View.OnClickListener listener = v -> {
            if (pinBuilder.length() < 4) {
                TextView btn = (TextView) v;
                pinBuilder.append(btn.getText().toString());
                updatePinDots();

                if (pinBuilder.length() == 4) {
                    etPinInput.setText(pinBuilder.toString());
                    btnSubmitPin.performClick();
                }
            }
        };

        btn1.setOnClickListener(listener);
        btn2.setOnClickListener(listener);
        btn3.setOnClickListener(listener);
        btn4.setOnClickListener(listener);
        btn5.setOnClickListener(listener);
        btn6.setOnClickListener(listener);
        btn7.setOnClickListener(listener);
        btn8.setOnClickListener(listener);
        btn9.setOnClickListener(listener);
        btn0.setOnClickListener(listener);

        btnDelete.setOnClickListener(v -> {
            if (pinBuilder.length() > 0) {
                pinBuilder.deleteCharAt(pinBuilder.length() - 1);
                updatePinDots();
            }
        });
    }

    private void updatePinDots() {
        int length = pinBuilder.length();

        updateDotState(t1, length >= 1, 1);
        updateDotState(t2, length >= 2, 2);
        updateDotState(t3, length >= 3, 3);
        updateDotState(t4, length >= 4, 4);
    }

    private void updateDotState(TransitionDrawable t, boolean shouldBeFilled, int dotIndex) {
        boolean current =
                (dotIndex == 1 ? d1 :
                        dotIndex == 2 ? d2 :
                                dotIndex == 3 ? d3 :
                                        d4);

        if (current == shouldBeFilled) return;

        if (shouldBeFilled) t.startTransition(150);
        else t.resetTransition();

        switch (dotIndex) {
            case 1: d1 = shouldBeFilled; break;
            case 2: d2 = shouldBeFilled; break;
            case 3: d3 = shouldBeFilled; break;
            case 4: d4 = shouldBeFilled; break;
        }
    }

    private void resetPinInput() {
        pinBuilder.setLength(0);
        etPinInput.setText("");

        t1.resetTransition(); d1 = false;
        t2.resetTransition(); d2 = false;
        t3.resetTransition(); d3 = false;
        t4.resetTransition(); d4 = false;
    }

    private void checkLockState() {
        String storedHash = prefs.getString(MASTER_HASH_KEY, null);

        if (storedHash == null) {
            setupCreationMode();
        } else {
            setupVerificationMode(storedHash);
        }
    }

    private void setupCreationMode() {
        tvLockTitle.setText("Set Master PIN");
        tvForgotPin.setVisibility(View.GONE);

        btnSubmitPin.setOnClickListener(v -> handlePinCreation());
        resetPinInput();
    }

    private void setupVerificationMode(String storedHash) {
        tvLockTitle.setText("Verify Master PIN");
        tvForgotPin.setVisibility(View.VISIBLE);

        btnSubmitPin.setOnClickListener(v -> handlePinVerification(storedHash));
        tvForgotPin.setOnClickListener(v -> showForgotPinDialog());
        resetPinInput();
    }

    private void handlePinCreation() {
        String pin = etPinInput.getText().toString();

        if (pin.length() != 4) {
            Toast.makeText(this, "PIN must be exactly 4 digits.", Toast.LENGTH_SHORT).show();
            resetPinInput();
            return;
        }

        String hashedPin = PasswordUtil.hashLockPassword(pin);
        prefs.edit().putString(MASTER_HASH_KEY, hashedPin).apply();

        Toast.makeText(this, "Master PIN set successfully!", Toast.LENGTH_SHORT).show();
        startMainActivity();
    }

    private void handlePinVerification(String storedHash) {
        String enteredPin = etPinInput.getText().toString();

        if (enteredPin.length() != 4) {
            Toast.makeText(this, "Please enter your 4-digit PIN.", Toast.LENGTH_SHORT).show();
            resetPinInput();
            return;
        }

        if (PasswordUtil.verifyLockPassword(enteredPin, storedHash)) {
            Toast.makeText(this, "Unlocked!", Toast.LENGTH_SHORT).show();
            startMainActivity();
        } else {
            Toast.makeText(this, "Incorrect PIN. Try again.", Toast.LENGTH_SHORT).show();
            resetPinInput();
            animateWrongPin();
        }
    }

    private void animateWrongPin() {
        View dotsContainer = findViewById(R.id.llPinDots);
        if (dotsContainer != null) {
            dotsContainer.animate().translationX(-25f).setDuration(50).withEndAction(() ->
                    dotsContainer.animate().translationX(25f).setDuration(50).withEndAction(() ->
                            dotsContainer.animate().translationX(-25f).setDuration(50).withEndAction(() ->
                                    dotsContainer.animate().translationX(0f).setDuration(50).start()
                            ).start()
                    ).start()
            ).start();
        }
    }

    private void showForgotPinDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);

        builder.setTitle("Reset Master PIN?");
        builder.setMessage("If you reset the master PIN, you'll need to set a new PIN.\n\nYour notes will NOT be deleted.");

        builder.setPositiveButton("Reset PIN", (dialog, which) -> {
            prefs.edit().remove(MASTER_HASH_KEY).apply();
            Toast.makeText(this, "Master PIN cleared. Set a new PIN.", Toast.LENGTH_SHORT).show();
            setupCreationMode();
        });

        builder.setNegativeButton("Cancel", null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            // Style tombol positive (Reset PIN) dengan warna merah warning
            Button positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setTextColor(Color.parseColor("#F44336")); // Material Red
                positiveButton.setAllCaps(false);
                positiveButton.setPadding(32, 16, 32, 16);
            }

            // Style tombol negative (Cancel) dengan warna netral
            Button negativeButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                negativeButton.setTextColor(Color.parseColor("#757575")); // Material Grey
                negativeButton.setAllCaps(false);
                negativeButton.setPadding(32, 16, 32, 16);
            }
        });

        dialog.show();

        // Tambahkan rounded corners dan padding pada dialog
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.drawable.dialog_holo_light_frame);
        }
    }

    private void startMainActivity() {
        Intent intent = new Intent(LockActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
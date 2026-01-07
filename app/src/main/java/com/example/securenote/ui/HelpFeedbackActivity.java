package com.example.securenote.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.securenote.R;

public class HelpFeedbackActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_feedback);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Help & Feedback");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        Button btnSend = findViewById(R.id.btnSendFeedback);
        btnSend.setOnClickListener(v -> sendFeedbackEmail());
    }

    private void sendFeedbackEmail() {
        // Use your real email so people can actually send feedback
        String[] to = new String[] { "praisilia.productive@gmail.com" };

        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // only email apps handle this
        intent.putExtra(Intent.EXTRA_EMAIL, to);
        intent.putExtra(Intent.EXTRA_SUBJECT, "SecureNote Feedback");
        intent.putExtra(Intent.EXTRA_TEXT,
                "Hi,\n\nI have some feedback about SecureNote:\n\n");

        try {
            startActivity(Intent.createChooser(intent, "Send feedback using"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                    "No email app found on this device.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Toolbar back arrow
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

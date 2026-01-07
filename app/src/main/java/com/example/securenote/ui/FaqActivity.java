package com.example.securenote.ui;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.securenote.R;

public class FaqActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_faqs);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("FAQs");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // show back arrow
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle toolbar back button
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

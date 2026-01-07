package com.example.securenote.ui;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.example.securenote.MyApp;
import com.example.securenote.R;
import com.example.securenote.viewmodel.ProfileViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ProfileActivity extends AppCompatActivity {

    private ProfileViewModel vm;

    private ImageView imgProfile;
    private EditText etName, etBio;
    private Button btnSave, btnChangePhoto, btnRemovePhoto;

    private String avatarPath = null;
    private boolean isCleaningBadUri = false;

    private ActivityResultLauncher<String> imagePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // ------------------------------------------------------------
        // SHARED VIEWMODEL (FIX: Main & Profile use same instance)
        // ------------------------------------------------------------
        vm = new ViewModelProvider(
                (MyApp) getApplication(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())
        ).get(ProfileViewModel.class);

        imgProfile = findViewById(R.id.imgProfile);
        etName = findViewById(R.id.etProfileName);
        etBio = findViewById(R.id.etProfileBio);
        btnSave = findViewById(R.id.btnSaveProfile);
        btnChangePhoto = findViewById(R.id.btnChangePhoto);
        btnRemovePhoto = findViewById(R.id.btnRemovePhoto);

        btnChangePhoto.setVisibility(View.GONE);
        btnRemovePhoto.setVisibility(View.GONE);

        Animation animIn = AnimationUtils.loadAnimation(this, R.anim.fade_slide_in);
        Animation animOut = AnimationUtils.loadAnimation(this, R.anim.fade_slide_out);

        final boolean[] optionsVisible = {false};

        imgProfile.setOnClickListener(v -> {
            if (!optionsVisible[0]) {
                btnChangePhoto.setVisibility(View.VISIBLE);
                btnRemovePhoto.setVisibility(View.VISIBLE);

                btnChangePhoto.startAnimation(animIn);
                btnRemovePhoto.startAnimation(animIn);

            } else {
                btnChangePhoto.startAnimation(animOut);
                btnRemovePhoto.startAnimation(animOut);

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    btnChangePhoto.setVisibility(View.GONE);
                    btnRemovePhoto.setVisibility(View.GONE);
                }, 150);
            }

            optionsVisible[0] = !optionsVisible[0];
        });

        // ------------------------------------------------------------
        // IMAGE PICKER
        // ------------------------------------------------------------
        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) savePickedImageToInternalStorage(uri); }
        );

        // ------------------------------------------------------------
        // OBSERVE PROFILE DATA
        // ------------------------------------------------------------
        vm.getProfile().observe(this, p -> {
            if (p == null) return;

            etName.setText(p.displayName);
            etBio.setText(p.bio);

            if (isLegacyPickerUri(p.avatarPath)) {
                if (!isCleaningBadUri) {
                    isCleaningBadUri = true;
                    avatarPath = null;
                    imgProfile.setImageResource(R.drawable.ic_person);
                    vm.saveProfile(p.displayName, null, p.bio, null);
                }
                return;
            }

            isCleaningBadUri = false;
            avatarPath = p.avatarPath;
            loadAvatarSafely();
        });

        btnChangePhoto.setOnClickListener(v -> imagePicker.launch("image/*"));

        btnRemovePhoto.setOnClickListener(v -> {
            avatarPath = null;
            imgProfile.setImageResource(R.drawable.ic_person);
            Toast.makeText(this, "Photo removed (save to apply)", Toast.LENGTH_SHORT).show();
        });

        // ------------------------------------------------------------
        // SAVE PROFILE
        // ------------------------------------------------------------
        btnSave.setOnClickListener(v -> {
            vm.saveProfile(
                    etName.getText().toString(),
                    null, // email not used
                    etBio.getText().toString(),
                    avatarPath
            );

            Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();
        });
    }


    // ------------------------------------------------------------
    // COPY PICKED IMAGE → INTERNAL STORAGE
    // ------------------------------------------------------------
    private void savePickedImageToInternalStorage(Uri pickedUri) {
        File destFile = new File(getFilesDir(), "profile_avatar.jpg");

        try (InputStream in = getContentResolver().openInputStream(pickedUri);
             OutputStream out = new FileOutputStream(destFile)) {

            if (in == null) {
                Toast.makeText(this, "Failed to open selected image", Toast.LENGTH_SHORT).show();
                return;
            }

            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }

            avatarPath = destFile.getAbsolutePath();

            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    destFile
            );

            // ANTI CACHE FIX
            Uri freshUri = fileUri.buildUpon()
                    .appendQueryParameter("t", String.valueOf(System.currentTimeMillis()))
                    .build();

            imgProfile.setImageURI(null);
            imgProfile.setImageURI(freshUri);

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load new image", Toast.LENGTH_SHORT).show();
        }
    }


    // ------------------------------------------------------------
    // LOAD AVATAR SAFELY (ANTI-CACHE)
    // ------------------------------------------------------------
    private void loadAvatarSafely() {
        imgProfile.setImageResource(R.drawable.ic_person);

        if (avatarPath == null || avatarPath.isEmpty()) return;

        if (isLegacyPickerUri(avatarPath)) {
            avatarPath = null;
            return;
        }

        File f = new File(avatarPath);
        if (!f.exists()) return;

        try {
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    f
            );

            Uri freshUri = fileUri.buildUpon()
                    .appendQueryParameter("t", String.valueOf(System.currentTimeMillis()))
                    .build();

            imgProfile.setImageURI(null);
            imgProfile.setImageURI(freshUri);

        } catch (Exception e) {
            e.printStackTrace();
            imgProfile.setImageResource(R.drawable.ic_person);
            avatarPath = null;
        }
    }


    // ------------------------------------------------------------
    // DETECT BAD PICKER URIs
    // ------------------------------------------------------------
    private boolean isLegacyPickerUri(String path) {
        return path != null &&
                (path.startsWith("content://media/picker_get_content") ||
                        path.startsWith("content://com.android.providers.media.photopicker"));
    }
}

package com.example.securenote.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.securenote.data.AppDatabase;
import com.example.securenote.data.ProfileRepository;
import com.example.securenote.model.UserProfile;

import java.util.concurrent.Executors;

public class ProfileViewModel extends AndroidViewModel {

    private final ProfileRepository repo;
    private final MutableLiveData<UserProfile> profileLive = new MutableLiveData<>();

    public ProfileViewModel(@NonNull Application app) {
        super(app);
        repo = new ProfileRepository(app);

        // 🔥 Fix B: Cleanup BEFORE loading profile
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(app);

            // Remove legacy PhotoPicker URIs
            db.userProfileDao().cleanAllPickerUris();

            // Now load sanitized profile
            UserProfile cleanProfile = db.userProfileDao().getProfile();
            profileLive.postValue(cleanProfile);
        });
    }

    public LiveData<UserProfile> getProfile() {
        return profileLive;
    }

    public void saveProfile(String name, String email, String bio, String avatarPath) {
        Executors.newSingleThreadExecutor().execute(() -> {
            UserProfile p = profileLive.getValue();
            if (p == null) p = new UserProfile();

            p.displayName = name;
            p.email = email;
            p.bio = bio;
            p.avatarPath = avatarPath;

            repo.updateProfile(p);

            // Update LiveData
            profileLive.postValue(p);
        });
    }

}

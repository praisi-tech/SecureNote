package com.example.securenote.data;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.securenote.model.UserProfile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileRepository {

    private final UserProfileDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<UserProfile> profileLive = new MutableLiveData<>();

    public ProfileRepository(Context context) {
        dao = AppDatabase.getInstance(context).userProfileDao();
        loadProfile();
    }

    private void loadProfile() {
        executor.execute(() -> {
            UserProfile p = dao.getProfile();
            if (p == null) {
                p = new UserProfile();
                p.displayName = "User";
                p.email = "";
                p.bio = "";
                p.avatarPath = null;
                dao.insertProfile(p);
            }
            profileLive.postValue(p);
        });
    }

    public LiveData<UserProfile> getProfile() {
        return profileLive;
    }

    public void updateProfile(UserProfile profile) {
        executor.execute(() -> {
            dao.updateProfile(profile);
            loadProfile();
        });
    }
}

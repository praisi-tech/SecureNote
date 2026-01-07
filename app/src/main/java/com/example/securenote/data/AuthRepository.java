package com.example.securenote.data;

import androidx.lifecycle.MutableLiveData;

public class AuthRepository {

    private static AuthRepository instance;

    // No FirebaseUser anymore — app is offline.
    private final MutableLiveData<Boolean> isLoggedIn = new MutableLiveData<>(true);
    private final MutableLiveData<String> authError = new MutableLiveData<>();

    private AuthRepository() { }

    public static synchronized AuthRepository getInstance() {
        if (instance == null) {
            instance = new AuthRepository();
        }
        return instance;
    }

    public MutableLiveData<Boolean> getUserLiveData() {
        return isLoggedIn;
    }

    public MutableLiveData<String> getAuthError() {
        return authError;
    }

    // Stub methods — app is offline, user is always considered "logged in"
    public void login(String email, String password) {
        // No-op
        isLoggedIn.setValue(true);
    }

    public void register(String email, String password) {
        // No-op
        isLoggedIn.setValue(true);
    }

    public void logout() {
        // No-op
        isLoggedIn.setValue(false);
    }
}

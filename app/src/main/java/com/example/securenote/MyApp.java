package com.example.securenote;

import android.app.Application;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;

public class MyApp extends Application implements ViewModelStoreOwner {

    private final ViewModelStore appViewModelStore = new ViewModelStore();

    @Override
    public ViewModelStore getViewModelStore() {
        return appViewModelStore;
    }
}

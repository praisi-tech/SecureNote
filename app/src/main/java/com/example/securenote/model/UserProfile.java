package com.example.securenote.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_profile")
public class UserProfile {

    @PrimaryKey
    public int id = 1;

    public String displayName;
    public String email;
    public String bio;
    public String avatarPath;

    // Reserved for Security Module (Module 2)
    public byte[] encryptedMasterKey;
    public byte[] encryptedRecoveryKey;

    public UserProfile() {}
}

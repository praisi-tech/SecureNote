package com.example.securenote.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.securenote.model.UserProfile;

@Dao
public interface UserProfileDao {

    @Query("UPDATE user_profile SET avatarPath = NULL " +
            "WHERE avatarPath LIKE 'content://media/picker_get_content%' " +
            "OR avatarPath LIKE 'content://com.android.providers.media.photopicker%'")
    void cleanAllPickerUris();

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    UserProfile getProfile();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertProfile(UserProfile profile);

    @Update
    void updateProfile(UserProfile profile);

    @Query("UPDATE user_profile SET displayName = :name WHERE id = 1")
    void updateName(String name);

    @Query("UPDATE user_profile SET email = :email WHERE id = 1")
    void updateEmail(String email);

    @Query("UPDATE user_profile SET bio = :bio WHERE id = 1")
    void updateBio(String bio);

    @Query("UPDATE user_profile SET avatarPath = :path WHERE id = 1")
    void updateAvatar(String path);
}

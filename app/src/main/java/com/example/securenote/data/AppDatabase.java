package com.example.securenote.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.securenote.model.Attachment;
import com.example.securenote.model.Note;
import com.example.securenote.model.UserProfile;
import com.example.securenote.model.TodoItem;

@Database(
        entities = {
                Note.class,
                Attachment.class,
                UserProfile.class,
                TodoItem.class
        },
        version = 7,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract NoteDao noteDao();
    public abstract AttachmentDao attachmentDao();
    public abstract UserProfileDao userProfileDao();
    public abstract TodoDao todoDao();   // <-- ADD DAO

    // ---------------------
    // MIGRATIONS
    // ---------------------

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `todo_items` (" +
                            "`id` TEXT NOT NULL PRIMARY KEY, " +
                            "`noteId` TEXT, " +
                            "`text` TEXT, " +
                            "`isCompleted` INTEGER NOT NULL, " +
                            "`position` INTEGER NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL" +
                            ")"
            );
        }
    };

    private static final Migration[] ALL_MIGRATIONS = new Migration[]{
            MIGRATION_5_6
    };


    // ---------------------
    // INSTANCE
    // ---------------------
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "securenote_db"
                            )
                            .fallbackToDestructiveMigration()
                            .addMigrations(ALL_MIGRATIONS)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}

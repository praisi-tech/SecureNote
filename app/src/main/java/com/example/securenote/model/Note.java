package com.example.securenote.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public class Note {

    @PrimaryKey
    @NonNull
    private String id;

    private long timestamp;
    private boolean pinned;

    // Stored in Room (encrypted)
    private String encryptedTitle;
    private String encryptedContent;

    // UI-only decrypted fields
    @Ignore
    private String title;

    @Ignore
    private String content;

    // Lock & trash
    private boolean locked;
    private String lockPassword;
    private boolean inTrash;

    // Category (plain string)
    @ColumnInfo(name = "category")
    private String category;

    @ColumnInfo(name = "isImportant")
    private boolean isImportant;

    // UI-only helper flag
    private transient boolean contentHidden = false;


    // ---------------------------------------
    //  ROOM REQUIRED EMPTY CONSTRUCTOR
    // ---------------------------------------
    public Note() { }


    // ---------------------------------------
    //  UI CONSTRUCTOR (DECRYPTED DISPLAY OBJECT)
    //  USED BY NoteRepository.decryptForDisplay()
    // ---------------------------------------
    @Ignore
    public Note(@NonNull String id,
                String title,
                String content,
                long timestamp,
                boolean pinned) {

        this.id = id;
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.pinned = pinned;

        // Default values for decrypted UI usage
        this.locked = false;
        this.lockPassword = null;
        this.inTrash = false;
        this.category = "All";
        this.isImportant = false;
    }

    public Note(String noteId, String title, String content, long l, boolean checked, boolean locked, String passwordHash, boolean inTrash, String s, boolean important) {
    }


    // ---------------------------------------
    //  GETTERS & SETTERS
    // ---------------------------------------

    @NonNull
    public String getId() { return id; }

    public void setId(@NonNull String id) { this.id = id; }

    public long getTimestamp() { return timestamp; }

    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isPinned() { return pinned; }

    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public String getEncryptedTitle() { return encryptedTitle; }

    public void setEncryptedTitle(String encryptedTitle) { this.encryptedTitle = encryptedTitle; }

    public String getEncryptedContent() { return encryptedContent; }

    public void setEncryptedContent(String encryptedContent) { this.encryptedContent = encryptedContent; }

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }

    public void setContent(String content) { this.content = content; }

    public boolean isLocked() { return locked; }

    public void setLocked(boolean locked) { this.locked = locked; }

    public String getLockPassword() { return lockPassword; }

    public void setLockPassword(String lockPassword) { this.lockPassword = lockPassword; }

    public boolean isInTrash() { return inTrash; }

    public void setInTrash(boolean inTrash) { this.inTrash = inTrash; }

    public long getLastModified() { return timestamp; }

    public boolean isContentHidden() { return contentHidden; }

    public void setContentHidden(boolean contentHidden) { this.contentHidden = contentHidden; }

    public String getCategory() { return category; }

    public void setCategory(String category) { this.category = category; }

    public boolean isImportant() { return isImportant; }

    public void setImportant(boolean important) { this.isImportant = important; }
}

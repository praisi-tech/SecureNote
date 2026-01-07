package com.example.securenote.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;

@Entity(
        tableName = "attachments",
        indices = {
                @Index(value = "noteId")
        }
)
public class Attachment {

    @PrimaryKey
    @NonNull
    private String id;              // UUID

    private String noteId;          // FK to Note.id (logical, not enforced)
    private String displayName;     // For UI
    private String mimeType;        // image/png, application/pdf, etc.
    private long sizeBytes;         // Original size before encryption
    private long createdAt;         // System.currentTimeMillis()

    // Path to encrypted file in internal storage
    // e.g. /data/data/..../files/attachments/<id>.bin
    private String encryptedFilePath;

    // --- getters / setters ---

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getNoteId() {
        return noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getEncryptedFilePath() {
        return encryptedFilePath;
    }

    public void setEncryptedFilePath(String encryptedFilePath) {
        this.encryptedFilePath = encryptedFilePath;
    }
}

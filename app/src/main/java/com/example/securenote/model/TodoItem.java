package com.example.securenote.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "todo_items")
public class TodoItem {

    @PrimaryKey
    @NonNull
    private String id;

    private String noteId;
    private String text;
    private boolean isCompleted;
    private int position;
    private long createdAt;

    public TodoItem(@NonNull String id, String noteId, String text, boolean isCompleted, int position, long createdAt) {
        this.id = id;
        this.noteId = noteId;
        this.text = text;
        this.isCompleted = isCompleted;
        this.position = position;
        this.createdAt = createdAt;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getNoteId() { return noteId; }
    public void setNoteId(String noteId) { this.noteId = noteId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}

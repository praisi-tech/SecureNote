package com.example.securenote.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Delete;

import com.example.securenote.model.Attachment;

import java.util.List;

@Dao
public interface AttachmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Attachment attachment);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Attachment> attachments);

    @Delete
    void delete(Attachment attachment);

    @Query("DELETE FROM attachments WHERE noteId = :noteId")
    void deleteForNote(String noteId);

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt ASC")
    LiveData<List<Attachment>> getForNoteLive(String noteId);

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt ASC")
    List<Attachment> getForNoteNow(String noteId);
}

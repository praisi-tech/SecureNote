package com.example.securenote.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.securenote.model.Note;

import java.util.List;

@Dao
public interface NoteDao {

    @Query("SELECT * FROM notes WHERE inTrash = 0 ORDER BY timestamp DESC")
    LiveData<List<Note>> getActiveNotes();

    @Query("SELECT * FROM notes WHERE inTrash = 1 ORDER BY timestamp DESC")
    LiveData<List<Note>> getTrashNotes();
    @Query("SELECT * FROM notes WHERE category = :category AND inTrash = 0 ORDER BY timestamp DESC")
    LiveData<List<Note>> getNotesByCategory(String category);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Note note);

    @Update
    void update(Note note);

    @Delete
    void delete(Note note);

    @Query("DELETE FROM notes WHERE id = :id")
    void deleteById(String id);

    @Query("UPDATE notes SET inTrash = 1 WHERE id = :id")
    void moveToTrash(String id);

    @Query("UPDATE notes SET inTrash = 0 WHERE id = :id")
    void restoreFromTrash(String id);

    @Query("DELETE FROM notes WHERE id = :id AND inTrash = 1")
    void deleteFromTrash(String id);

    @Query("SELECT * FROM notes WHERE inTrash = 0")
    List<Note> getActiveNotesNow();

    @Query("SELECT * FROM notes WHERE inTrash = 1")
    List<Note> getTrashNotesNow();

}

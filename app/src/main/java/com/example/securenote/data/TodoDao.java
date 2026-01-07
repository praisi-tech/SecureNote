package com.example.securenote.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.securenote.model.TodoItem;

import java.util.List;

@Dao
public interface TodoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TodoItem item);

    @Update
    void update(TodoItem item);

    @Delete
    void delete(TodoItem item);

    @Query("SELECT * FROM todo_items WHERE noteId = :noteId ORDER BY position ASC, createdAt ASC")
    LiveData<List<TodoItem>> getTodos(String noteId);

    @Query("SELECT * FROM todo_items WHERE noteId = :noteId ORDER BY position ASC, createdAt ASC")
    List<TodoItem> getTodosSync(String noteId);

    @Query("DELETE FROM todo_items WHERE noteId = :noteId")
    void deleteTodosByNoteId(String noteId);
}

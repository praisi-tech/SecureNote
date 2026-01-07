package com.example.securenote.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.securenote.databinding.ActivityTrashBinding;
import com.example.securenote.model.Note;
import com.example.securenote.viewmodel.NoteViewModel;

public class TrashActivity extends AppCompatActivity {

    private ActivityTrashBinding binding;
    private NoteViewModel noteViewModel;
    private NoteAdapter trashAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTrashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        noteViewModel = new ViewModelProvider(this).get(NoteViewModel.class);

        trashAdapter = new NoteAdapter();
        binding.rvTrash.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTrash.setAdapter(trashAdapter);

        noteViewModel.getTrashNotes().observe(this, notes -> {
            trashAdapter.setNotes(notes);
            if (notes == null || notes.isEmpty()) {
                binding.tvEmptyTrash.setVisibility(View.VISIBLE);
                binding.rvTrash.setVisibility(View.GONE);
            } else {
                binding.tvEmptyTrash.setVisibility(View.GONE);
                binding.rvTrash.setVisibility(View.VISIBLE);
            }
        });

        trashAdapter.setOnNoteClickListener(new NoteAdapter.OnNoteClickListener() {
            @Override
            public void onNoteClick(Note note) {
                new AlertDialog.Builder(TrashActivity.this)
                        .setTitle("Restore Note")
                        .setMessage("Restore this note?")
                        .setPositiveButton("Restore", (d, w) -> noteViewModel.restoreFromTrash(note))
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onNoteLongClick(Note note) {
                new AlertDialog.Builder(TrashActivity.this)
                        .setTitle("Delete Permanently")
                        .setMessage("Delete this note permanently? This cannot be undone.")
                        .setPositiveButton("Delete", (d, w) -> {
                            // 🔍 Debug: check the ID before deleting
                            android.util.Log.d("TrashCheck", "Delete ID = " + note.getId());

                            noteViewModel.deleteFromTrash(note);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }
}

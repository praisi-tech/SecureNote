package com.example.securenote.ui;

import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Html;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.securenote.R;
import com.example.securenote.databinding.ItemNoteBinding;
import com.example.securenote.model.Note;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    private final List<Note> noteList = new ArrayList<>();
    private OnNoteClickListener listener;

    // Gradient backgrounds (for cards)
    private final int[] cardBackgrounds = {
            R.drawable.card_gradient_1,
            R.drawable.card_gradient_2,
            R.drawable.card_gradient_3,
            R.drawable.card_gradient_4
    };

    public interface OnNoteClickListener {
        void onNoteClick(Note note);
        void onNoteLongClick(Note note);
    }

    public NoteAdapter() {
        setHasStableIds(true);
    }

    public void setOnNoteClickListener(OnNoteClickListener listener) {
        this.listener = listener;
    }

    public void setNotes(List<Note> notes) {
        noteList.clear();
        if (notes != null) noteList.addAll(notes);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        Note note = noteList.get(position);
        if (note.getId() == null) return RecyclerView.NO_ID;
        return note.getId().hashCode();
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNoteBinding binding = ItemNoteBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new NoteViewHolder(binding, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = noteList.get(position);
        int bgIndex = position % cardBackgrounds.length;
        holder.setCardBackground(cardBackgrounds[bgIndex]);
        holder.bind(note);
    }

    @Override
    public int getItemCount() {
        return noteList.size();
    }

    public Note getNoteAt(int position) {
        if (position < 0 || position >= noteList.size()) return null;
        return noteList.get(position);
    }

    // -----------------------------
    // HELPER METHOD: Strip HTML Tags
    // -----------------------------
    private static String stripHtmlTags(String html) {
        if (html == null || html.isEmpty()) {
            return "No content";
        }

        // Convert HTML to plain text using Android's Html class
        String plainText;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            plainText = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString();
        } else {
            plainText = Html.fromHtml(html).toString();
        }

        // Clean up extra whitespace and newlines
        plainText = plainText.replaceAll("\\s+", " ").trim();

        // Limit preview length to 150 characters
        int maxLength = 150;
        if (plainText.length() > maxLength) {
            return plainText.substring(0, maxLength) + "...";
        }

        return plainText.isEmpty() ? "No content" : plainText;
    }

    // -----------------------------
    // VIEW HOLDER CLASS
    // -----------------------------
    public static class NoteViewHolder extends RecyclerView.ViewHolder {

        private final ItemNoteBinding binding;
        private final OnNoteClickListener listener;
        private final SimpleDateFormat sdf =
                new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());

        public NoteViewHolder(@NonNull ItemNoteBinding binding, OnNoteClickListener listener) {
            super(binding.getRoot());
            this.binding = binding;
            this.listener = listener;
        }

        public void setCardBackground(int backgroundRes) {
            if (binding.viewCardBackground != null) {
                binding.viewCardBackground.setBackgroundResource(backgroundRes);
            }
        }

        public void bind(Note note) {
            binding.tvTitle.setText(note.getTitle());

            // Content visibility logic - WITH HTML STRIPPING
            if (note.isContentHidden()) {
                binding.tvContent.setText("This note is locked");
                binding.tvContent.setTextColor(Color.GRAY);
                binding.tvContent.setTypeface(null, Typeface.ITALIC);
            } else {
                // ===== STRIP HTML TAGS HERE =====
                String cleanContent = stripHtmlTags(note.getContent());
                binding.tvContent.setText(cleanContent);
                binding.tvContent.setTextColor(Color.parseColor("#666666")); // Dark gray
                binding.tvContent.setTypeface(null, Typeface.NORMAL);
            }

            // CATEGORY (all use default pastel color)
            String category = note.getCategory();

            if (category != null && !category.isEmpty()) {
                binding.tvCategory.setText(category);
                binding.tvCategory.setVisibility(View.VISIBLE);

                int colorRes = R.color.pastel_default;  // ALWAYS default color

                GradientDrawable bg = (GradientDrawable) binding.tvCategory
                        .getBackground().mutate();
                bg.setColor(ContextCompat.getColor(binding.getRoot().getContext(), colorRes));

            } else {
                binding.tvCategory.setVisibility(View.GONE);
            }

            // Lock + pin icons
            if (binding.ivLock != null)
                binding.ivLock.setVisibility(note.isLocked() ? View.VISIBLE : View.GONE);
            if (binding.ivPin != null)
                binding.ivPin.setVisibility(note.isPinned() ? View.VISIBLE : View.GONE);

            // Timestamp
            if (binding.tvTimestamp != null) {
                long t = note.getLastModified();
                binding.tvTimestamp.setText(getRelativeTime(t));
            }

            // Click listeners
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onNoteClick(note);
            });
            binding.getRoot().setOnLongClickListener(v -> {
                if (listener != null) listener.onNoteLongClick(note);
                return true;
            });
        }

        private String getRelativeTime(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;
            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (seconds < 60) return "Just now";
            else if (minutes < 60) return minutes + " min ago";
            else if (hours < 24) return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
            else if (days < 7) return days + " day" + (days > 1 ? "s" : "") + " ago";
            else return new SimpleDateFormat("MMM dd", Locale.getDefault()).format(new Date(timestamp));
        }
    }
}
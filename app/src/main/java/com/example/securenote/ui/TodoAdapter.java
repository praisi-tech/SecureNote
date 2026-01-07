package com.example.securenote.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Paint;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;


import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.securenote.R;
import com.example.securenote.model.TodoItem;

import java.util.ArrayList;
import java.util.List;

public class TodoAdapter extends RecyclerView.Adapter<TodoAdapter.TodoViewHolder> {

    private final List<TodoItem> items = new ArrayList<>();

    public interface TodoListener {
        void onChecked(TodoItem item, boolean isChecked);
        void onTextChanged(TodoItem item, String text);
        void onDelete(TodoItem item);
        void onAddAfter(int position);
    }

    private TodoListener listener;

    public void setListener(TodoListener listener) {
        this.listener = listener;
    }

    public void setItems(List<TodoItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void removeItemAt(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
        }
    }

    @NonNull
    @Override
    public TodoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_todo, parent, false);
        return new TodoViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TodoViewHolder holder, int position) {
        holder.bind(items.get(position), position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class TodoViewHolder extends RecyclerView.ViewHolder {

        CheckBox cb;
        EditText et;
        ImageButton btnDelete;

        TodoItem item;
        private boolean isBinding = false;

        TodoViewHolder(@NonNull View itemView) {
            super(itemView);

            cb = itemView.findViewById(R.id.cbTodoItem);
            et = itemView.findViewById(R.id.etTodoText);
            btnDelete = itemView.findViewById(R.id.btnDeleteTodo);

            // ===========================
            // FIX: Avoid animation on bind
            // ===========================
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (item == null || isBinding) return;

                item.setCompleted(isChecked);
                updateStrikethrough(isChecked, true);
                animateCheckbox(isChecked);

                if (listener != null)
                    listener.onChecked(item, isChecked);
            });

            et.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (item == null || isBinding) return;

                    String txt = s.toString();
                    if (!txt.equals(item.getText())) {
                        item.setText(txt);

                        if (listener != null)
                            listener.onTextChanged(item, txt);
                    }
                }
            });

            // ENTER → Add item below
            et.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.getAction() == KeyEvent.ACTION_DOWN) {

                    if (listener != null) {
                        int currentPos = getAdapterPosition();
                        listener.onAddAfter(currentPos);

                        et.postDelayed(() -> {
                            RecyclerView rv = (RecyclerView) itemView.getParent();
                            if (rv != null) {
                                RecyclerView.ViewHolder nextHolder =
                                        rv.findViewHolderForAdapterPosition(currentPos + 1);

                                if (nextHolder instanceof TodoViewHolder) {
                                    EditText nextEt = ((TodoViewHolder) nextHolder).et;
                                    nextEt.requestFocus();
                                    nextEt.setSelection(nextEt.getText().length());
                                }
                            }
                        }, 100);
                    }
                    return true;
                }
                return false;
            });

            // Delete animation
            btnDelete.setOnClickListener(v -> {
                if (listener != null && item != null) {
                    animateDelete();
                }
            });
        }

        void bind(TodoItem todo, int pos) {
            isBinding = true;
            this.item = todo;

            if (!et.getText().toString().equals(todo.getText())) {
                et.setText(todo.getText());
                if (todo.getText() != null && todo.getText().length() > 0) {
                    et.setSelection(todo.getText().length());
                }
            }

            // FIX: Prevent triggering listener during bind
            cb.setChecked(todo.isCompleted());

            et.post(() -> updateStrikethrough(todo.isCompleted(), false));

            et.setEnabled(true);
            et.setFocusableInTouchMode(true);
            et.setCursorVisible(true);

            isBinding = false;
        }

        // All your animations stay the same
        private void animateCheckbox(boolean completed) { /* NO CHANGE */ }

        private void updateStrikethrough(boolean completed, boolean animate) {
            if (animate) {
                ValueAnimator alphaAnim = ValueAnimator.ofFloat(
                        completed ? 1f : 0.5f,
                        completed ? 0.5f : 1f
                );
                alphaAnim.setDuration(250);
                alphaAnim.addUpdateListener(animation -> {
                    float v = (float) animation.getAnimatedValue();
                    et.setAlpha(v);
                });
                alphaAnim.start();
            } else {
                et.setAlpha(completed ? 0.5f : 1f);
            }

            if (completed) {
                et.setPaintFlags(et.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                et.setPaintFlags(et.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            }
        }

        private void animateDelete() {
            int currentPosition = getAdapterPosition();

            AnimatorSet deleteAnim = new AnimatorSet();

            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(itemView, "alpha", 1f, 0f);
            ObjectAnimator slideOut = ObjectAnimator.ofFloat(itemView, "translationX", 0f, -100f);
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(itemView, "scaleX", 1f, 0.9f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(itemView, "scaleY", 1f, 0.9f);

            deleteAnim.playTogether(fadeOut, slideOut, scaleX, scaleY);
            deleteAnim.setDuration(200);

            deleteAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    itemView.setAlpha(1f);
                    itemView.setTranslationX(0f);
                    itemView.setScaleX(1f);
                    itemView.setScaleY(1f);

                    if (listener != null && item != null) {
                        listener.onDelete(item);
                        removeItemAt(currentPosition);
                    }
                }
            });

            deleteAnim.start();
        }
    }
}

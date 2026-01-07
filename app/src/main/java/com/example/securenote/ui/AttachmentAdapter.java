package com.example.securenote.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.securenote.R;
import com.example.securenote.model.Attachment;

import java.util.ArrayList;
import java.util.List;

public class AttachmentAdapter extends RecyclerView.Adapter<AttachmentAdapter.AttachmentViewHolder> {

    public interface OnAttachmentClickListener {
        void onAttachmentClick(Attachment attachment);
        void onAttachmentLongClick(Attachment attachment);
    }

    private final List<Attachment> attachments = new ArrayList<>();
    private OnAttachmentClickListener listener;

    public void setOnAttachmentClickListener(OnAttachmentClickListener listener) {
        this.listener = listener;
    }

    public void setAttachments(List<Attachment> newList) {
        attachments.clear();
        if (newList != null) {
            attachments.addAll(newList);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AttachmentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attachment, parent, false);
        return new AttachmentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AttachmentViewHolder holder, int position) {
        Attachment att = attachments.get(position);
        holder.bind(att);
    }

    @Override
    public int getItemCount() {
        return attachments.size();
    }

    class AttachmentViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivIcon;
        private final TextView tvName;
        private final TextView tvSize;

        AttachmentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivAttachmentIcon);
            tvName = itemView.findViewById(R.id.tvAttachmentName);
            tvSize = itemView.findViewById(R.id.tvAttachmentSize);
        }

        void bind(Attachment attachment) {
            tvName.setText(attachment.getDisplayName());

            long sizeBytes = attachment.getSizeBytes();
            tvSize.setText(formatSize(sizeBytes));

            // Simple icon based on MIME type
            String mime = attachment.getMimeType();
            int iconRes = android.R.drawable.ic_menu_save;
            if (mime != null) {
                if (mime.startsWith("image/")) {
                    iconRes = android.R.drawable.ic_menu_gallery;
                } else if (mime.equals("application/pdf")) {
                    iconRes = android.R.drawable.ic_menu_view;
                } else if (mime.startsWith("audio/")) {
                    iconRes = android.R.drawable.ic_btn_speak_now;
                }
            }
            ivIcon.setImageResource(iconRes);

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onAttachmentClick(attachment);
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onAttachmentLongClick(attachment);
                return true;
            });
        }

        private String formatSize(long bytes) {
            if (bytes <= 0) return "";
            double kb = bytes / 1024.0;
            if (kb < 1024) {
                return String.format("%.1f KB", kb);
            }
            double mb = kb / 1024.0;
            return String.format("%.1f MB", mb);
        }
    }
}

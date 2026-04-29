package com.apix.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Channel cards with 16:9 aspect ratio, name overlay at bottom-left
 * Touch + focus effects for both phone and TV
 * Focus effect: darker overlay + rounded gold border (matching website)
 */
public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    public interface OnChannelClick {
        void onClick(RemoteModels.Channel channel);
    }

    private Context context;
    private List<RemoteModels.Channel> data;
    private OnChannelClick listener;

    private static final int GOLD = Color.parseColor("#FFD700");

    public ChannelAdapter(Context ctx, List<RemoteModels.Channel> data, OnChannelClick listener) {
        this.context = ctx;
        this.data = data;
        this.listener = listener;
    }

    public void updateData(List<RemoteModels.Channel> newData) {
        this.data = newData;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_channel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RemoteModels.Channel channel = data.get(position);
        holder.name.setText(channel.name);

        // Force 16:9 aspect ratio
        holder.imageContainer.getViewTreeObserver().addOnPreDrawListener(
            new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    holder.imageContainer.getViewTreeObserver().removeOnPreDrawListener(this);
                    int width = holder.imageContainer.getMeasuredWidth();
                    if (width > 0) {
                        int height = (int) (width * 9.0 / 16.0);
                        ViewGroup.LayoutParams params = holder.imageContainer.getLayoutParams();
                        params.height = height;
                        holder.imageContainer.setLayoutParams(params);
                    }
                    return true;
                }
            });

        // Load image
        if (channel.imageUrl != null && !channel.imageUrl.isEmpty()) {
            ImageLoader.load(channel.imageUrl, holder.image);
        } else {
            holder.image.setImageResource(android.R.color.darker_gray);
        }

        holder.itemView.setOnClickListener(v -> listener.onClick(channel));

        holder.itemView.setClickable(true);
        holder.itemView.setFocusable(true);

        // Focus effect for D-pad (TV remote)
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            applyFocusEffect(holder, hasFocus);
        });

        // Touch feedback for phones
        holder.itemView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    applyFocusEffect(holder, true);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    applyFocusEffect(holder, false);
                    break;
            }
            return false;
        });
    }

    private void applyFocusEffect(ViewHolder holder, boolean focused) {
        if (focused) {
            holder.itemView.animate().scaleX(1.04f).scaleY(1.04f).setDuration(150).start();
            // Rounded gold border on the CardView
            holder.card.setCardElevation(10f);
            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(dpToPx(12));
            border.setStroke(dpToPx(2.5f), GOLD);
            border.setColor(Color.TRANSPARENT);
            holder.itemView.setForeground(border);
            // Gold name
            holder.name.setTextColor(GOLD);
        } else {
            holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
            holder.card.setCardElevation(4f);
            holder.itemView.setForeground(null);
            holder.name.setTextColor(Color.WHITE);
        }
    }

    private int dpToPx(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView name;
        FrameLayout imageContainer;
        CardView card;

        ViewHolder(View v) {
            super(v);
            image = v.findViewById(R.id.channel_image);
            name = v.findViewById(R.id.channel_name);
            imageContainer = v.findViewById(R.id.image_container);
            card = v.findViewById(R.id.channel_card);
        }
    }
}

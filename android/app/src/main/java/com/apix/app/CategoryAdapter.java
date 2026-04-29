package com.apix.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Category tabs with icons
 * Side mode: selected = gold bg, focused (not selected) = gold border outline
 * Bottom mode: gold text + indicator, evenly distributed
 */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {

    public interface OnCategoryClick {
        void onClick(RemoteModels.Category category);
    }

    private Context context;
    private List<RemoteModels.Category> data;
    private OnCategoryClick listener;
    private int selectedPosition = 0;
    private boolean isSideMode = false;

    private static final int GOLD = Color.parseColor("#FFD700");
    private static final int DARK_BG = Color.parseColor("#0A0A0A");

    private static final Map<String, Integer> ICON_MAP = new HashMap<>();
    static {
        ICON_MAP.put("sport", R.drawable.ic_sport);
        ICON_MAP.put("sports", R.drawable.ic_sport);
        ICON_MAP.put("movie", R.drawable.ic_movie);
        ICON_MAP.put("movies", R.drawable.ic_movie);
        ICON_MAP.put("network", R.drawable.ic_network);
        ICON_MAP.put("networks", R.drawable.ic_network);
        ICON_MAP.put("religion", R.drawable.ic_religion);
        ICON_MAP.put("relagon", R.drawable.ic_religion);
        ICON_MAP.put("دين", R.drawable.ic_religion);
        ICON_MAP.put("settings", R.drawable.ic_settings);
    }

    public CategoryAdapter(Context ctx, List<RemoteModels.Category> data, OnCategoryClick listener) {
        this.context = ctx;
        this.data = data;
        this.listener = listener;
    }

    public void setSideMode(boolean sideMode) {
        this.isSideMode = sideMode;
    }

    public void updateData(List<RemoteModels.Category> newData) {
        this.data = newData;
        notifyDataSetChanged();
    }

    public void setSelected(int position) {
        int old = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(old);
        notifyItemChanged(position);
    }

    private int getIconForCategory(String name) {
        if (name == null) return R.drawable.ic_category_default;
        String lower = name.toLowerCase().trim();
        for (Map.Entry<String, Integer> entry : ICON_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return R.drawable.ic_category_default;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = isSideMode ? R.layout.item_category_side : R.layout.item_category;
        View view = LayoutInflater.from(context).inflate(layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RemoteModels.Category cat = data.get(position);
        holder.name.setText(cat.name);
        holder.name.setAllCaps(true);

        int iconRes = getIconForCategory(cat.name);
        if (holder.icon != null) {
            holder.icon.setImageResource(iconRes);
        }

        boolean isSelected = position == selectedPosition;

        if (isSideMode) {
            applySideStyle(holder, isSelected, false);
        } else {
            applyBottomStyle(holder, isSelected);
        }

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            setSelected(pos);
            listener.onClick(data.get(pos));
        });

        holder.itemView.setFocusable(true);
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (isSideMode) {
                boolean sel = holder.getAdapterPosition() == selectedPosition;
                if (hasFocus) {
                    if (sel) {
                        // Already gold bg, just scale
                        applySideStyle(holder, true, false);
                    } else {
                        // Focus = gold border outline (not full bg)
                        applySideFocusStyle(holder);
                    }
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(100).start();
                } else {
                    applySideStyle(holder, sel, false);
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                }
            } else {
                boolean sel = holder.getAdapterPosition() == selectedPosition;
                if (hasFocus) {
                    holder.name.setTextColor(GOLD);
                    if (holder.icon != null) {
                        holder.icon.setColorFilter(GOLD, PorterDuff.Mode.SRC_IN);
                    }
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(100).start();
                } else {
                    applyBottomStyle(holder, sel);
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                }
            }
        });
    }

    private void applySideStyle(ViewHolder holder, boolean selected, boolean focused) {
        if (selected) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(12f);
            bg.setColor(GOLD);
            holder.itemView.setBackground(bg);
            holder.name.setTextColor(DARK_BG);
            if (holder.icon != null) {
                holder.icon.setColorFilter(DARK_BG, PorterDuff.Mode.SRC_IN);
            }
        } else {
            holder.itemView.setBackground(null);
            holder.name.setTextColor(Color.WHITE);
            if (holder.icon != null) {
                holder.icon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            }
        }
    }

    /** Focus style for non-selected sidebar items: gold border outline */
    private void applySideFocusStyle(ViewHolder holder) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12f);
        bg.setStroke(3, GOLD);
        bg.setColor(Color.TRANSPARENT);
        holder.itemView.setBackground(bg);
        holder.name.setTextColor(GOLD);
        if (holder.icon != null) {
            holder.icon.setColorFilter(GOLD, PorterDuff.Mode.SRC_IN);
        }
    }

    private void applyBottomStyle(ViewHolder holder, boolean selected) {
        holder.name.setTextColor(selected ? GOLD : Color.WHITE);
        if (holder.icon != null) {
            holder.icon.setColorFilter(selected ? GOLD : Color.WHITE, PorterDuff.Mode.SRC_IN);
        }
        if (holder.indicator != null) {
            holder.indicator.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        View indicator;
        ImageView icon;

        ViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.category_name);
            indicator = v.findViewById(R.id.category_indicator);
            icon = v.findViewById(R.id.category_icon);
        }
    }
}

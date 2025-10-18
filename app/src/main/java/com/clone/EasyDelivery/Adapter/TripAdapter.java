package com.clone.EasyDelivery.Adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.EasyDelivery.R;

import java.util.ArrayList;
import java.util.List;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    private List<String> tripFiles = new ArrayList<>();
    private LayoutInflater inflater;
    private OnItemClickListener listener;
    private Context context;
    private boolean enableAnimations = true;
    private List<Integer> animatedPositions = new ArrayList<>();


    public interface OnItemClickListener {
        void onItemClick(String tripNumber);
    }

    // Constructor
    public TripAdapter(Context context, OnItemClickListener onItemClickListener) {
        this.listener = onItemClickListener;
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }

    public void updateTrips(List<String> newTrips) {
        if (newTrips != null) {
            boolean wasEmpty = tripFiles.isEmpty();
            tripFiles.clear();
            tripFiles.addAll(newTrips);
            
            if (wasEmpty && !newTrips.isEmpty()) {
                // 🎨 SMOOTH ANIMATIONS: Enable staggered entry animations for initial load
                enableAnimations = true;
                animatedPositions.clear();
                Log.i("TripAdapter", "🎨 Enabling entry animations for " + newTrips.size() + " trips");
            }
            
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_trip, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        String tripFile = tripFiles.get(position);

        holder.bind(tripFile, listener);
        
        // 🎨 SMOOTH ANIMATIONS: Apply staggered fade-in animation
        if (enableAnimations && !animatedPositions.contains(position)) {
            animateItem(holder.itemView, position);
            animatedPositions.add(position);
            
            // Disable animations after all items have been shown
            if (animatedPositions.size() >= tripFiles.size()) {
                enableAnimations = false;
                Log.i("TripAdapter", "🎨 All trip animations completed, disabling for performance");
            }
        }
    }

    @Override
    public int getItemCount() {
        return tripFiles.size();
    }
    
    /**
     * 🎨 Apply smooth entrance animation to item view
     * Uses staggered timing for elegant cascading effect
     */
    private void animateItem(View itemView, int position) {
        try {
            // Set initial state - invisible for fade-in
            itemView.setAlpha(0f);
            
            // Choose animation based on position for variety
            int animationResource = (position % 2 == 0) ? R.anim.item_fade_in : R.anim.item_fade_in_delayed;
            Animation animation = AnimationUtils.loadAnimation(context, animationResource);
            
            // Add stagger delay based on position (100ms per item)
            long staggerDelay = Math.min(position * 100L, 800L); // Cap at 800ms
            animation.setStartOffset(staggerDelay);
            
            // Set animation listener to ensure proper visibility
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    itemView.setAlpha(1f); // Make visible when animation starts
                }
                
                @Override
                public void onAnimationEnd(Animation animation) {
                    // Animation completed
                }
                
                @Override
                public void onAnimationRepeat(Animation animation) {
                    // Not used
                }
            });
            
            // Start the animation
            itemView.startAnimation(animation);
            
            Log.d("TripAdapter", "🎨 Animating trip item " + position + " with delay " + staggerDelay + "ms");
            
        } catch (Exception e) {
            Log.w("TripAdapter", "Animation failed for position " + position + ", showing item immediately", e);
            // Fallback: show item immediately if animation fails
            itemView.setAlpha(1f);
        }
    }

    // ViewHolder class
    public static class TripViewHolder extends RecyclerView.ViewHolder {
        private TextView tripNumberTextView;
        private TextView textViewDeliveryCount;

        public TripViewHolder(@NonNull View itemView) {
            super(itemView);

            tripNumberTextView = itemView.findViewById(R.id.text_trip_number);
            textViewDeliveryCount = itemView.findViewById(R.id.textViewDeliveryCount);
        }

        public void bind(final String tripName, final TripAdapter.OnItemClickListener listener) {
            int deliveryCount = 0;

            // Note: Delivery count will be handled by API data in future updates
            // For now, show minimal placeholder count until API integration is complete
            deliveryCount = 1; // Default placeholder

            tripNumberTextView.setText(tripName);

            if (deliveryCount > 1) {
                textViewDeliveryCount.setText(deliveryCount + " Deliveries");
            } else {
                textViewDeliveryCount.setText(deliveryCount + " Delivery");
            }

            itemView.setOnClickListener(v -> listener.onItemClick(tripName));
        }
    }
}

package com.clone.EasyDelivery.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.EasyDelivery.data.entities.DeliveryEntity;
import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.viewmodel.UserStateViewModel;

import java.util.List;

/**
 * HeaderAdapter for displaying delivery documents in RecyclerView
 * Updated to use UserStateViewModel instead of AppConstants
 */
public class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.CustomerViewHolder> {

    private List<DeliveryEntity> deliveries;
    private OnItemClickListener listener;
    private UserStateViewModel userStateViewModel; // Modern approach
    private ImageView imageViewExclamation;

    public interface OnItemClickListener {
        void onItemClick(DeliveryEntity delivery);
    }

    public HeaderAdapter(List<DeliveryEntity> deliveries, OnItemClickListener listener) {
        this.deliveries = deliveries;
        this.listener = listener;
        this.userStateViewModel = null; // Will be set via setter
    }

    /**
     * Set UserStateViewModel for state access
     */
    public void setUserStateViewModel(UserStateViewModel userStateViewModel) {
        this.userStateViewModel = userStateViewModel;
    }

    @NonNull
    @Override
    public CustomerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_header, parent, false);
        return new CustomerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CustomerViewHolder holder, int position) {
        DeliveryEntity delivery = deliveries.get(position);
        holder.bind(delivery, listener, userStateViewModel);
    }

    @Override
    public int getItemCount() {
        return deliveries.size();
    }

    public static class CustomerViewHolder extends RecyclerView.ViewHolder {
        private TextView documentNumberTextView;
        private TextView customerNameTextView;
        private TextView customerAddressTextView;
        private ImageView imageViewExclamation;

        public CustomerViewHolder(@NonNull View itemView) {
            super(itemView);
            documentNumberTextView = itemView.findViewById(R.id.text_document_number);
            customerNameTextView = itemView.findViewById(R.id.text_customer_name);
            customerAddressTextView = itemView.findViewById(R.id.text_customer_address);
            imageViewExclamation = itemView.findViewById(R.id.iv_exclamation);
        }

        public void bind(final DeliveryEntity delivery, final OnItemClickListener listener, UserStateViewModel userStateViewModel) {
            documentNumberTextView.setText(delivery.getDeliveryId());
            customerNameTextView.setText(delivery.getCustomerName());
            customerAddressTextView.setText(delivery.getDeliveryAddress());

            // Use UserStateViewModel instead of AppConstants
            String currentDocument = userStateViewModel != null ? userStateViewModel.getCurrentDocumentValue() : "";

            if (currentDocument != null && currentDocument.equals(delivery.getDeliveryId())) {
                itemView.setBackgroundResource(R.drawable.dash_border_red);
            } else {
                itemView.setBackgroundResource(R.drawable.dash_border);
            }

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    listener.onItemClick(delivery);
                }
            });
        }
    }
}

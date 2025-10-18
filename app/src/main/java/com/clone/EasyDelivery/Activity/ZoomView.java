package com.clone.EasyDelivery.Activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.viewmodel.UserStateViewModel;
import com.squareup.picasso.Picasso;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * ZoomView Activity for displaying images with zoom functionality
 * Uses UserStateViewModel for state management
 * <p>
 * Uses Hilt for proper dependency injection into ViewModels
 */
@AndroidEntryPoint
public class ZoomView extends AppCompatActivity {

    private ImageView ivZoom;
    private RelativeLayout ivClose;
    private UserStateViewModel userStateViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zoom_view);

        userStateViewModel = new ViewModelProvider(this).get(UserStateViewModel.class);

        ivZoom = (ImageView) findViewById(R.id.photo_view);
        ivClose = findViewById(R.id.llTop);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);

        int width = dm.widthPixels;
        int height = dm.heightPixels;

        getWindow().setLayout((int) ((int) width * .85), (int) ((int) height * .85));

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.gravity = Gravity.CENTER;
        params.x = 0;
        params.y = -20;

        getWindow().setAttributes(params);

        String zoomPath = userStateViewModel.getZoom().getValue();
        if (zoomPath != null && !zoomPath.isEmpty()) {
            Picasso.with(this)
                    .load("file://" + zoomPath)
                    .into(ivZoom);
        }

        ivClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}

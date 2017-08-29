/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.tensorflow.cloudmachinelearningengine;

import android.app.Activity;
import android.app.Fragment;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.support.v13.app.FragmentCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainStylizerFragment extends Fragment
        implements View.OnTouchListener, FragmentCompat.OnRequestPermissionsResultCallback {
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "MainStylizerFragment";

    // hints as toast for showing briefly how this app works
    private static final String ACTION_HINTS = "Press camera icon to take a picture.\n" +
            "Press switch icon to switch camera.\n" +
            "Select a style icon to apply style.\n" +
            "Have fun.";

    // client parameters: alpha blend between original source bitmap and stylized bitmap
    private static final float IMAGE_PREVIEW_ALPHA = 0.9f;

    /**
     * UI components
     */
    // ImageView to captured image as well as stylized image
    private ImageView mImageView;
    private Activity mActivity;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    // carousel for holding thumbnails of styles using RecyclerView
    private RecyclerView mHorizontalRecyclerView;
    private Carousel mCarousel;
    private List<Carousel.CarouselImage> mCarouselImages = new ArrayList<>();
    
    private CMLEHandler mCMLEHandler;

    private CameraHandler mCameraHandler;

    /**
     * MainStylizerFragment
     */
    public static MainStylizerFragment newInstance() {
        return new MainStylizerFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.main_fragment, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        // current activity that holds this fragment
        mActivity = getActivity();
        // preview textureview
        mTextureView = view.findViewById(R.id.texture);
        // image view for captured and stylized image
        mImageView = view.findViewById(R.id.image);

        // set up camera button touch events
        view.findViewById(R.id.picture).setOnTouchListener(this);

        //  set up switch camera button click event
        ImageButton switchCameraButton = view.findViewById(R.id.switch_camera);

        mCMLEHandler = new CMLEHandler(getActivity(), mImageView);

        // carousel of thumbnails
        mHorizontalRecyclerView = view.findViewById(R.id.horizontal_recycler_view);
        mCarousel = new Carousel(getActivity(), mCMLEHandler);
        LinearLayoutManager horizontalLayoutManager = new LinearLayoutManager(mActivity, LinearLayoutManager.HORIZONTAL, false);
        mHorizontalRecyclerView.setLayoutManager(horizontalLayoutManager);
        mHorizontalRecyclerView.setAdapter(mCarousel);
        mCarousel.loadCarouselImages();

        mCameraHandler = new CameraHandler(getActivity(), mImageView, mTextureView);

        mCMLEHandler.setCameraHandler(mCameraHandler);
        // authenticate service account using json file
        mCMLEHandler.getCMLECredentials();
        // set up CMLE project path, request json and engine instance
        mCMLEHandler.setupCMLERequest();

        // Listener for Switch cameras button
        switchCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraHandler.switchCameras();
            }
        });

        // set up textureview click event for action hints
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showToast(ACTION_HINTS);
            }
        });
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int id = v.getId();
        Log.d(TAG, "touch id: " + id);
        if (id == R.id.picture) {
            int eventaction = event.getAction();
            switch (eventaction) {
                case MotionEvent.ACTION_DOWN:
                    mImageView.setVisibility(View.INVISIBLE);
                    return true;

                case MotionEvent.ACTION_UP:
                    mImageView.setAlpha(IMAGE_PREVIEW_ALPHA);
                    mCameraHandler.takePicture();
                    break;
            }
            // tell the system that we handled the event but a further processing is required
            return false;
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        mCameraHandler.startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            mCameraHandler.openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        mCameraHandler.closeCamera();
        mCameraHandler.stopBackgroundThread();
        super.onPause();
    }

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable: " + width + " : " + height);
            mCameraHandler.openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            //configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    /*
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        if (mActivity != null) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mActivity, text, Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}

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
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * image thumbnails carousel using RecyclerView
 */

public class Carousel extends RecyclerView.Adapter<Carousel.CarouselViewHolder> {
    private static final String TAG = "CAROUSEL";

    // client parameters: number of thumbnails available
    private static final int NUM_THUMB_STYLES = 26;

    private Activity activity;
    private List<CarouselImage> carouselImageList;

    private CMLEHandler mCMLEHandler;

    public Carousel(Activity activity,
                    CMLEHandler cmleHandler) {
        this.activity = activity;
        this.mCMLEHandler = cmleHandler;
        carouselImageList = new ArrayList<>();
    }

    // Load style thumbnail images into an array
    public void loadCarouselImages() {
        TypedArray styleThumbIds = activity.getResources().obtainTypedArray(R.array.styleThumbnails);
        for (int i = 0; i < NUM_THUMB_STYLES; i++) {
            carouselImageList.add(new Carousel.CarouselImage(
                    BitmapFactory.decodeResource(
                            activity.getResources(),
                            styleThumbIds.getResourceId(i, 0))));
        }
        // recycle the array
        styleThumbIds.recycle();
    }

    public class CarouselViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public CarouselViewHolder(View view) {
            super(view);
            imageView = view.findViewById(R.id.imageview);
        }
    }

    @Override
    public CarouselViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.thumbnail_carousel, parent, false);

        return new CarouselViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(final CarouselViewHolder holder, final int position) {
        holder.imageView.setImageBitmap(carouselImageList.get(position).getBitmap());

        holder.imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick sendStylizedRequest");
                new Thread(new Runnable() {
                    public void run() {
                        mCMLEHandler.sendRequestToCMLE(position);
                    }
                }).start();
            }
        });

    }

    @Override
    public int getItemCount() {
        return carouselImageList.size();
    }

    // inner class for loadng thumbnails into carousel for style thumbnails
    protected static class CarouselImage {
        private Bitmap mBitmap;

        CarouselImage(Bitmap bitmap) {
            mBitmap = bitmap;
        }

        public Bitmap getBitmap() {
            return mBitmap;
        }
    }
}
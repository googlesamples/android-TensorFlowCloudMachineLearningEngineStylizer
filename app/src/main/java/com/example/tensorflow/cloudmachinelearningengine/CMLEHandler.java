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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.ml.v1.CloudMachineLearningEngine;
import com.google.api.services.ml.v1.CloudMachineLearningEngineScopes;
import com.google.api.services.ml.v1.model.GoogleApiHttpBody;
import com.google.api.services.ml.v1.model.GoogleCloudMlV1PredictRequest;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class CMLEHandler {
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "CMLEHANDLER";

    /**
     * Boolean flag for debugging.
     */
    private static final Boolean DEBUG = false;
    /**
     * Debug related to timing
     */
    long mRequestStartMs;
    long mRequestEndMs;
    
    // constants for TF stylizer model on Cloud Machine Learning Engine
    private static final String PROJECT_ID = "<YOUR_PROJECT_ID>";
    private static final String MODEL_NAME = "<YOUR_MODEL_NAME>";
    // service account for authentication
    private static final String SERVICE_ACCOUNT_JSON_FILE = "<YOUR_SERVICE_ACCOUNT_KEY_JSON>";

    // input parameters of the TF stylizer model
    private static final int NUM_RAW_STYLES = 32;
    private static final String INSTANCES = "instances";
    private static final String PREDICTIONS = "predictions";
    // output parameters of the TF stylizer model
    private static final String OUTPUT_IMAGE = "output_image";

    // alpha blend between original source bitmap and stylized bitmap:
    private static final int BLEND_ALPHA = 128;

    // ImageView to captured image as well as stylized image
    private ImageView mImageView;
    private Activity mCurrentActivity;

    private CameraHandler mCameraHandler;

    // array of floats for sending image requests with style weights
    private final Float[] mStyleVals = new Float[NUM_RAW_STYLES];

    /**
     * Cloud Machine Learning Engine objects
     */
    // credentials related to service account
    private GoogleCredential mCredentials = null;

    // CMLE instance for making request
    private CloudMachineLearningEngine mCloudMachineLearningEngine;

    // JSON request for prediction
    private GoogleCloudMlV1PredictRequest mRequestJson;

    // project path string related to project id and model name
    private String mProjectPath;

    public CMLEHandler(Activity activity, ImageView imageView) {
        mCurrentActivity = activity;
        mImageView = imageView;
    }

    public void setCameraHandler(CameraHandler cameraHandler) {
        mCameraHandler = cameraHandler;
    }

    // authenticate the service account associated with the CMLE project/model
    public void getCMLECredentials() {
        Log.d(TAG, "getCMLECredentials");

        // get application default credentials from service account json
        int credentialId = mCurrentActivity.getResources().getIdentifier(
                SERVICE_ACCOUNT_JSON_FILE, "raw", mCurrentActivity.getPackageName());
        InputStream jsonCredentials = mCurrentActivity.getResources().openRawResource(credentialId);
        try {
            mCredentials = GoogleCredential.fromStream(jsonCredentials).createScoped(
                    Collections.singleton(CloudMachineLearningEngineScopes.CLOUD_PLATFORM));
        } catch (IOException e) {
            Log.d(TAG, "You need to create service account and associated private key");
        } finally {
            try {
                jsonCredentials.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing input stream", e);
            }
        }
    }

    /**
     * Set up the following associated with Cloud Machine Learning Engine request
     * String project path
     * {@link GoogleCloudMlV1PredictRequest}
     * {@link CloudMachineLearningEngine}
     */
    public void setupCMLERequest() {
        Log.d(TAG, "setupCMLERequest");
        // set project path
        mProjectPath = String.format("projects/%s/models/%s", PROJECT_ID, MODEL_NAME);

        // instantiate predict request
        mRequestJson = new GoogleCloudMlV1PredictRequest();

        // Set up the HTTP transport and JSON factory
        final HttpTransport httpTransport = new ApacheHttpTransport();
        //AndroidHttp.newCompatibleTransport();
        final JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        // instantiate CloudMachineLearningEngine instance
        mCloudMachineLearningEngine = new CloudMachineLearningEngine.Builder(
                httpTransport,
                jsonFactory,
                mCredentials)
                .setApplicationName(mCurrentActivity.getPackageName())
                .build();
    }

    // set up parameters for CMLE request
    private void setRequestInputParameters(Bitmap bitmap, int style_index) {
        // encode bitmap to string
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        String encodedByteArrayString = Base64.encodeToString(byteArray, Base64.DEFAULT);

        // set style to mStyleVals weight array
        // Image style could be selected as an array of intensities from multiple existing source
        // styles but their sum need to add up to 1. In this case for simplicity we're only picking
        // one style so we just set its corresponding intensity to 1 while others to 0.
        for (int i = 0; i < NUM_RAW_STYLES; i++) {
            mStyleVals[i] = i == style_index ? 1.00f : 0.0f;
        }

        // set input image pixel bytes and weights
        PixelStyleJSON imageStylePixels = new PixelStyleJSON();
        imageStylePixels.setImageBytesAndWeights(encodedByteArrayString, mStyleVals);

        // set instances input
        mRequestJson.set(INSTANCES, imageStylePixels.objectifyImageStylePixels());

        if (DEBUG) {
            Log.d(TAG, "mRequestJson: " + " : " + mRequestJson);
            writeToFile(mRequestJson.toString());
        }
    }

    // Use {@create a {@link CloudMachineLearningEngine} object to create a
    // CloudMachineLearningEngine.Projects.Predict} instance, call its execute()
    // to apply styles, and then process its response to decode stylized bitmap
    public void sendRequestToCMLE(int style) {
        Bitmap bitmap = mCameraHandler.getCroppedBitmap();
        if (mCameraHandler == null || bitmap == null) {
            Log.d(TAG, "Source bitmap is null.");
            return;
        }
        Log.d(TAG, "sendStylizedRequestToCMLE: " + style);

        setRequestInputParameters(bitmap, style);
        GoogleApiHttpBody response = null;
        mRequestStartMs = SystemClock.elapsedRealtime();
        try {
            CloudMachineLearningEngine.Projects.Predict predict =
                    mCloudMachineLearningEngine.projects().predict(mProjectPath, mRequestJson);
            response = predict.execute();

            mRequestEndMs = SystemClock.elapsedRealtime();
            long lapseMs = mRequestEndMs - mRequestStartMs;

            Log.d(TAG, "response time: " + lapseMs);
        } catch (java.io.IOException io) {
            Log.d(TAG, "predict execution i/o error: " + io);
        }

        if (response != null) {
            overlayImageViewByStylizedBitmap(
                    decodeStylizedBitmapFromResponse(response),
                    bitmap);
        } else {
            Log.d(TAG, "Response body from CMLE is null.");
        }
    }

    /*
     * Parse {@link GoogleApiHttpBody} response to extract/decode stylized bitmap
     * using the following format.
     * response format:
       {"predictions":
         [
            {"output_image": "<encoded_byte_string"},
            ...
            {"output_image": "<encoded_byte_string"}
         ]
       }
     */
    public Bitmap decodeStylizedBitmapFromResponse(GoogleApiHttpBody response) {
        if (response.get("error") == null) {
            Gson gson = new Gson();
            String predictions = gson.toJson(response.get(PREDICTIONS));

            ArrayList<?> outImages = (ArrayList<?>) gson.fromJson(predictions, Object.class);
            // Only one image is sent in this sample so we always fetch the first stylized bitmap
            HashMap<?, ?> stylizedImage0 = (HashMap<?, ?>) outImages.get(0);
            String decodedStylizedString = (String) stylizedImage0.get(OUTPUT_IMAGE);

            byte[] decodedStylizedBytes = Base64.decode(decodedStylizedString, Base64.URL_SAFE);
            Bitmap decodedStylizedBitmap = BitmapFactory.decodeByteArray(
                    decodedStylizedBytes, 0, decodedStylizedBytes.length);

            return decodedStylizedBitmap;
        } else {
            Log.d(TAG, "Response from CMLE has error.");
            return null;
        }
    }

    // Overlay stylized bitmap onto the original captured source bitmap and render to image view
    private void overlayImageViewByStylizedBitmap(Bitmap styledBitmap, Bitmap sourceBitmap) {
        Bitmap blended = blendBitmaps(styledBitmap, sourceBitmap);
        mCurrentActivity.runOnUiThread(() -> {
            if (styledBitmap != null) {
                if (mImageView != null) {
                    mImageView.setVisibility(View.VISIBLE);
                    mImageView.setAlpha(1.0f);
                    mImageView.setImageBitmap(blended);
                }
            }
        });
    }

    /**
     * @param stylized The styled image to use as the base
     * @param original The original image, to overlay at 50% opacity.
     * @return
     */
    public static Bitmap blendBitmaps(Bitmap stylized, Bitmap original) {
        Bitmap blended = Bitmap.createBitmap(stylized.getWidth(), stylized.getHeight(), stylized.getConfig());
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setAlpha(BLEND_ALPHA);
        Canvas canvas = new Canvas(blended);
        canvas.drawBitmap(stylized, new Matrix(), null);
        canvas.drawBitmap(original, new Matrix(), paint);
        return blended;
    }

    // Used to save e.g. request CMLE JSON into file for debugging
    private void writeToFile(String data) {
        try {
            File sdCard = Environment.getExternalStorageDirectory();
            File dir = new File(sdCard.getAbsolutePath() + "/");
            File file = new File(dir, "request.json");
            Log.d(TAG, "current dir path: " + file.toString());
            FileOutputStream stream = new FileOutputStream(file);
            try {
                stream.write(data.getBytes());
            } finally {
                stream.close();
            }
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }
}

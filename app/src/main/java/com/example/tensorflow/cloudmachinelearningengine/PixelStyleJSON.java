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

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;

public class PixelStyleJSON {
    private static final String STYLE_ENCODE = "b64";
    private static final String STYLE_WEIGHT = "style_weights";
    private static final String STYLE_IMAGE_BYTES = "image_bytes";

    ArrayList<HashMap<String, Object>> pixelStyleMapList;
    HashMap<String, Object> pixelStyleMap;

    public PixelStyleJSON() {
        pixelStyleMapList = new ArrayList<>();
        pixelStyleMap = new HashMap<>();
    }

    protected void setImageBytesAndWeights(String bytes, Float[] w) {
        HashMap<String, String> encodedContent = new HashMap<>();
        encodedContent.put(STYLE_ENCODE, bytes);
        pixelStyleMap.put(STYLE_WEIGHT, w);
        pixelStyleMap.put(STYLE_IMAGE_BYTES, encodedContent);
        pixelStyleMapList.add(pixelStyleMap);
    }

    protected Object objectifyImageStylePixels() {
        pixelStyleMapList.add(pixelStyleMap);
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(pixelStyleMapList), Object.class);
    }
}
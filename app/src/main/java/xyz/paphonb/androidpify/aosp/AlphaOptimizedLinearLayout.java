/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package xyz.paphonb.androidpify.aosp;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * A linear layout which does not have overlapping renderings commands and therefore does not need a
 * layer when alpha is changed.
 */
public class AlphaOptimizedLinearLayout extends LinearLayout
{
    public AlphaOptimizedLinearLayout(Context context) {
        super(context);
    }

    public AlphaOptimizedLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AlphaOptimizedLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AlphaOptimizedLinearLayout(Context context, AttributeSet attrs, int defStyleAttr,
                                      int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jackrabbit.vault.fs.spi;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;

/**
 * <code>ProgressTracker</code>...
 *
 */
public class ProgressTracker {

    protected ProgressTrackerListener listener;

    private ProgressTrackerListener.Mode mode = ProgressTrackerListener.Mode.TEXT;

    private boolean enabled = true;

    public ProgressTracker() {
    }

    public ProgressTracker(ProgressTrackerListener listener) {
        this.listener = listener;
    }

    public void setListener(ProgressTrackerListener listener) {
        this.listener = listener;
    }

    public ProgressTrackerListener getListener() {
        return listener;
    }

    public void track(String action, String path) {
        if (enabled && listener != null) {
            listener.onMessage(mode, action, path);
        }
    }

    public void track(Exception e, String path) {
        if (enabled && listener != null) {
            listener.onError(mode, path, e);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ProgressTrackerListener.Mode getMode() {
        return mode;
    }

    public ProgressTrackerListener.Mode setMode(ProgressTrackerListener.Mode mode) {
        ProgressTrackerListener.Mode prev = this.mode;
        this.mode = mode;
        return prev;
    }


}
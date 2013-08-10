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

package org.apache.jackrabbit.vault.fs.api;

/**
 * A <code>ProgressTrackerListener</code> can be provided by clients to
 * receive messages and errors during operations.
 */
public interface ProgressTrackerListener {

    /**
     * Is called when a message is received.
     * @param mode message mode
     * @param action action
     * @param path path or message the action was performed on
     */
    void onMessage(Mode mode, String action, String path);

    /**
     * Is called when an error is received.
     * @param mode message mode
     * @param path path or message
     * @param e error
     */
    void onError(Mode mode, String path, Exception e);

    /**
     * Message mode
     */
    enum Mode {
        
        /**
         * Argument represents a generic text.
         */
        TEXT,

        /**
         * Argument represents a path.
         */
        PATHS
    }

}
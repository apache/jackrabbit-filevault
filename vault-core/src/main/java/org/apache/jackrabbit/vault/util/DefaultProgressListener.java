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

package org.apache.jackrabbit.vault.util;

import java.io.PrintWriter;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;

/**
 * <code>DefaultProgressListener</code>...
*/
public class DefaultProgressListener implements ProgressTrackerListener {

    private final PrintWriter out;

    public DefaultProgressListener() {
        this(new PrintWriter(System.out));
    }

    public DefaultProgressListener(PrintWriter out) {
        this.out = out;
    }

    public void onMessage(Mode mode, String action, String path) {
        String name = path;
        if (mode == Mode.PATHS) {
            name = path.substring(path.lastIndexOf('/') + 1);
        }
        out.printf("%s %s%n", action, name);
        out.flush();
    }

    public void onError(Mode mode, String path, Exception e) {
        String name = path;
        if (mode == Mode.PATHS) {
            name = path.substring(path.lastIndexOf('/') + 1);
        }
        out.printf("E %s (%s)%n", name, e.toString());
        out.flush();
    }

}
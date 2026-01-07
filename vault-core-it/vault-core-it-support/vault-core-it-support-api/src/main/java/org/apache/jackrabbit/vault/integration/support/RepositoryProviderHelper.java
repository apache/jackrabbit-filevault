/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.integration.support;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;

public class RepositoryProviderHelper {
    private RepositoryProviderHelper() {
        // only static methods
    }

    public static void deleteDirectory(File directory) throws IOException {
        try {
            FileUtils.deleteDirectory(directory);
        } catch (IOException ioe) {
            // retry after wait on Windows, as it may release file locks in a deferred manner
            if (SystemUtils.IS_OS_WINDOWS) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    IOException wrappedIOException =
                            new IOException("Initially failed with IOException and waiting was interrupted", ioe);
                    wrappedIOException.addSuppressed(ie);
                }
                FileUtils.deleteDirectory(directory);
            } else {
                throw ioe;
            }
        }
    }
}

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
package org.apache.jackrabbit.vault.packaging.integration;

import javax.jcr.RepositoryException;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.io.DocViewParser.XmlParseException;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests how importing not well-formed DocView XMLs behaves
 */
public class NotWellFormedDocViewXmlsIT extends IntegrationTestBase {

    @Override
    public void tearDown() throws Exception {
        try {
            if (admin.nodeExists("/testroot")) {
                admin.getNode("/testroot").remove();
                admin.save();
            }
        } finally {
            super.tearDown();
        }
    }

    @Test
    public void testHandlingTruncatedContentXMLWithNonStrictMode() throws Exception {
        assertNodeMissing("/testroot");
        ImportOptions options = getDefaultOptions();
        options.setStrict(false);
        CapturingProgressTrackerListener listener = new CapturingProgressTrackerListener();
        options.setListener(listener);
        extractVaultPackage("/test-packages/xml-nwf-truncated.zip", options);
        assertNodeExists("/testroot");
        assertEquals(1, listener.getErrorMap().size());
        Entry<String, Exception> firstEntry =
                listener.getErrorMap().entrySet().iterator().next();
        assertTrue(firstEntry.getValue() instanceof XmlParseException);
        assertEquals("/", firstEntry.getKey());
    }

    static final class CapturingProgressTrackerListener implements ProgressTrackerListener {

        private final Map<String, Exception> errorMap;

        public CapturingProgressTrackerListener() {
            errorMap = new HashMap<>();
        }

        @Override
        public void onMessage(Mode mode, String action, String path) {
            // ignore regular messages
        }

        @Override
        public void onError(Mode mode, String path, Exception e) {
            errorMap.put(path, e);
        }

        public Map<String, Exception> getErrorMap() {
            return errorMap;
        }
    }

    @Test
    public void testHandlingTruncatedContentXMLWithStrictMode() throws Exception {
        assertNodeMissing("/testroot");
        try {
            ImportOptions options = getDefaultOptions();
            options.setStrict(true);
            extractVaultPackage("/test-packages/xml-nwf-truncated.zip", options);
        } catch (RepositoryException expected) {
            // expected
        }
        assertNodeMissing("/testroot");
    }
}

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

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

/**
 * <code>PlatformNameTest</code>...
 *
 */
public class PlatformNameTest extends TestCase {

    private Map<String, String> names = new HashMap<String, String>();


    protected void setUp() throws Exception {
        names.put("test.jpg", "test.jpg");
        names.put("cq:content", "_cq_content");
        names.put("cq:test_image.jpg", "_cq_test_image.jpg");
        names.put("test_image.jpg", "test_image.jpg");
        names.put("_testimage.jpg", "_testimage.jpg");
        names.put("_test_image.jpg", "__test_image.jpg");
        names.put("cq:test:image.jpg", "_cq_test%3aimage.jpg");
        names.put("_cq_:test.jpg", "__cq_%3atest.jpg");
        names.put("_cq:test.jpg", "_cq%3atest.jpg");
        names.put("cq_:test.jpg", "cq_%3atest.jpg");
        names.put("_", "_");
        names.put(":", "%3a");
        names.put(":test", "%3atest");
    }

    public void testToPlatform() {
        for (String repName: names.keySet()) {
            String pfName = names.get(repName);
            assertEquals("Repo("+repName+")->Platform", pfName, PlatformNameFormat.getPlatformName(repName));
        }
    }

    public void testToRepo() {
        for (String repName: names.keySet()) {
            String pfName = names.get(repName);
            assertEquals("Platform("+pfName+")->Repo", repName, PlatformNameFormat.getRepositoryName(pfName));
        }
    }

}
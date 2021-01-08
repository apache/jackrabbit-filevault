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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PlatformNameTest {
    
    @Parameters(name = "platform: {0}, repo: {1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {     
             { "test.jpg", "test.jpg" },
             {"cq:content", "_cq_content"},
             {"cq:test_image.jpg", "_cq_test_image.jpg"},
             {"test_image.jpg", "test_image.jpg"},
             {"_testimage.jpg", "_testimage.jpg"},
             {"_test_image.jpg", "__test_image.jpg"},
             {"cq:test:image.jpg", "_cq_test%3aimage.jpg"},
             {"_cq_:test.jpg", "__cq_%3atest.jpg"},
             {"_cq:test.jpg", "_cq%3atest.jpg"},
             {"cq_:test.jpg", "cq_%3atest.jpg"},
             {"_", "_"},
             {":", "%3a"},
             {":test", "%3atest"},
             {":oak:mount-libs-nodetype", "%3aoak%3amount-libs-nodetype"}
       });
    }

    private final String repoName;
    private final String platformName;
    
    public PlatformNameTest(String repoName, String platformName) {
        this.repoName = repoName;
        this.platformName = platformName;
    }

    @Test
    public void toPlatform() {
        assertEquals(platformName, PlatformNameFormat.getPlatformName(repoName));
    }

    @Test
    public void toRepo() {
        assertEquals(repoName, PlatformNameFormat.getRepositoryName(platformName));
    }

}
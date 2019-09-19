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
package org.apache.jackrabbit.filevault.validation.impl.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ResettableInputStreamTest {

    @Parameters
    public static Collection<Object> data() throws IOException, URISyntaxException {
        return Arrays.asList(new Object[] {
                ResettableInputStreamTest.class.getResourceAsStream("/dummy.txt"),
                new FileInputStream(new File(ResettableInputStreamTest.class.getResource("/dummy.txt").toURI())),
                Files.newInputStream(Paths.get(ResettableInputStreamTest.class.getResource("/dummy.txt").toURI()))
        });
    }

    private final InputStream input;

    public ResettableInputStreamTest(InputStream input) {
        this.input = input;
    }

    @Test
    public void testWithBufferedInputStream() throws IOException {
        try (ResettableInputStream resettableInputStream = new ResettableInputStream(input)) {
            Assert.assertTrue(resettableInputStream.markSupported());
            Assert.assertEquals("some dummy text", IOUtils.toString(resettableInputStream, StandardCharsets.UTF_8));
            // this one should be reset to the beginning
            resettableInputStream.reset();
            Assert.assertEquals("some dummy text", IOUtils.toString(resettableInputStream, StandardCharsets.UTF_8));
        }
    }

    @After
    public void tearDown() {
        IOUtils.closeQuietly(input);
    }
}

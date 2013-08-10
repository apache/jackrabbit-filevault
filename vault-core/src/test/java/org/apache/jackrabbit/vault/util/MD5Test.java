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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

/**
 * <code>MD5Test</code>...
 */
public class MD5Test extends TestCase {

    private static String testData = "Hello, World";
    private static String testString = "82bb413746aee42f89dea2b59614f9ef";
    private static long testMSB = 0x82bb413746aee42fL;
    private static long testLSB = 0x89dea2b59614f9efL;
    private static byte[] testBytes = new byte[]{
            (byte) 0x82, (byte) 0xbb, (byte) 0x41, (byte) 0x37,
            (byte) 0x46, (byte) 0xae, (byte) 0xe4, (byte) 0x2f,
            (byte) 0x89, (byte) 0xde, (byte) 0xa2, (byte) 0xb5,
            (byte) 0x96, (byte) 0x14, (byte) 0xf9, (byte) 0xef
    };

    public void testCreateLong() {
        MD5 md5 = new MD5(testMSB, testLSB);
        assertEquals(testString, md5.toString());
        assertEquals(testBytes, md5.getBytes());
    }

    public void testCreateBytes() {
        MD5 md5 = new MD5(testBytes);
        assertEquals(testString, md5.toString());
        assertEquals(testMSB, md5.getMsb());
        assertEquals(testLSB, md5.getLsb());
    }

    public void testCreateString() {
        MD5 md5 = new MD5(testString);
        assertEquals(testMSB, md5.getMsb());
        assertEquals(testLSB, md5.getLsb());
    }

    public void testSmall() {
        MD5 md5 = new MD5(0, 0);
        assertEquals("00000000000000000000000000000000", md5.toString());
    }


    public void testDigest() throws IOException {
        InputStream in = new ByteArrayInputStream(testData.getBytes());
        MD5 md5 = MD5.digest(in);
        assertEquals(testString, md5.toString());
    }

    private void assertEquals(byte[] expected, byte[] result) {
        for (int i=0; i< expected.length; i++) {
            if (expected[i] != result[i]) {
                fail("expected: " + expected[i] + " but was:" + result[i]);
            }
        }
    }
}
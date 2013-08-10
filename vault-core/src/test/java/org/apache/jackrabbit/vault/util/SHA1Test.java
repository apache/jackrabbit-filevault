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
public class SHA1Test extends TestCase {

    private static String testData = "Hello, World\n";
    private static String testString = "4ab299c8ad6ed14f31923dd94f8b5f5cb89dfb54";
    private static int[] testInts = new int[]{0x4ab299c8, 0xad6ed14f, 0x31923dd9, 0x4f8b5f5c, 0xb89dfb54};
    private static byte[] testBytes = new byte[]{
            (byte) 0x4a, (byte) 0xb2, (byte) 0x99, (byte) 0xc8,
            (byte) 0xad, (byte) 0x6e, (byte) 0xd1, (byte) 0x4f,
            (byte) 0x31, (byte) 0x92, (byte) 0x3d, (byte) 0xd9,
            (byte) 0x4f, (byte) 0x8b, (byte) 0x5f, (byte) 0x5c,
            (byte) 0xb8, (byte) 0x9d, (byte) 0xfb, (byte) 0x54
    };

    public void testCreateInt() {
        SHA1 sha = new SHA1(testInts[0], testInts[1], testInts[2], testInts[3], testInts[4]);
        assertEquals(testString, sha.toString());
        assertEquals(testBytes, sha.getBytes());
    }

    public void testCreateBytes() {
        SHA1 sha = new SHA1(testBytes);
        for (int i=0; i<testInts.length; i++) {
            assertEquals("w" + i, testInts[i], sha.getInts()[i]);
        }
        assertEquals(testString, sha.toString());
    }

    public void testCreateString() {
        SHA1 sha = new SHA1(testString);
        for (int i=0; i<testInts.length; i++) {
            assertEquals("w" + i, testInts[i], sha.getInts()[i]);
        }
    }

    public void testSmall() {
        SHA1 sha = new SHA1(0, 0, 0, 0, 0);
        assertEquals("0000000000000000000000000000000000000000", sha.toString());
    }


    public void testDigest() throws IOException {
        InputStream in = new ByteArrayInputStream(testData.getBytes());
        SHA1 sha1 = SHA1.digest(in);
        assertEquals(testString, sha1.toString());
    }

    private void assertEquals(byte[] expected, byte[] result) {
        for (int i=0; i< expected.length; i++) {
            if (expected[i] != result[i]) {
                fail("expected: " + expected[i] + " but was:" + result[i]);
            }
        }
    }
}
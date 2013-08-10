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
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;

import junit.framework.TestCase;

/**
 * <code>LineOutputTest</code>...
 *
 */
public class LineInputTest extends TestCase {

    public void testInput0() throws Exception {
        byte[] in = new byte[]{0,0,0,0x0a,0,0,0,0x0a,0,0,0};
        byte[] u = new byte[]{0,0,0,0x0a,0,0,0,0x0a,0,0,0};
        byte[] w = new byte[]{0,0,0,0x0d,0x0a,0,0,0,0x0d,0x0a,0,0,0};
        doTest(in, u, w);
    }

    public void testInput1() throws Exception {
        byte[] in = new byte[]{0,0,0,0x0a,0,0,0,0x0a,0,0,0x0a};
        byte[] u = new byte[]{0,0,0,0x0a,0,0,0,0x0a,0,0,0x0a};
        byte[] w = new byte[]{0,0,0,0x0d,0x0a,0,0,0,0x0d,0x0a,0,0,0x0d,0x0a};
        doTest(in, u, w);
    }

    public void testInput2() throws Exception {
        byte[] in = new byte[]{0,0,0,0x0a,0x0d,0,0,0,0x0a,0x0d,0,0,0x0a,0x0d};
        byte[] u = new byte[]{0,0,0,0x0a,0,0,0,0x0a,0,0,0x0a};
        byte[] w = new byte[]{0,0,0,0x0d,0x0a,0,0,0,0x0d,0x0a,0,0,0x0d,0x0a};
        doTest(in, u, w);
    }

    public void testInput3() throws Exception {
        byte[] in = new byte[]{0,0,0,0x0a,0x0a,0,0,0,0x0d,0x0d,0,0,0x0a,0x0d};
        byte[] u = new byte[]{0,0,0,0x0a,0x0a,0,0,0,0x0a,0x0a,0,0,0x0a};
        byte[] w = new byte[]{0,0,0,0x0d,0x0a,0x0d,0x0a,0,0,0,0x0d,0x0a,0x0d,0x0a,0,0,0x0d,0x0a};
        doTest(in, u, w);
    }

    public void testInput4() throws Exception {
        byte[] in = "bla */\r\n\r\n/** bla */\r\n".getBytes();
        byte[] u = "bla */\n\n/** bla */\n".getBytes();
        byte[] w = "bla */\r\n\r\n/** bla */\r\n".getBytes();
        doTest(in, u, w);
    }

    public void testInput5() throws Exception {
        byte[] in = "bla */\n\n\n/** bla */\n".getBytes();
        byte[] u = "bla */\n\n\n/** bla */\n".getBytes();
        byte[] w = "bla */\r\n\r\n\r\n/** bla */\r\n".getBytes();
        doTest(in, u, w);
    }

    public void testInput6() throws Exception {
        byte[] in = "bla */\r\n\r\n\r\n\r\n/** bla */\r\n".getBytes();
        byte[] u = "bla */\n\n\n\n/** bla */\n".getBytes();
        byte[] w = "bla */\r\n\r\n\r\n\r\n/** bla */\r\n".getBytes();
        doTest(in, u, w);
    }

    public void testLarge1() throws Exception {
        byte[] in = new byte[3*8192];
        Arrays.fill(in, (byte) 20);
        in[8192] = 0xa;
        byte[] u = new byte[3*8192];
        Arrays.fill(u, (byte) 20);
        u[8192] = 0xa;
        byte[] w = new byte[3*8192 + 1];
        Arrays.fill(w, (byte) 20);
        w[8192] = 0xd;
        w[8193] = 0xa;
        doTest(in, u, w);
    }

    public void testLarge2() throws Exception {
        byte[] in = new byte[3*8192];
        Arrays.fill(in, (byte) 20);
        in[8191] = 0xa;
        byte[] u = new byte[3*8192];
        Arrays.fill(u, (byte) 20);
        u[8191] = 0xa;
        byte[] w = new byte[3*8192 + 1];
        Arrays.fill(w, (byte) 20);
        w[8191] = 0xd;
        w[8192] = 0xa;
        doTest(in, u, w);
    }

    public void testLarge3() throws Exception {
        byte[] in = new byte[3*8192];
        Arrays.fill(in, (byte) 20);
        in[8193] = 0xa;
        byte[] u = new byte[3*8192];
        Arrays.fill(u, (byte) 20);
        u[8193] = 0xa;
        byte[] w = new byte[3*8192 + 1];
        Arrays.fill(w, (byte) 20);
        w[8193] = 0xd;
        w[8194] = 0xa;
        doTest(in, u, w);
    }

    private void doTest(byte[] in, byte[] expectUnix, byte[] expectWindows) throws Exception {
        check(in, expectUnix, LineInputStream.LS_UNIX);
        check(in, expectWindows, LineInputStream.LS_WINDOWS);
    }

    private void check(byte[] in, byte[] expect, byte[] ls) throws Exception {
        ByteArrayInputStream src = new ByteArrayInputStream(in);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();

        LineInputStream lis = new LineInputStream(src, ls);
        IOUtils.copy(lis, bo);
        lis.close();

        byte[] result = bo.toByteArray();

        StringBuffer hexIn = new StringBuffer();
        for (byte b: expect) {
            hexIn.append(String.format("%x, ", b));
        }
        StringBuffer hexOut = new StringBuffer();
        for (byte b: result) {
            hexOut.append(String.format("%x, ", b));
        }
        assertEquals(hexIn.toString(), hexOut.toString());
    }
}
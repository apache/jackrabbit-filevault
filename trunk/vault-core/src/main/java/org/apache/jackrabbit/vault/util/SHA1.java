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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.io.FileUtils;

/**
 * SHA1 abstraction
 */
public class SHA1 {

    public final static SHA1 NULL = new SHA1(0,0,0,0,0);

    private final int w0;

    private final int w1;

    private final int w2;

    private final int w3;

    private final int w4;

    public SHA1(int w0, int w1, int w2, int w3, int w4) {
        this.w0 = w0;
        this.w1 = w1;
        this.w2 = w2;
        this.w3 = w3;
        this.w4 = w4;
    }

    public SHA1(String str) {
        if (str.length() != 40) {
            throw new IllegalArgumentException("invalid string length " + str.length());
        }
        w0 = (int) Long.parseLong(str.substring(0x00, 0x08), 16);
        w1 = (int) Long.parseLong(str.substring(0x08, 0x10), 16);
        w2 = (int) Long.parseLong(str.substring(0x10, 0x18), 16);
        w3 = (int) Long.parseLong(str.substring(0x18, 0x20), 16);
        w4 = (int) Long.parseLong(str.substring(0x20, 0x28), 16);
    }

    public SHA1(byte[] bytes) {
        if (bytes.length != 20) {
            throw new IllegalArgumentException("invalid bytes length " + bytes.length);
        }
        w0 = getInt(bytes, 0);
        w1 = getInt(bytes, 4);
        w2 = getInt(bytes, 8);
        w3 = getInt(bytes, 12);
        w4 = getInt(bytes, 16);
    }

    public int[] getInts() {
        return new int[]{w0, w1, w2, w3, w4};
    }

    public byte[] getBytes() {
        byte[] buf = new byte[20];
        setInt(buf, 0, w0);
        setInt(buf, 4, w1);
        setInt(buf, 8, w2);
        setInt(buf, 12, w3);
        setInt(buf, 16, w4);
        return buf;
    }

    public static SHA1 digest(InputStream in) throws IOException {
        try {
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalArgumentException(e.toString());
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                md.update(buffer, 0, read);
            }
            return new SHA1(md.digest());
        } finally {
            in.close();
        }
    }

    public static SHA1 digest(File file) throws IOException {
        return digest(FileUtils.openInputStream(file));
    }

    public String toString() {
        return String.format("%08x%08x%08x%08x%08x", w0, w1, w2, w3, w4);
    }

    public int hashCode() {
        return w2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SHA1 sha1 = (SHA1) o;
        return w0 == sha1.w0 && w1 == sha1.w1 && w2 == sha1.w2 && w3 == sha1.w3 && w4 == sha1.w4;
    }

    private static int getInt(byte[] b, int offs) {
        return ((b[    offs] & 0xFF) << 24) +
               ((b[1 + offs] & 0xFF) << 16) +
               ((b[2 + offs] & 0xFF) << 8) +
               ((b[3 + offs] & 0xFF));
    }

    private static void setInt(byte[] b, int offs, int v) {
        b[offs  ] = (byte) ((v >>> 24) & 0xFF);
        b[offs+1] = (byte) ((v >>> 16) & 0xFF);
        b[offs+2] = (byte) ((v >>>  8) & 0xFF);
        b[offs+3] = (byte) (v & 0xFF);
    }


}
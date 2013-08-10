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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * <code>MD5</code>...
 */
public class MD5 {

    private final long msb;

    private final long lsb;

    public MD5(long msb, long lsb) {
        this.msb = msb;
        this.lsb = lsb;
    }

    public MD5(String str) {
        if (str.length() != 32) {
            throw new IllegalArgumentException("invalid string length " + str.length());
        }
        msb = (Long.parseLong(str.substring(0, 8), 16) << 32)
                + (Long.parseLong(str.substring(8, 16), 16));
        lsb = (Long.parseLong(str.substring(16, 24), 16) << 32)
                + (Long.parseLong(str.substring(24, 32), 16));
    }

    public MD5(byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("invalid bytes length " + bytes.length);
        }
        msb = getLong(bytes, 0);
        lsb = getLong(bytes, 8);
    }

    public long[] getLongs() {
        return new long[]{msb, lsb};
    }

    public long getMsb() {
        return msb;
    }

    public long getLsb() {
        return lsb;
    }

    public byte[] getBytes() {
        byte[] buf = new byte[16];
        setLong(buf, 0, msb);
        setLong(buf, 8, lsb);
        return buf;
    }

    public static MD5 digest(InputStream in) throws IOException {
        try {
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("md5");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalArgumentException(e.toString());
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                md.update(buffer, 0, read);
            }
            return new MD5(md.digest());
        } finally {
            in.close();
        }
    }

    public static MD5 digest(File file) throws IOException {
        return digest(new FileInputStream(file));
    }

    public String toString() {
        return String.format("%016x%016x", msb, lsb);
    }

    public int hashCode() {
        return (int)((msb >> 32) ^ msb ^ (lsb >> 32) ^ lsb);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MD5 md5 = (MD5) o;
        return lsb == md5.lsb && msb == md5.msb;
    }

    private static long getLong(byte[] b, int offs) {
        return ((long)  (b[offs] & 0xFF) << 56) +
                ((long) (b[1 + offs] & 0xFF) << 48) +
                ((long) (b[2 + offs] & 0xFF) << 40) +
                ((long) (b[3 + offs] & 0xFF) << 32) +
                ((long) (b[4 + offs] & 0xFF) << 24) +
                ((long) (b[5 + offs] & 0xFF) << 16) +
                ((long) (b[6 + offs] & 0xFF) << 8) +
                ((long) (b[7 + offs] & 0xFF));
    }

    private static void setLong(byte[] b, int offs, long v) {
        b[offs]   = (byte) ((v >>> 56) & 0xFF);
        b[offs+1] = (byte) ((v >>> 48) & 0xFF);
        b[offs+2] = (byte) ((v >>> 40) & 0xFF);
        b[offs+3] = (byte) ((v >>> 32) & 0xFF);
        b[offs+4] = (byte) ((v >>> 24) & 0xFF);
        b[offs+5] = (byte) ((v >>> 16) & 0xFF);
        b[offs+6] = (byte) ((v >>>  8) & 0xFF);
        b[offs+7] = (byte) ((v >>>  0) & 0xFF);
    }


}
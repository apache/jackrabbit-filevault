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


package org.apache.jackrabbit.vault.util.xml.serialize;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;

import org.apache.jackrabbit.vault.util.xml.xerces.util.EncodingMap;

/**
 * This class represents an encoding.
 *
 * @version $Id$
 */
public class EncodingInfo {

    // Method: sun.io.CharToByteConverter.getConverter(java.lang.String)
    private static java.lang.reflect.Method fgGetConverterMethod = null;

    // Method: sun.io.CharToByteConverter.canConvert(char)
    private static java.lang.reflect.Method fgCanConvertMethod = null;

    // Flag indicating whether or not sun.io.CharToByteConverter is available.
    private static boolean fgConvertersAvailable = false;

    // An array to hold the argument for a method of CharToByteConverter.
    private Object[] fArgsForMethod = null;

    // name of encoding as registered with IANA;
    // preferably a MIME name, but aliases are fine too.
    String ianaName;
    String javaName;
    int lastPrintable;

    // The charToByteConverter with which we test unusual characters.
    Object fCharToByteConverter = null;

    // Is the converter null because it can't be instantiated
    // for some reason (perhaps we're running with insufficient authority as 
    // an applet?
    boolean fHaveTriedCToB = false;
    Charset nioCharset = null;
    CharsetEncoder nioCharEncoder = null;

    /**
     * Creates new <code>EncodingInfo</code> instance.
     */
    public EncodingInfo(String ianaName, String javaName, int lastPrintable) {
        this.ianaName = ianaName;
        this.javaName = EncodingMap.getIANA2JavaMapping(ianaName);
        this.lastPrintable = lastPrintable;
        try {
            nioCharset = Charset.forName(this.javaName);
            if (nioCharset.canEncode())
                nioCharEncoder = nioCharset.newEncoder();
        } catch (IllegalCharsetNameException ie) {
            nioCharset = null;
            nioCharEncoder = null;
        } catch (UnsupportedCharsetException ue) {
            nioCharset = null;
            nioCharEncoder = null;
        }
    }

    /**
     * Returns a MIME charset name of this encoding.
     */
    public String getIANAName() {
        return this.ianaName;
    }

    /**
     * Returns a writer for this encoding based on
     * an output stream.
     *
     * @return A suitable writer
     * @throws UnsupportedEncodingException There is no convertor
     *                                      to support this encoding
     */
    public Writer getWriter(OutputStream output)
            throws UnsupportedEncodingException {
        // this should always be true!
        if (javaName != null)
            return new OutputStreamWriter(output, javaName);
        javaName = EncodingMap.getIANA2JavaMapping(ianaName);
        if (javaName == null)
            // use UTF-8 as preferred encoding
            return new OutputStreamWriter(output, "UTF8");
        return new OutputStreamWriter(output, javaName);
    }

    /**
     * Checks whether the specified character is printable or not
     * in this encoding.
     *
     * @param ch a code point (0-0x10ffff)
     */
    public boolean isPrintable(char ch) {
        if (ch <= this.lastPrintable)
            return true;
        if (nioCharEncoder != null)
            return nioCharEncoder.canEncode(ch);

        //We should not reach here , if we reach due to
        //charset not supporting encoding then fgConvertersAvailable
        //should take care of returning false.

        if (fCharToByteConverter == null) {
            if (fHaveTriedCToB || !fgConvertersAvailable) {
                // forget it; nothing we can do...
                return false;
            }
            if (fArgsForMethod == null) {
                fArgsForMethod = new Object[1];
            }
            // try and create it:
            try {
                fArgsForMethod[0] = javaName;
                fCharToByteConverter = fgGetConverterMethod.invoke(null, fArgsForMethod);
            } catch (Exception e) {
                // don't try it again...
                fHaveTriedCToB = true;
                return false;
            }
        }
        try {
            fArgsForMethod[0] = new Character(ch);
            return ((Boolean) fgCanConvertMethod.invoke(fCharToByteConverter, fArgsForMethod)).booleanValue();
        } catch (Exception e) {
            // obviously can't use this converter; probably some kind of
            // security restriction
            fCharToByteConverter = null;
            fHaveTriedCToB = false;
            return false;
        }
    }

    // is this an encoding name recognized by this JDK?
    // if not, will throw UnsupportedEncodingException
    public static void testJavaEncodingName(String name) throws UnsupportedEncodingException {
        final byte[] bTest = {(byte) 'v', (byte) 'a', (byte) 'l', (byte) 'i', (byte) 'd'};
        String s = new String(bTest, name);
    }

    // Attempt to get methods for char to byte 
    // converter on class initialization.
    static {
        try {
            Class clazz = Class.forName("sun.io.CharToByteConverter");
            fgGetConverterMethod = clazz.getMethod("getConverter", new Class[]{String.class});
            fgCanConvertMethod = clazz.getMethod("canConvert", new Class[]{Character.TYPE});
            fgConvertersAvailable = true;
        }
        // ClassNotFoundException, NoSuchMethodException or SecurityException
        // Whatever the case, we cannot use sun.io.CharToByteConverter.
        catch (Exception exc) {
            fgGetConverterMethod = null;
            fgCanConvertMethod = null;
            fgConvertersAvailable = false;
        }
    }
}

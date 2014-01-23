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

package org.apache.jackrabbit.vault.fs.config;

import java.io.ByteArrayOutputStream;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.jcr.Credentials;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * <code>SimpleCredentialsConfig</code>...
*
*/
public class SimpleCredentialsConfig extends CredentialsConfig {

    /**
     * key length
     */
    private final static int KEY_LENGTH = 8;

    /**
     * encryption prefix
     */
    private final static String PREFIX = "{DES}";

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(SimpleCredentialsConfig.class);

    private final SimpleCredentials creds;
    public static final String ELEM_USER = "user";
    public static final String ATTR_NAME = "name";
    public static final String ATTR_PASSWORD = "password";

    public SimpleCredentialsConfig(SimpleCredentials creds) {
        super("simple");
        this.creds = creds;
    }

    public Credentials getCredentials() {
        return creds;
    }

    public static SimpleCredentialsConfig load(Element elem) throws ConfigurationException {
        assert elem.getNodeName().equals(ELEM_CREDETIALS);

        NodeList nl = elem.getChildNodes();
        for (int i=0; i<nl.getLength(); i++) {
            Node child = nl.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (child.getNodeName().equals(ELEM_USER)) {
                    Element e = (Element) child;
                    String name = e.getAttribute(ATTR_NAME);
                    String pass = decrypt(e.getAttribute(ATTR_PASSWORD));
                    return new SimpleCredentialsConfig(
                            new SimpleCredentials(
                                    name,
                                    pass == null ? new char[0] : pass.toCharArray()));
                }
            }
        }
        throw new ConfigurationException("mandatory element <user> missing.");
    }

    public void writeInner(ContentHandler handler) throws SAXException {
        if (creds != null) {
            AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute("", ATTR_NAME, "", "CDATA", creds.getUserID());
            attrs.addAttribute("", ATTR_PASSWORD, "", "CDATA", encrypt(new String(creds.getPassword())));
            handler.startElement("", ELEM_USER, "", attrs);
            handler.endElement("", ELEM_USER, "");
        }
    }

    /**
     * Encrypts the given string in a fairly secure way so that it can be
     * {@link #decrypt(String) decrypted} again.
     *
     * @param s string to encrypt
     * @return the encrypted string with a "{AES}" prefix.
     */
    private static String encrypt(String s) {
        try {
            SecretKey key = KeyGenerator.getInstance("DES").generateKey();
            Cipher cipher = Cipher.getInstance("DES");
            byte[] keyBytes = key.getEncoded();
            byte[] data = s.getBytes("utf-8");
            ByteArrayOutputStream out = new ByteArrayOutputStream(keyBytes.length + data.length);
            out.write(keyBytes);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            out.write(cipher.update(data));
            out.write(cipher.doFinal());
            StringBuilder ret = new StringBuilder(PREFIX);
            for (byte b: out.toByteArray()) {
                ret.append(Text.hexTable[b>>4 & 0x0f]).append(Text.hexTable[b&0x0f]);
            }
            return ret.toString();
        } catch (Exception e) {
            log.warn("Unable to encrypt string: " + e);
            return null;
        }
    }

    /**
     * Decrypts a string that was previously {@link #encrypt(String)} encrypted}.
     *
     * @param s the data to decrypt
     * @return the string or <code>null</code> if an internal error occurred
     */
    private static String decrypt(String s) {
        if (s == null || !s.startsWith(PREFIX)) {
            return s;
        }
        try {
            byte[] data = new byte[(s.length() - PREFIX.length())/2];
            for (int i=PREFIX.length(),b=0; i<s.length(); i+=2, b++) {
                data[b] = (byte) (Integer.parseInt(s.substring(i, i+2), 16) &0xff);
            }
            SecretKeySpec key = new SecretKeySpec(data, 0, KEY_LENGTH, "DES");
            Cipher cipher = Cipher.getInstance("DES");
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
            cipher.init(Cipher.DECRYPT_MODE, key);
            out.write(cipher.update(data, KEY_LENGTH, data.length - KEY_LENGTH));
            out.write(cipher.doFinal());
            return out.toString("utf-8");
        } catch (Exception e) {
            log.warn("Unable to decrypt data: " + e);
            return null;
        }
    }

}
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

import javax.jcr.Credentials;
import javax.jcr.SimpleCredentials;

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
                    String pass = e.getAttribute(ATTR_PASSWORD);
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
            attrs.addAttribute("", ATTR_PASSWORD, "", "CDATA", new String(creds.getPassword()));
            handler.startElement("", ELEM_USER, "", attrs);
            handler.endElement("", ELEM_USER, "");
        }
    }
}
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

import org.w3c.dom.Element;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * <code>CredentialsConfig</code>...
*
*/
public abstract class CredentialsConfig {

    public final String type;
    public static final String ATTR_TYPE = "type";
    public static final String ELEM_CREDETIALS = "credentials";

    public CredentialsConfig(String type) {
        this.type = type;
    }

    public static CredentialsConfig load(Element elem) throws ConfigurationException {
        assert elem.getNodeName().equals(ELEM_CREDETIALS);

        String type = elem.getAttribute(ATTR_TYPE);
        if (type == null || type.equals("simple")) {
            return SimpleCredentialsConfig.load(elem);
        }
        throw new ConfigurationException("unknown credentials type: " + type);
    }

    public abstract Credentials getCredentials();

    public void write(ContentHandler handler) throws SAXException {
        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", ATTR_TYPE, "", "CDATA", type);
        handler.startElement("", ELEM_CREDETIALS, "", attrs);
        writeInner(handler);
        handler.endElement("", ELEM_CREDETIALS, "");
    }

    protected abstract void writeInner(ContentHandler handler) throws SAXException;
}
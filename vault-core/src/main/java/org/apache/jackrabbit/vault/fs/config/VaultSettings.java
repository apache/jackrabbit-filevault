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

import java.util.HashSet;
import java.util.Set;

import org.w3c.dom.Element;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * <code>VaultUserConfig</code>...
 *
 */
public class VaultSettings extends AbstractConfig {

    public static final String ELEM_IGNORE = "ignore";

    public static final String ATTR_IGNORE_NAME = "name";

    private Set<String> ignores = new HashSet<String>();

    public Set<String> getIgnoredNames() {
        return ignores;
    }

    protected void doLoad(Element child) throws ConfigurationException {
        if (child.getNodeName().equals(ELEM_IGNORE)) {
            loadIgnore(child);
        } else {
            throw new ConfigurationException("unexpected element: " + child.getLocalName());
        }
    }

    public boolean isIgnored(String name) {
        return ignores.contains(name);
    }

    private void loadIgnore(Element ignore) {
        String name = ignore.getAttribute(ATTR_IGNORE_NAME);
        if (name != null) {
            ignores.add(name);
        }
    }

    protected String getRootElemName() {
        return "vault";
    }

    protected double getSupportedVersion() {
        return 1.0;
    }

    protected void doWrite(ContentHandler handler) throws SAXException {
        for (String ignore: ignores) {
            AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute("", ATTR_IGNORE_NAME, "", "CDATA", ignore);
            handler.startElement("", ELEM_IGNORE, "", attrs);
            handler.endElement("", ELEM_IGNORE, "");
        }
    }

    public static VaultSettings createDefault() {
        VaultSettings s = new VaultSettings();
        s.ignores.add(".svn");
        return s;
    }
}
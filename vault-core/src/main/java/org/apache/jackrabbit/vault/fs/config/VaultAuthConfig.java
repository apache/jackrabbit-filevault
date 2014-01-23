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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.jackrabbit.vault.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * <code>VaultUserConfig</code>...
 *
 */
public class VaultAuthConfig extends AbstractConfig {

    protected static Logger log = LoggerFactory.getLogger(VaultAuthConfig.class);

    public static final String ELEM_REPOSITORY = "repository";
    public static final String ATTR_URI = "uri";

    private final Map<String, RepositoryConfig> repoConfigs = new HashMap<String, RepositoryConfig>();

    protected void doLoad(Element child) throws ConfigurationException {
        if (!child.getNodeName().equals(ELEM_REPOSITORY)) {
            throw new ConfigurationException("unexpected element: " + child.getLocalName());
        }
        RepositoryConfig cfg = RepositoryConfig.load(child);
        if (cfg != null) {
            repoConfigs.put(cfg.uri, cfg);
        }
    }

    protected String getRootElemName() {
        return "auth";
    }

    protected double getSupportedVersion() {
        return 1.0;
    }

    public RepositoryConfig getRepoConfig(String uri) {
        return repoConfigs.get(uri);
    }

    public void addRepositoryConfig(RepositoryConfig cfg) {
        repoConfigs.put(cfg.uri, cfg);
    }

    protected void doWrite(ContentHandler handler) throws SAXException {
        for (RepositoryConfig cfg: repoConfigs.values()) {
            cfg.write(handler);
        }
    }

    public boolean load() throws IOException, ConfigurationException {
        return load(new File(getConfigDir(), Constants.AUTH_XML));
    }

    public void save() throws IOException {
        save(getConfigFile());
    }

    public File getConfigFile() throws IOException {
        return new File(getConfigDir(), Constants.AUTH_XML);
    }

    public static class RepositoryConfig {

        public final String uri;

        public CredentialsConfig creds;

        public RepositoryConfig(String uri) {
            this.uri = uri;
        }

        public CredentialsConfig getCredsConfig() {
            return creds;
        }

        public void addCredsConfig(CredentialsConfig creds) {
            this.creds = creds;
        }

        public static RepositoryConfig load(Element elem) throws ConfigurationException {
            assert elem.getNodeName().equals(ELEM_REPOSITORY);

            String uri = elem.getAttribute(ATTR_URI);
            if (uri == null) {
                throw new ConfigurationException("missing attribute: " + ATTR_URI);
            }
            RepositoryConfig cfg = new RepositoryConfig(uri);
            NodeList nl = elem.getChildNodes();
            for (int i=0; i<nl.getLength(); i++) {
                Node child = nl.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    if (child.getNodeName().equals(CredentialsConfig.ELEM_CREDETIALS)) {
                        CredentialsConfig credentialsConfig = CredentialsConfig.load((Element) child);
                        if (credentialsConfig != null) {
                            cfg.creds = credentialsConfig;
                        }
                    } else {
                        throw new ConfigurationException("unexpected element: " + child.getLocalName());
                    }
                }
            }
            return cfg;
        }

        public void write(ContentHandler handler) throws SAXException {
            AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute("", ATTR_URI, "", "CDATA", uri);
            handler.startElement("", ELEM_REPOSITORY, "", attrs);
            creds.write(handler);
            handler.endElement("", ELEM_REPOSITORY, "");
        }
    }

}
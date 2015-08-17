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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.Aggregator;
import org.apache.jackrabbit.vault.fs.api.ArtifactHandler;
import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.util.RejectingEntityResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * <code>JcrFsConfig</code>...
 */
public abstract class AbstractVaultFsConfig implements VaultFsConfig {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(AbstractVaultFsConfig.class);

    public static final String ATTR_VERSION = "version";

    private List<Aggregator> aggregators = new ArrayList<Aggregator>();

    private List<ArtifactHandler> handlers = new ArrayList<ArtifactHandler>();

    private Map<String, String> properties = new HashMap<String, String>();

    private byte[] source;

    private String name = "";

    public static VaultFsConfig load(File file)
            throws ConfigurationException, IOException {
        return load(new FileInputStream(file), file.getName());
    }

    public static VaultFsConfig load(InputStream in, String name)
            throws ConfigurationException, IOException {
        try {
            byte[] source = IOUtils.toByteArray(in);
            Document document = parse(new ByteArrayInputStream(source));

            Element doc = document.getDocumentElement();
            if (!doc.getNodeName().equals("vaultfs")) {
                throw new ConfigurationException("<vaultfs> expected.");
            }
            String v = doc.getAttribute(ATTR_VERSION);
            if (v == null || v.equals("")) {
                v = "1.0";
            }
            double version = Double.parseDouble(v);
            AbstractVaultFsConfig config;
            if (version != VaultFsConfig11.SUPPORTED_VERSION) {
                throw new ConfigurationException("version " + version + " not supported.");
            } else {
                config = new VaultFsConfig11();
            }
            config.setSource(source);
            config.setName(name);
            config.process(doc);
            return config;
        } finally {
            IOUtils.closeQuietly(in);
        }

    }

    protected Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public String getProperty(String name) {
        return properties.get(name);
    }

    protected abstract void process(Element doc) throws ConfigurationException;

    private void setSource(byte[] bytes) {
        this.source = bytes;
    }

    private void setName(String name) {
        this.name = name;
    }

    public InputStream getSource() {
        return new ByteArrayInputStream(source);
    }

    public String getSourceAsString() {
        try {
            return new String(source, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Aggregator> getAggregators() {
        return aggregators;
    }

    public List<ArtifactHandler> getHandlers() {
        return handlers;
    }

    private static Document parse(InputStream xml)
            throws ConfigurationException, IOException {
        try {
            DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            // disable DTD loading (bug #36897)
            builder.setEntityResolver(new RejectingEntityResolver());
            return builder.parse(xml);
        } catch (ParserConfigurationException e) {
            throw new ConfigurationException(
                    "Unable to create configuration XML parser", e);
        } catch (SAXException e) {
            throw new ConfigurationException(
                    "Configuration file syntax error.", e);
        }
    }

    protected static Collection<Element> getChildElements(Node elem) {
        NodeList nodeList = elem.getChildNodes();
        List<Element> nodes = new ArrayList<Element>(nodeList.getLength());
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                nodes.add((Element) child);
            }
        }
        return nodes;
    }

    protected void fail(String msg, Node elem) throws ConfigurationException {
        String path = "";
        while (elem != null && elem.getNodeType() != Node.DOCUMENT_NODE) {
            path = " > " + elem.getNodeName() + path;
            elem = elem.getParentNode();
        }
        throw new ConfigurationException(msg + ". Location: " + name + path);
    }

}
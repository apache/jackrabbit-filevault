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
package org.apache.jackrabbit.vault.vlt.meta.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.jackrabbit.vault.util.xml.serialize.OutputFormat;
import org.apache.jackrabbit.vault.util.xml.serialize.XMLSerializer;
import org.apache.jackrabbit.vault.vlt.VltException;
import org.apache.jackrabbit.vault.vlt.VltFile;
import org.apache.jackrabbit.vault.vlt.meta.VltEntries;
import org.apache.jackrabbit.vault.vlt.meta.VltEntry;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * <code>Entries</code>...
 *
 */
public class XmlEntries implements VltEntries {

    public static final String EN_ENTRIES = "entries";

    public static final String AN_PATH = "path";

    public static final String AN_AGGREGATE_PATH = "aggregatePath";

    private final String path;

    private Map<String, VltEntry> entries = new HashMap<String, VltEntry>();

    private boolean dirty;

    public XmlEntries(String path) {
        this.path = path;
    }

    public XmlEntries(String path, boolean dirty) {
        this.path = path;
        this.dirty = dirty;
    }

    public String getPath() {
        return path;
    }
    
    public static XmlEntries load(InputStream in) throws VltException {
        InputSource source = new InputSource(in);
        return load(source);
    }

    public static XmlEntries load(InputSource source) throws VltException {
        try {
            DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(source);
            Element doc = document.getDocumentElement();
            if (!doc.getNodeName().equals(EN_ENTRIES)) {
                throw new VltException(source.getSystemId(), "<entries> expected.");
            }
            // get uri
            String path = doc.getAttribute(AN_PATH);
            XmlEntries entries = new XmlEntries(path);

            // get entries
            NodeList nodes = doc.getChildNodes();
            for (int i=0; i<nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node instanceof Element) {
                    Element elem = (Element) node;
                    if (elem.getNodeName().equals(XmlEntry.EN_ENTRY)) {
                        XmlEntry entry = XmlEntry.load(elem);
                        entries.entries.put(entry.getName(), entry);
                    } else {
                        throw new VltException(source.getSystemId(),
                                "<entry> expected in <entries> element.");
                    }
                }
            }
            entries.dirty = false;
            return entries;
        } catch (ParserConfigurationException e) {
            throw new VltException(source.getSystemId(),
                    "Unable to create configuration XML parser", e);
        } catch (SAXException e) {
            throw new VltException(source.getSystemId(),
                    "Configuration file syntax error.", e);
        } catch (IOException e) {
            throw new VltException(source.getSystemId(),
                    "Configuration file could not be read.", e);
        }
    }

    /*
    public void save(File file) throws VltException {
        if (file.exists() && !isDirty()) {
            return;
        }
        try {
            FileOutputStream out = new FileOutputStream(file);
            save(out);
            out.close();
        } catch (IOException e) {
            throw new VltException(file.getPath(), "Error while saving", e);
        }
    }
    */

    public void save(OutputStream out) throws IOException {
        OutputFormat fmt = new OutputFormat("xml", "UTF-8", true);
        fmt.setLineWidth(0);
        fmt.setIndent(2);
        XMLSerializer ser = new XMLSerializer(out, fmt);
        try {
            write(ser);
        } catch (SAXException e) {
            throw new IOException(e.toString());
        }
    }

    private void write(ContentHandler handler) throws SAXException {
        handler.startDocument();
        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", AN_PATH, "", "CDATA", path);
        handler.startElement("", EN_ENTRIES, "", attrs);
        for (VltEntry e: entries.values()) {
            ((XmlEntry) e).write(handler);
        }
        handler.endElement("", EN_ENTRIES, "");
        handler.endDocument();
        dirty = false;
    }

    public void update(VltFile file) {
        VltEntry e = file.getEntry();
        if (e == null) {
            entries.remove(file.getName());
            dirty = true;
        } else {
            putEntry(e);
        }
    }

    public void putEntry(VltEntry e) {
        if (entries.get(e.getName()) != e) {
            dirty = true;
            entries.put(e.getName(), e);
        }
    }

    public VltEntry getEntry(String localName) {
        return entries.get(localName);
    }

    public VltEntry update(String localName, String aggregatePath, String repoRelPath) {
        XmlEntry e = new XmlEntry(localName, aggregatePath, repoRelPath);
        VltEntry old = entries.remove(localName);
        if (old != null) {
            e.put(old.work());
            e.put(old.base());
            e.put(old.mine());
            e.put(old.theirs());
        }
        putEntry(e);
        return e;
    }

    public boolean hasEntry(String localName) {
        return entries.containsKey(localName);
    }

    public Collection<VltEntry> entries() {
        return entries.values();
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
    
    public boolean isDirty() {
        if (dirty) {
            return true;
        }
        for (VltEntry e: entries.values()) {
            if (e.isDirty()) {
                return dirty = true;
            }
        }
        return false;
    }
}
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.Dumpable;
import org.apache.jackrabbit.vault.fs.api.FilterSet;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.PathMapping;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.fs.spi.ProgressTracker;
import org.apache.jackrabbit.vault.util.RejectingEntityResolver;
import org.apache.jackrabbit.vault.util.Tree;
import org.apache.jackrabbit.vault.util.xml.serialize.OutputFormat;
import org.apache.jackrabbit.vault.util.xml.serialize.XMLSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Holds a list of {@link PathFilterSet}s.
 *
 */
public class DefaultWorkspaceFilter implements Dumpable, WorkspaceFilter {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultWorkspaceFilter.class);

    private final List<PathFilterSet> filterSets = new LinkedList<PathFilterSet>();

    public static final String ATTR_VERSION = "version";

    public static final double SUPPORTED_VERSION = 1.0;

    protected double version = SUPPORTED_VERSION;

    private byte[] source;

    /**
     * globally ignored paths. they are not persisted, yet
     */
    private PathFilter globalIgnored;

    /**
     * global import mode override (e.g. for snapshot restores)
     */
    private ImportMode importMode;

    public void add(PathFilterSet set) {
        filterSets.add(set);
    }

    /**
     * {@inheritDoc}
     */
    public List<PathFilterSet> getFilterSets() {
        return filterSets;
    }

    /**
     * {@inheritDoc}
     */
    public PathFilterSet getCoveringFilterSet(String path) {
        if (isGloballyIgnored(path)) {
            return null;
        }
        for (PathFilterSet set: filterSets) {
            if (set.covers(path)) {
                return set;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public ImportMode getImportMode(String path) {
        if (importMode != null) {
            return importMode;
        }
        FilterSet set = getCoveringFilterSet(path);
        return set == null ? ImportMode.REPLACE : set.getImportMode();
    }

    public void setImportMode(ImportMode importMode) {
        this.importMode = importMode;
    }

    /**
     * {@inheritDoc}
     */
    public boolean contains(String path) {
        if (isGloballyIgnored(path)) {
            return false;
        }
        for (PathFilterSet set: filterSets) {
            if (set.contains(path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean covers(String path) {
        if (isGloballyIgnored(path)) {
            return false;
        }
        for (PathFilterSet set: filterSets) {
            if (set.covers(path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isAncestor(String path) {
        for (PathFilterSet set: filterSets) {
            if (set.isAncestor(path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isGloballyIgnored(String path) {
        return globalIgnored != null && globalIgnored.matches(path);
    }

    /**
     * {@inheritDoc}
     */
    public WorkspaceFilter translate(PathMapping mapping) {
        if (mapping == null) {
            return this;
        }
        DefaultWorkspaceFilter mapped = new DefaultWorkspaceFilter();
        if (globalIgnored != null) {
            mapped.setGlobalIgnored(globalIgnored.translate(mapping));
        }
        for (PathFilterSet set: filterSets) {
            mapped.add(set.translate(mapping));
        }
        return mapped;
    }

    /**
     * Loads the workspace filter from the given file
     * @param file source
     * @throws ConfigurationException if the source is not valid
     * @throws IOException if an I/O error occurs
     */
    public void load(File file) throws IOException, ConfigurationException {
        load(new FileInputStream(file));
    }

    /**
     * {@inheritDoc}
     */
    public InputStream getSource() {
        if (source == null) {
            generateSource();
        }
        return new ByteArrayInputStream(source);
    }

    /**
     * {@inheritDoc}
     */
    public String getSourceAsString() {
        if (source == null) {
            generateSource();
        }
        try {
            return new String(source, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Loads the workspace filter from the given input source
     * @param in source
     * @throws ConfigurationException if the source is not valid
     * @throws IOException if an I/O error occurs
     */
    public void load(InputStream in) throws IOException, ConfigurationException {
        try {
            source = IOUtils.toByteArray(in);
            in = getSource();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            //factory.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            // disable DTD loading (bug #36897)
            builder.setEntityResolver(new RejectingEntityResolver());
            Document document = builder.parse(in);
            Element doc = document.getDocumentElement();
            if (!"workspaceFilter".equals(doc.getNodeName())) {
                throw new ConfigurationException("<workspaceFilter> expected.");
            }
            String v = doc.getAttribute(ATTR_VERSION);
            if (v == null || "".equals(v)) {
                v = "1.0";
            }
            version = Double.parseDouble(v);
            if (version > SUPPORTED_VERSION) {
                throw new ConfigurationException("version " + version + " not supported.");
            }
            read(doc);
        } catch (ParserConfigurationException e) {
            throw new ConfigurationException(
                    "Unable to create configuration XML parser", e);
        } catch (SAXException e) {
            throw new ConfigurationException(
                    "Configuration file syntax error.", e);
        } finally {
            IOUtils.closeQuietly(in);
        }

    }

    private void read(Element elem) throws ConfigurationException {
        NodeList nl = elem.getChildNodes();
        for (int i=0; i<nl.getLength(); i++) {
            Node child = nl.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (!"filter".equals(child.getNodeName())) {
                    throw new ConfigurationException("<filter> expected.");
                }
                PathFilterSet def = readDef((Element) child);
                filterSets.add(def);
            }
        }
    }

    private PathFilterSet readDef(Element elem) throws ConfigurationException {
        String root = elem.getAttribute("root");
        PathFilterSet def = new PathFilterSet(root == null || root.length() == 0 ? "/" : root);
        // check for import mode
        String mode = elem.getAttribute("mode");
        if (mode != null && mode.length() > 0) {
            def.setImportMode(ImportMode.valueOf(mode.toUpperCase()));
        }
        // check for filters
        NodeList n1 = elem.getChildNodes();
        for (int i=0; i<n1.getLength(); i++) {
            Node child = n1.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if ("include".equals(child.getNodeName())) {
                    def.addInclude(readFilter((Element) child));
                } else if ("exclude".equals(child.getNodeName())) {
                    def.addExclude(readFilter((Element) child));
                } else {
                    throw new ConfigurationException("either <include> or <exclude> expected.");
                }
            }
        }
        return def;
    }

    protected PathFilter readFilter(Element elem) throws ConfigurationException {
        String pattern = elem.getAttribute("pattern");
        if (pattern == null || "".equals(pattern)) {
            throw new ConfigurationException("Filter pattern must not be empty");
        }
        return new DefaultPathFilter(pattern);
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        Iterator<PathFilterSet> iter = filterSets.iterator();
        while (iter.hasNext()) {
            PathFilterSet set = iter.next();
            ctx.println(!iter.hasNext(), "ItemFilterSet");
            ctx.indent(!iter.hasNext());
            set.dump(ctx, false);
            ctx.outdent();
        }
    }

    /**
     * Reset the source content to a null state.
     */
    @SuppressWarnings("unused")
    public void resetSource() {
        source = null;
    }


    private void generateSource() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            XMLSerializer ser = new XMLSerializer(out, new OutputFormat("xml", "UTF-8", true));
            ser.startDocument();
            AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute(null, null, ATTR_VERSION, "CDATA", String.valueOf(version));
            ser.startElement(null, null, "workspaceFilter", attrs);
            for (PathFilterSet set: filterSets) {
                attrs = new AttributesImpl();
                attrs.addAttribute(null, null, "root", "CDATA", set.getRoot());
                if (set.getImportMode() != ImportMode.REPLACE) {
                    attrs.addAttribute(null, null, "mode", "CDATA", set.getImportMode().name().toLowerCase());
                }
                ser.startElement(null, null, "filter", attrs);
                for (PathFilterSet.Entry<PathFilter> entry: set.getEntries()) {
                    // only handle path filters
                    PathFilter filter = entry.getFilter();
                    if (filter instanceof DefaultPathFilter) {
                        attrs = new AttributesImpl();
                        attrs.addAttribute(null, null, "pattern", "CDATA",
                                ((DefaultPathFilter) filter).getPattern());
                        if (entry.isInclude()) {
                            ser.startElement(null, null, "include", attrs);
                            ser.endElement("include");
                        } else {
                            ser.startElement(null, null, "exclude", attrs);
                            ser.endElement("exclude");
                        }
                    } else {
                        throw new IllegalArgumentException("Can only export default path filters, yet.");
                    }
                }
                ser.endElement("filter");
            }
            ser.endElement("workspaceFilter");
            ser.endDocument();
            source = out.toByteArray();
        } catch (SAXException e) {
            throw new IllegalStateException(e);
        }
    }

    public void setGlobalIgnored(PathFilter ignored) {
        globalIgnored = ignored;
    }

    /**
     * {@inheritDoc}
     */
    public void dumpCoverage(javax.jcr.Node rootNode, ProgressTrackerListener listener)
            throws RepositoryException {
        ProgressTracker tracker = new ProgressTracker(listener);
        log.debug("Starting coverage dump at / (skipJcrContent=false)");
        dumpCoverage(rootNode, tracker, false);
    }

    /**
     * {@inheritDoc}
     */
    public void dumpCoverage(Session session, ProgressTrackerListener listener, boolean skipJcrContent)
            throws RepositoryException {
        ProgressTracker tracker = new ProgressTracker(listener);
        // get common ancestor
        Tree<PathFilterSet> tree = new Tree<PathFilterSet>();
        for (PathFilterSet set: filterSets) {
            tree.put(set.getRoot(), set);
        }
        String rootPath = tree.getRootPath();
        javax.jcr.Node rootNode;
        if (session.nodeExists(rootPath)) {
            rootNode = session.getNode(rootPath);
        } else if (session.nodeExists("/")) {
            log.warn("Common ancestor {} not found. Using root node", rootPath);
            rootNode = session.getRootNode();
            rootPath = "/";
        } else {
            throw new PathNotFoundException("Common ancestor " + rootPath+ " not found.");
        }
        log.debug("Starting coverage dump at {} (skipJcrContent={})", rootPath, skipJcrContent);
        dumpCoverage(rootNode, tracker, skipJcrContent);
    }

    private void dumpCoverage(javax.jcr.Node node, ProgressTracker tracker, boolean skipJcrContent)
            throws RepositoryException {
        String path = node.getPath();
        if (skipJcrContent && "jcr:content".equals(Text.getName(path))) {
            return;
        }
        boolean contained;
        if ((contained = contains(path)) || isAncestor(path)) {
            if (contained) {
                tracker.track("A", path);
            }
            NodeIterator iter = node.getNodes();
            while (iter.hasNext()) {
                dumpCoverage(iter.nextNode(), tracker, skipJcrContent);
            }

        }
    }


}
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

    private final List<PathFilterSet> nodesFilterSets = new LinkedList<>();

    private final List<PathFilterSet> propsFilterSets = new LinkedList<>();

    // this list is only kept as reference for source generation.
    private List<PathFilterSet> referenceFilterSets = null;

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

    /**
     * Add a #PathFilterSet for nodes items.
     * @param set the set of filters to add.
     */
    public void add(PathFilterSet set) {
        nodesFilterSets.add(set);
        if (referenceFilterSets == null) {
            referenceFilterSets = new LinkedList<>();
        }
        referenceFilterSets.add(set);

        // create corresponding filter set for the properties so that it covers the roots of the node filters
        for (PathFilterSet s: propsFilterSets) {
            if (s.getRoot().equals(set.getRoot())) {
                return;
            }
        }
        propsFilterSets.add(new PathFilterSet(set.getRoot()));
    }

    /**
     * Add a #PathFilterSet for node and property items.
     * @param nodeFilter the set of filters to add.
     * @param propFilter the set of filters to add.
     */
    public void add(PathFilterSet nodeFilter, PathFilterSet propFilter) {
        if (!nodeFilter.getRoot().equals(propFilter.getRoot())) {
            throw new IllegalArgumentException("Adding node and property filter sets must have the same root");
        }

        nodesFilterSets.add(nodeFilter);
        propsFilterSets.add(propFilter);
        if (referenceFilterSets == null) {
            referenceFilterSets = new LinkedList<>();
        }
        PathFilterSet bothFilter = new PathFilterSet(nodeFilter.getRoot());
        bothFilter.setType(nodeFilter.getType());
        bothFilter.setImportMode(nodeFilter.getImportMode());
        bothFilter.addAll(nodeFilter);
        for (PathFilterSet.Entry<PathFilter> entry: propFilter.getEntries()) {
            // only handle path filters
            PathFilter filter = entry.getFilter();
            if (filter instanceof DefaultPathFilter) {
                if (entry.isInclude()) {
                    bothFilter.addInclude(new DefaultPropertyPathFilter(((DefaultPathFilter) filter).getPattern()));
                } else {
                    bothFilter.addExclude(new DefaultPropertyPathFilter(((DefaultPathFilter) filter).getPattern()));
                }
            } else {
                throw new IllegalArgumentException("Can only export default path filters, yet.");
            }
        }
        referenceFilterSets.add(bothFilter);
    }

    /**
     * Add a #PathFilterSet for properties items.
     * @param set the set of filters to add.
     *
     * @deprecated use {@link #add(PathFilterSet, PathFilterSet)} instead.
     */
    @Deprecated
    public void addPropertyFilterSet(PathFilterSet set) {
        // minimal backward compatibility: replace the props filter with the same root
        Iterator<PathFilterSet> iter = propsFilterSets.iterator();
        while (iter.hasNext()) {
            PathFilterSet filterSet = iter.next();
            if (filterSet.getRoot().equals(set.getRoot())) {
                iter.remove();
                break;
            }
        }
        propsFilterSets.add(set);
    }

    /**
     * {@inheritDoc}
     */
    public List<PathFilterSet> getFilterSets() {
        return nodesFilterSets;
    }

    /**
     * {@inheritDoc}
     */
    public List<PathFilterSet> getPropertyFilterSets() {
        return propsFilterSets;
    }

    /**
     * {@inheritDoc}
     */
    public PathFilterSet getCoveringFilterSet(String path) {
        if (isGloballyIgnored(path)) {
            return null;
        }
        for (PathFilterSet set: nodesFilterSets) {
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
        for (PathFilterSet set: nodesFilterSets) {
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
        for (PathFilterSet set: nodesFilterSets) {
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
        for (PathFilterSet set: nodesFilterSets) {
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
        mapped.importMode = importMode;
        if (globalIgnored != null) {
            mapped.setGlobalIgnored(globalIgnored.translate(mapping));
        }
        for (PathFilterSet set: nodesFilterSets) {
            mapped.nodesFilterSets.add(set.translate(mapping));
        }
        for (PathFilterSet set: propsFilterSets) {
            mapped.propsFilterSets.add(set.translate(mapping));
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
        try (InputStream input = new FileInputStream(file)) {
            load(input);
        }
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
     * Loads the workspace filter from the given input source.
     * <p>The specified stream remains open after this method returns.
     * @param in source
     * @throws ConfigurationException if the source is not valid
     * @throws IOException if an I/O error occurs
     */
    public void load(final InputStream in) throws IOException, ConfigurationException {
        source = IOUtils.toByteArray(in);
        try (InputStream inCopy = getSource()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            //factory.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            // disable DTD loading (bug #36897)
            builder.setEntityResolver(new RejectingEntityResolver());
            Document document = builder.parse(inCopy);
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
            nodesFilterSets.clear();
            propsFilterSets.clear();
            referenceFilterSets = new LinkedList<>();
            read(doc);
        } catch (ParserConfigurationException e) {
            throw new ConfigurationException(
                    "Unable to create configuration XML parser", e);
        } catch (SAXException e) {
            throw new ConfigurationException(
                    "Configuration file syntax error.", e);
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
                readDef((Element) child);
            }
        }
    }

    private void readDef(Element elem) throws ConfigurationException {
        String root = elem.getAttribute("root");
        if (root == null || root.length() == 0) {
            root = "/";
        }
        PathFilterSet nodeFilters = new PathFilterSet(root);
        PathFilterSet propFilters = new PathFilterSet(root);
        PathFilterSet bothFilters = new PathFilterSet(root);
        // check for import mode
        String mode = elem.getAttribute("mode");
        if (mode != null && mode.length() > 0) {
            ImportMode importMode = ImportMode.valueOf(mode.toUpperCase());
            nodeFilters.setImportMode(importMode);
            propFilters.setImportMode(importMode);
            bothFilters.setImportMode(importMode);
        }
        String type = elem.getAttribute("type");
        if (type != null && type.length() > 0) {
            nodeFilters.setType(type);
            propFilters.setType(type);
            bothFilters.setType(type);
        }

        // check for filters
        NodeList n1 = elem.getChildNodes();
        for (int i=0; i<n1.getLength(); i++) {
            Node child = n1.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                final PathFilter filter = readFilter((Element) child);
                if ("include".equals(child.getNodeName())) {
                    if (filter instanceof DefaultPropertyPathFilter) {
                        propFilters.addInclude(filter);
                    } else {
                        nodeFilters.addInclude(filter);
                    }
                    bothFilters.addInclude(filter);
                } else if ("exclude".equals(child.getNodeName())) {
                    if (filter instanceof DefaultPropertyPathFilter) {
                        propFilters.addExclude(filter);
                    } else {
                        nodeFilters.addExclude(filter);
                    }
                    bothFilters.addExclude(filter);
                } else {
                    throw new ConfigurationException("either <include> or <exclude> expected.");
                }
            }
        }
        nodesFilterSets.add(nodeFilters);
        propsFilterSets.add(propFilters);
        referenceFilterSets.add(bothFilters);
    }

    protected PathFilter readFilter(Element elem) throws ConfigurationException {
        String pattern = elem.getAttribute("pattern");
        if (pattern == null || "".equals(pattern)) {
            throw new ConfigurationException("Filter pattern must not be empty");
        }
        boolean matchProperties = Boolean.valueOf(elem.getAttribute("matchProperties"));
        if (matchProperties) {
            return new DefaultPropertyPathFilter(pattern);
        }
        return new DefaultPathFilter(pattern);
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        Iterator<PathFilterSet> iter = nodesFilterSets.iterator();
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

            if (referenceFilterSets == null) {
                referenceFilterSets = new LinkedList<>(nodesFilterSets);
            }
            for (PathFilterSet set: referenceFilterSets) {
                attrs = new AttributesImpl();
                attrs.addAttribute(null, null, "root", "CDATA", set.getRoot());
                if (set.getImportMode() != ImportMode.REPLACE) {
                    attrs.addAttribute(null, null, "mode", "CDATA", set.getImportMode().name().toLowerCase());
                }
                if (set.getType() != null) {
                    attrs.addAttribute(null, null, "type", "CDATA", set.getType());
                }
                ser.startElement(null, null, "filter", attrs);
                for (PathFilterSet.Entry<PathFilter> entry: set.getEntries()) {
                    // only handle path filters
                    PathFilter filter = entry.getFilter();
                    if (filter instanceof DefaultPathFilter) {
                        attrs = new AttributesImpl();
                        attrs.addAttribute(null, null, "pattern", "CDATA", ((DefaultPathFilter) filter).getPattern());
                        if (filter instanceof DefaultPropertyPathFilter) {
                            attrs.addAttribute(null, null, "matchProperties", "CDATA", "true");
                        }
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
        for (PathFilterSet set: nodesFilterSets) {
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

    /**
     * internal class to mark the property filter entries. eventually promote to outer class and adjust the 'contains'
     * code accordingly. But since the filter set are publicly accessible, this would introduce backward compatibility
     * issues for code that is reading those directly.
     */
    private static class DefaultPropertyPathFilter extends DefaultPathFilter {

        private DefaultPropertyPathFilter(String pattern) {
            super(pattern);
        }

        @Override
        public PathFilter translate(PathMapping mapping) {
            DefaultPathFilter mapped =  (DefaultPathFilter) super.translate(mapping);
            if (mapped != this) {
                mapped = new DefaultPropertyPathFilter(mapped.getPattern());
            }
            return mapped;
        }
    }

}
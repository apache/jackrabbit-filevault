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
package org.apache.jackrabbit.vault.packaging.registry.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.api.FilterSet.Entry;
import org.apache.jackrabbit.vault.fs.api.PathFilter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.SubPackageHandling;
import org.apache.jackrabbit.vault.packaging.SubPackageHandling.Option;
import org.apache.jackrabbit.vault.util.RejectingEntityResolver;
import org.apache.jackrabbit.vault.util.xml.serialize.FormattingXmlStreamWriter;
import org.apache.jackrabbit.vault.util.xml.serialize.OutputFormat;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Internal (immutable) State object to cache and pass the relevant metadata around.
 */
public class FSInstallState {

    private static final String TAG_REGISTRY_METADATA = "registryMetadata";

    private static final String ATTR_PACKAGE_ID = "packageid";

    private static final String ATTR_FILE_PATH = "filepath";

    private static final String ATTR_PACKAGE_STATUS = "packagestatus";

    private static final String ATTR_EXTERNAL = "external";

    private static final String ATTR_SIZE = "size";

    private static final String TAG_DEPENDENCY = "dependency";

    private static final String TAG_SUBPACKAGE = "subpackage";

    private static final String ATTR_INSTALLATION_TIME = "installtime";

    private static final String ATTR_SUBPACKAGE_HANDLING_OPTION = "sphoption";

    private static final String TAG_FILTER = "filter";

    private static final String TAG_WORKSPACEFILTER = "workspacefilter";

    private static final String ATTR_ROOT = "root";

    private static final String TAG_RULE = "rule";

    private static final String ATTR_INCLUDE = "include";

    private static final String ATTR_EXCLUDE = "exclude";

    private static final String TAG_PACKAGEPROPERTIES = "packageproperties";

    private final PackageId packageId;
    private final FSPackageStatus status;
    private Path filePath;
    private boolean external;
    private Set<Dependency> dependencies = Collections.emptySet();
    private Map<PackageId, SubPackageHandling.Option> subPackages = Collections.emptyMap();
    private Long installTime;
    private long size = 0;
    private WorkspaceFilter filter;
    private Properties properties = new Properties();

    public FSInstallState(@Nonnull PackageId pid, @Nonnull FSPackageStatus status) {
        this.packageId = pid;
        this.status = status;
    }

    public FSInstallState withFilePath(Path filePath) {
        this.filePath = filePath;
        return this;
    }

    public FSInstallState withExternal(boolean external) {
        this.external = external;
        return this;
    }

    public FSInstallState withDependencies(Set<Dependency> dependencies) {
        this.dependencies = dependencies == null
                ? Collections.<Dependency>emptySet()
                : Collections.unmodifiableSet(dependencies);
        return this;
    }

    public FSInstallState withSubPackages(Map<PackageId, SubPackageHandling.Option> subPackages) {
        this.subPackages = subPackages == null
                ? Collections.<PackageId, SubPackageHandling.Option>emptyMap()
                : Collections.unmodifiableMap(subPackages);
        return this;
    }

    public FSInstallState withInstallTime(Long installTime) {
        this.installTime = installTime;
        return this;
    }

    public FSInstallState withSize(long size) {
        this.size = size;
        return this;
    }

    public FSInstallState withFilter(WorkspaceFilter filter) {
        this.filter = filter;
        return this;
    }

    public FSInstallState withProperties(Properties properties) {
        this.properties = properties;
        return this;
    }

    /**
     * Parses {@code InstallState} from metafile.
     *
     * @param metaFile The meta file.
     * @return Install state or null if file is not metafile format
     *
     * @throws IOException in case root tag is correct but structure not parsable as expected
     */
    @Nullable
    public static FSInstallState fromFile(File metaFile) throws IOException {
        if (!metaFile.exists()) {
            return null;
        }
        try (InputStream in = FileUtils.openInputStream(metaFile)) {
            return fromStream(in, metaFile.getPath());
        }
    }

    /**
     * Parses {@code InstallState} from metafile.
     *
     * @param in The input stream
     * @param systemId the id of the stream
     * @return Install state or null if file is not metafile format
     *
     * @throws IOException in case root tag is correct but structure not parsable as expected
     */
    @Nullable
    public static FSInstallState fromStream(InputStream in, String systemId) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver(new RejectingEntityResolver());
            Document document = builder.parse(in, systemId);
            Element doc = document.getDocumentElement();
            if (!TAG_REGISTRY_METADATA.equals(doc.getNodeName())) {
                return null;
            }
            String packageId = doc.getAttribute(ATTR_PACKAGE_ID);
            Path filePath = Paths.get(doc.getAttribute(ATTR_FILE_PATH));
            Long installTime = null;
            if (doc.hasAttribute(ATTR_INSTALLATION_TIME)) {
                installTime = Long.valueOf(doc.getAttribute(ATTR_INSTALLATION_TIME));
            }
            boolean external = Boolean.parseBoolean(doc.getAttribute(ATTR_EXTERNAL));
            long size = 0;
            if (doc.hasAttribute(ATTR_SIZE)) {
                size = Long.valueOf(doc.getAttribute(ATTR_SIZE));
            }
            FSPackageStatus status = FSPackageStatus.valueOf(doc.getAttribute(ATTR_PACKAGE_STATUS).toUpperCase());
            NodeList nl = doc.getChildNodes();
            Set<Dependency> dependencies = new HashSet<>();
            Map<PackageId, SubPackageHandling.Option> subPackages = new HashMap<>();
            WorkspaceFilter filter = null;
            Properties properties = new Properties();
            for (int i = 0; i < nl.getLength(); i++) {
                Node child = nl.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    String childName = child.getNodeName();
                    if (TAG_DEPENDENCY.equals(childName)) {
                        dependencies.add(readDependency((Element) child));
                    } else if (TAG_SUBPACKAGE.equals(childName)) {
                        subPackages.put(readPackageId((Element) child), readSubPackgeHandlingOption((Element) child));
                    } else if (TAG_WORKSPACEFILTER.equals(childName)) {
                        filter = readWorkspaceFilter((Element) child);
                    } else if (TAG_PACKAGEPROPERTIES.equals(childName)) {
                        properties = readProperties((Element) child);
                    } else {
                        throw new IOException("<" + TAG_DEPENDENCY + "> or <" + TAG_SUBPACKAGE + "> or <" + TAG_WORKSPACEFILTER + "> expected.");
                    }
                }

            }
            return new FSInstallState(PackageId.fromString(packageId), status)
                    .withFilePath(filePath)
                    .withExternal(external)
                    .withSize(size)
                    .withFilter(filter)
                    .withDependencies(dependencies)
                    .withSubPackages(subPackages)
                    .withProperties(properties)
                    .withInstallTime(installTime);
        } catch (ParserConfigurationException e) {
            throw new IOException("Unable to create configuration XML parser", e);
        } catch (SAXException e) {
            throw new IOException("Configuration file syntax error.", e);
        } catch (ConfigurationException e) {
            throw new IOException("Configuration file syntax error.", e);
        }
    }

    private static Properties readProperties(Element child) {
        Properties properties = new Properties();
        NamedNodeMap attributes = child.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            properties.put(attr.getNodeName(), attr.getNodeValue());
        }
        return properties;
    }

    private static Dependency readDependency(Element child) {
        return Dependency.fromString(child.getAttribute(ATTR_PACKAGE_ID));
    }

    private static SubPackageHandling.Option readSubPackgeHandlingOption(Element child) {
        return SubPackageHandling.Option.valueOf(child.getAttribute(ATTR_SUBPACKAGE_HANDLING_OPTION));
    }

    private static PackageId readPackageId(Element child) {
        return PackageId.fromString(child.getAttribute(ATTR_PACKAGE_ID));
    }

    private static WorkspaceFilter readWorkspaceFilter(Element child) throws ConfigurationException {
        DefaultWorkspaceFilter wsfilter = new DefaultWorkspaceFilter();
        NodeList nl = child.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node filter = nl.item(i);
            if (filter.getNodeType() == Node.ELEMENT_NODE) {
                PathFilterSet pfs = new PathFilterSet(((Element) filter).getAttribute(ATTR_ROOT));
                NodeList nlf = filter.getChildNodes();
                for (int j = 0; j < nlf.getLength(); j++) {
                    Node rule = nlf.item(j);
                    if (rule.getNodeType() == Node.ELEMENT_NODE) {
                        if (((Element) rule).hasAttribute(ATTR_INCLUDE)) {
                            DefaultPathFilter pf = new DefaultPathFilter(((Element) rule).getAttribute(ATTR_INCLUDE));
                            pfs.addInclude(pf);
                        } else if (((Element) rule).hasAttribute(ATTR_EXCLUDE)) {
                            DefaultPathFilter pf = new DefaultPathFilter(((Element) rule).getAttribute(ATTR_INCLUDE));
                            pfs.addExclude(pf);
                        }
                    }
                }
                wsfilter.add(pfs);
            }
        }
        return wsfilter;
    }


    /**
     * Persists the installState to a metadatafile
     *
     * @param file The files to save the state to
     * @throws IOException if an error occurs.
     */
    public void save(File file) throws IOException {
        try (OutputStream out = FileUtils.openOutputStream(file)) {
            save(out);
        }
    }

    /**
     * Persists the installState to a metadatafile
     *
     * @param out Outputsteam to write to.
     * @throws IOException if an error occurs.
     */
    public void save(OutputStream out) throws IOException {
        try (FormattingXmlStreamWriter writer = FormattingXmlStreamWriter.create(out, new OutputFormat(4, false))) {
            writer.writeStartDocument();
            writer.writeStartElement(TAG_REGISTRY_METADATA);
            writer.writeAttribute(ATTR_PACKAGE_ID, packageId.toString());
            writer.writeAttribute(ATTR_SIZE, Long.toString(size));
            if (installTime != null) {
                writer.writeAttribute(ATTR_INSTALLATION_TIME, Long.toString(installTime));
            }
            writer.writeAttribute(ATTR_FILE_PATH, filePath.toString());
            writer.writeAttribute(ATTR_EXTERNAL, Boolean.toString(external));
            writer.writeAttribute(ATTR_PACKAGE_STATUS, status.name().toLowerCase());

            if (filter != null && !filter.getFilterSets().isEmpty()) {
                writer.writeStartElement(TAG_WORKSPACEFILTER);
                for (PathFilterSet pfs : filter.getFilterSets()) {
                    writer.writeStartElement(TAG_FILTER);
                    writer.writeAttribute(ATTR_ROOT, pfs.getRoot());
                    for (Entry<PathFilter> pf : pfs.getEntries()) {
                        writer.writeStartElement(TAG_RULE);
                        DefaultPathFilter dpf = (DefaultPathFilter) pf.getFilter();
                        if (pf.isInclude()) {
                            writer.writeAttribute(ATTR_INCLUDE, dpf.getPattern());
                        } else {
                            writer.writeAttribute(ATTR_EXCLUDE, dpf.getPattern());
                        }
                        writer.writeEndElement();
                    }
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }
            if (dependencies != null) {
                for (Dependency dependency : dependencies) {
                    writer.writeStartElement(TAG_DEPENDENCY);
                    writer.writeAttribute(ATTR_PACKAGE_ID, dependency.toString());
                    writer.writeEndElement();
                }
            }
            if (subPackages != null) {
                for (java.util.Map.Entry<PackageId, Option> entry : subPackages.entrySet()) {
                    writer.writeStartElement(TAG_SUBPACKAGE);
                    writer.writeAttribute(ATTR_PACKAGE_ID, entry.getKey().toString());
                    writer.writeAttribute(ATTR_SUBPACKAGE_HANDLING_OPTION, entry.getValue().toString());
                    writer.writeEndElement();
                }
            }
            if (properties.size() > 0) {
                writer.writeStartElement(TAG_PACKAGEPROPERTIES);
                for (String key : properties.stringPropertyNames()) {
                    writer.writeAttribute(key, properties.getProperty(key));
                }
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndDocument();
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    public Long getInstallationTime() {
        return installTime;
    }

    public Map<PackageId, SubPackageHandling.Option> getSubPackages() {
        return subPackages;
    }

    public PackageId getPackageId() {
        return packageId;
    }

    public Path getFilePath() {
        return filePath;
    }

    public boolean isExternal() {
        return external;
    }

    public FSPackageStatus getStatus() {
        return status;
    }

    public Set<Dependency> getDependencies() {
        return dependencies;
    }

    public long getSize() {
        return size;
    }

    public WorkspaceFilter getFilter() {
        return filter;
    }

    public Properties getProperties() {
        return properties;
    }
}
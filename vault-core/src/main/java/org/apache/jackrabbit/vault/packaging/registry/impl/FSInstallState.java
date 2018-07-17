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
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.SubPackageHandling;
import org.apache.jackrabbit.vault.util.RejectingEntityResolver;
import org.apache.jackrabbit.vault.util.xml.serialize.OutputFormat;
import org.apache.jackrabbit.vault.util.xml.serialize.XMLSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Internal (immutable) State object to cache and pass the relevant metadata around.
 */
public class FSInstallState {

    private static final String TAG_REGISTRY_METADATA = "registryMetadata";

    private static final String ATTR_PACKAGE_ID = "packageid";

    private static final String ATTR_FILE_PATH = "filepath";

    private static final String ATTR_PACKAGE_STATUS = "packagestatus";

    private static final String ATTR_EXTERNAL = "external";

    private static final String TAG_DEPENDENCY = "dependency";

    private static final String TAG_SUBPACKAGE = "subpackage";

    private static final String ATTR_INSTALLATION_TIME = "installtime";

    private static final String ATTR_SUBPACKAGE_HANDLING_OPTION = "sphoption";

    private PackageId packageId;
    private FSPackageStatus status;
    private String filePath;
    private boolean external;
    private Set<Dependency> dependencies = Collections.emptySet();
    private Map<PackageId, SubPackageHandling.Option> subPackages = Collections.emptyMap();
    private Long installTime;

    public FSInstallState(@Nonnull PackageId packageId, @Nonnull FSPackageStatus status, @Nullable String filePath,
                          boolean external, @Nullable Set<Dependency> dependencies, @Nullable Map<PackageId, SubPackageHandling.Option> subPackages,
                          @Nullable Long installTime) {
        this.packageId = packageId;
        this.status = status;
        this.filePath = filePath;
        this.external = external;
        if (dependencies != null) {
            this.dependencies = Collections.unmodifiableSet(dependencies);
        }
        this.dependencies = dependencies;
        this.installTime = installTime;
        if (subPackages != null) {
            this.subPackages = Collections.unmodifiableMap(subPackages);
        }
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
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver(new RejectingEntityResolver());
            Document document = builder.parse(metaFile);
            Element doc = document.getDocumentElement();
            if (!TAG_REGISTRY_METADATA.equals(doc.getNodeName())) {
                return null;
            }
            String packageId = doc.getAttribute(ATTR_PACKAGE_ID);
            String filePath = doc.getAttribute(ATTR_FILE_PATH);
            Long installTime = null;
            if (doc.hasAttribute(ATTR_INSTALLATION_TIME)) {
                installTime = Long.valueOf(doc.getAttribute(ATTR_INSTALLATION_TIME));
            }
            boolean external = Boolean.parseBoolean(doc.getAttribute(ATTR_EXTERNAL));
            FSPackageStatus status = FSPackageStatus.valueOf(doc.getAttribute(ATTR_PACKAGE_STATUS).toUpperCase());
            NodeList nl = doc.getChildNodes();
            Set<Dependency> dependencies = new HashSet<>();
            Map<PackageId, SubPackageHandling.Option> subPackages = new HashMap<>();
            for (int i = 0; i < nl.getLength(); i++) {
                Node child = nl.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    String childName = child.getNodeName();
                    if (TAG_DEPENDENCY.equals(childName)) {
                        dependencies.add(readDependency((Element) child));
                    } else if (TAG_SUBPACKAGE.equals(childName)) {
                        subPackages.put(readPackageId((Element) child), readSubPackgeHandlingOption((Element) child));
                    } else {
                        throw new IOException("<" + TAG_DEPENDENCY + "> or <" + TAG_SUBPACKAGE + "> expected.");
                    }
                }
            }
            return new FSInstallState(PackageId.fromString(packageId), status, filePath, external, dependencies, subPackages, installTime);
        } catch (ParserConfigurationException e) {
            throw new IOException("Unable to create configuration XML parser", e);
        } catch (SAXException e) {
            throw new IOException("Configuration file syntax error.", e);
        }
    }

    private static Dependency readDependency(Element child) {
        return Dependency.fromString(child.getAttribute(ATTR_PACKAGE_ID));
    }

    private static SubPackageHandling.Option readSubPackgeHandlingOption(Element child) {
        return SubPackageHandling.Option.valueOf(ATTR_SUBPACKAGE_HANDLING_OPTION);
    }
    
    private static PackageId readPackageId(Element child) {
        return PackageId.fromString(child.getAttribute(ATTR_PACKAGE_ID));
    }

    /**
     * Persists the installState to a metadatafile
     * @param file The files to save the state to
     * @throws IOException if an error occurs.
     */
    public void save(File file) throws IOException {
        OutputStream out = FileUtils.openOutputStream(file);
        try {
            XMLSerializer ser = new XMLSerializer(out, new OutputFormat("xml", "UTF-8", true));
            ser.startDocument();
            AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute(null, null, ATTR_PACKAGE_ID, "CDATA", packageId.toString());
            if (installTime != null) {
                attrs.addAttribute(null, null, ATTR_INSTALLATION_TIME, "CDATA", Long.toString(installTime));
            }
            attrs.addAttribute(null, null, ATTR_FILE_PATH, "CDATA", filePath);
            attrs.addAttribute(null, null, ATTR_EXTERNAL, "CDATA", Boolean.toString(external));
            attrs.addAttribute(null, null, ATTR_PACKAGE_STATUS, "CDATA", status.name().toLowerCase());
            ser.startElement(null, null, TAG_REGISTRY_METADATA, attrs);
            if (dependencies != null) {
                for (Dependency dependency : dependencies) {
                    attrs = new AttributesImpl();
                    attrs.addAttribute(null, null, ATTR_PACKAGE_ID, "CDATA", dependency.toString());
                    ser.startElement(null, null, TAG_DEPENDENCY, attrs);
                    ser.endElement(TAG_DEPENDENCY);
                }
            }
            if (subPackages != null) {
                for (PackageId subPackId : subPackages.keySet()) {
                    attrs = new AttributesImpl();
                    attrs.addAttribute(null, null, ATTR_PACKAGE_ID, "CDATA", subPackId.toString());
                    attrs.addAttribute(null, null, ATTR_SUBPACKAGE_HANDLING_OPTION, "CDATA", subPackages.get(subPackId).toString());
                    ser.startElement(null, null, TAG_SUBPACKAGE, attrs);
                    ser.endElement(TAG_SUBPACKAGE);
                }
            }
            ser.endElement(TAG_REGISTRY_METADATA);
            ser.endDocument();
        } catch (SAXException e) {
            throw new IllegalStateException(e);
        } finally {
            IOUtils.closeQuietly(out);
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

    public String getFilePath() {
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
}
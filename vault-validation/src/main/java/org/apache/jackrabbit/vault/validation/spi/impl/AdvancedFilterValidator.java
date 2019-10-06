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
package org.apache.jackrabbit.vault.validation.spi.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.vault.fs.api.FilterSet.Entry;
import org.apache.jackrabbit.vault.fs.api.PathFilter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.Text;
import org.apache.jackrabbit.vault.validation.ValidationViolation;
import org.apache.jackrabbit.vault.validation.impl.util.ValidationMessageErrorHandler;
import org.apache.jackrabbit.vault.validation.spi.FilterValidator;
import org.apache.jackrabbit.vault.validation.spi.GenericMetaInfDataValidator;
import org.apache.jackrabbit.vault.validation.spi.NodePathValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public final class AdvancedFilterValidator implements GenericMetaInfDataValidator, FilterValidator, NodePathValidator {

    protected static final String MESSAGE_ORPHANED_FILTER_ENTRIES = "Found orphaned filter entries: %s";
    protected static final String MESSAGE_INVALID_PATTERN = "Invalid pattern given ('%s') which will never match for any descendants of the root path '%s'.";
    protected static final String MESSAGE_ROOT_PATH_NOT_ABSOLUTE = "Root path must be absolute, but does not start with a '/': '%s'.";
    protected static final String MESSAGE_INVALID_FILTER_XML = "Invalid filter.xml";
    protected static final String MESSAGE_FILTER_ROOT_ANCESTOR_COVERED_BUT_EXCLUDED = "Filter root's ancestor '%s' is covered by dependency '%s' but excluded by its patterns.";
    protected static final String MESSAGE_FILTER_ROOT_ANCESTOR_UNCOVERED = "Filter root's ancestor '%s' is not covered by any of the specified dependencies nor a valid root.";
    protected static final String MESSAGE_NODE_NOT_CONTAINED = "Node '%s' is not contained in any of the filter rules";
    protected static final String MESSAGE_ANCESTOR_NODE_NOT_COVERED = "Ancestor node '%s' is not covered by any of the filter rules. Preferably depend on a package that provides this node or include it in the filter rules!";
    protected static final String MESSAGE_ANCESTOR_NODE_NOT_COVERED_BUT_VALID_ROOT = "Ancestor node '%s' is not covered by any of the filter rules but that node is a given root (either by a dependency or by the known roots). Remove that node!";
    protected static final String MESSAGE_NODE_BELOW_CLEANUP_FILTER = "Node '%s' is covered by a 'cleanup' filter rule. That filter type is only supposed to be used for removing nodes during import!";
    
    static final Path FILTER_XML_PATH = Paths.get(Constants.VAULT_DIR, Constants.FILTER_XML);

    private final Collection<String> validRoots;
    private final ValidationMessageSeverity defaultSeverity;
    private final ValidationMessageSeverity severityForUncoveredAncestorNode;
    private final ValidationMessageSeverity severityForUncoveredFilterRootAncestors;
    private final ValidationMessageSeverity severityForOrphanedFilterEntries;
    private final Collection<PackageInfo> dependenciesMetaInfo;
    private final WorkspaceFilter filter;
    private Map<String, FilterValidator> filterValidators;
    private final Collection<String> danglingNodePaths;
    private final Map<PathFilterSet, List<Entry<PathFilter>>> orphanedFilterSets;

    public AdvancedFilterValidator(@Nonnull ValidationMessageSeverity defaultSeverity, @Nonnull ValidationMessageSeverity severityForUncoveredAncestorNodes, @Nonnull ValidationMessageSeverity severityForUncoveredFilterRootAncestors, @Nonnull ValidationMessageSeverity severityForOrphanedFilterEntries, @Nonnull Collection<PackageInfo> dependenciesMetaInfo, @Nonnull WorkspaceFilter filter, @Nonnull Collection<String> validRoots) {
        this.filterValidators = new HashMap<>();
        this.defaultSeverity = defaultSeverity;
        this.severityForUncoveredAncestorNode = severityForUncoveredAncestorNodes;
        this.severityForUncoveredFilterRootAncestors = severityForUncoveredFilterRootAncestors;
        this.severityForOrphanedFilterEntries = severityForOrphanedFilterEntries;
        this.dependenciesMetaInfo = dependenciesMetaInfo;
        this.filter = filter;
        this.validRoots = validRoots;
        this.danglingNodePaths = new LinkedList<>();
        
        // all roots from dependencies are also potentially valid
        for (PackageInfo dependencyInfo : dependenciesMetaInfo) {
            for (PathFilterSet set : dependencyInfo.getFilter().getFilterSets()) {
                    String root = set.getRoot();
                    validRoots.add(root);
            }
        }
        this.orphanedFilterSets = new LinkedHashMap<>();
        for (PathFilterSet pathFilter : filter.getFilterSets()) {
            if (!PathFilterSet.TYPE_CLEANUP.equals(pathFilter.getType())) {
                List<Entry<PathFilter>> entries = pathFilter.getEntries().stream().filter(Entry<PathFilter>::isInclude).collect(Collectors.toList());
                // add all includes to a new list
                this.orphanedFilterSets.put(pathFilter, entries);
            }
        }
    }

    public void setFilterValidators(Map<String, FilterValidator> filterValidators) {
        this.filterValidators.putAll(filterValidators);
    }

    @Override
    public Collection<ValidationMessage> done() {
        StringBuilder orphanEntries = new StringBuilder();
        for (java.util.Map.Entry<PathFilterSet, List<Entry<PathFilter>>> entry : this.orphanedFilterSets.entrySet()) {
            // separator!
            if (orphanEntries.length() > 0) {
                orphanEntries.append(", ");
            }
            if (entry.getValue().isEmpty()) {
                orphanEntries.append("entry with root '").append(entry.getKey().getRoot()).append("'");
            } else {
                orphanEntries.append("includes [");
                StringBuilder includeEntries = new StringBuilder();
                for (Entry<PathFilter> pathFilterEntry : entry.getValue()) {
                    if (includeEntries.length() > 0) {
                        includeEntries.append(", ");
                    }
                    includeEntries.append(pathFilterEntry.getFilter().toString());
                }
                orphanEntries.append(includeEntries).append("] below root '").append(entry.getKey().getRoot()).append("'");
            }
        }
        if (orphanEntries.length() > 0) {
            return Collections.singleton(new ValidationMessage(severityForOrphanedFilterEntries, String.format(MESSAGE_ORPHANED_FILTER_ENTRIES, orphanEntries.toString())));
        } else {
            return null;
        }
    }

    @Override
    public Collection<ValidationMessage> validate(WorkspaceFilter filter) {
        Collection<ValidationMessage> messages = new LinkedList<>();
        messages.addAll(validatePathFilterSets(filter.getFilterSets(), true));
        messages.addAll(validatePathFilterSets(filter.getPropertyFilterSets(), false));

        // first collect all ancestors (except for the valid roots)
        Set<String> ancestors = new LinkedHashSet<>();
        for (PathFilterSet set : filter.getFilterSets()) {
            if ("cleanup".equals(set.getType())) {
                continue;
            }
            String root = StringUtils.substringBeforeLast(set.getRoot(), "/");
            // ignore well known roots
            if (validRoots.contains(root)) {
                continue;
            }

            // check if this package already contains the ancestor
            if (filter.contains(root)) {
                continue;
            }
            ancestors.add(root);
        }
        // then check for each ancestor
        for (String root : ancestors) {
            String coveringPackageId = null;
            boolean isContained = false;
            for (PackageInfo dependencyInfo : dependenciesMetaInfo) {
                WorkspaceFilter dependencyFilter = dependencyInfo.getFilter();
                if (dependencyFilter.contains(root)) {
                    isContained = true;
                }
                if (dependencyFilter.covers(root)) {
                    coveringPackageId = dependencyInfo.getId().toString();
                }
            }
            if (!isContained) {
                String msg;
                if (coveringPackageId == null) {
                    msg = String.format(MESSAGE_FILTER_ROOT_ANCESTOR_UNCOVERED, root);
                } else {
                    msg = String.format(MESSAGE_FILTER_ROOT_ANCESTOR_COVERED_BUT_EXCLUDED, root, coveringPackageId);
                }
                messages.add(new ValidationMessage(severityForUncoveredFilterRootAncestors, msg));
            }
        }
        return messages;
    }

    public Collection<ValidationMessage> validatePathFilterSets(Collection<PathFilterSet> pathFilterSets, boolean checkRoots) {
        Collection<ValidationMessage> messages = new LinkedList<>();
        for (PathFilterSet pathFilterSet : pathFilterSets) {
            // check for validity of root path
            if (checkRoots && !pathFilterSet.getRoot().startsWith("/")) {
                messages.add(new ValidationMessage(defaultSeverity,
                        String.format(MESSAGE_ROOT_PATH_NOT_ABSOLUTE, pathFilterSet.getRoot())));
            }
            for (Entry<PathFilter> pathFilterEntry : pathFilterSet.getEntries()) {
                if (!(pathFilterEntry.getFilter() instanceof DefaultPathFilter)) {
                    throw new IllegalStateException(
                            "Unexpected path filter found: " + pathFilterEntry.getFilter() + ". Must be of type DefaultPathFilter!");
                }
                DefaultPathFilter defaultPathFilter = DefaultPathFilter.class.cast(pathFilterEntry.getFilter());
                defaultPathFilter.getPattern();
                if (!isRegexValidForRootPath(defaultPathFilter.getPattern(), pathFilterSet.getRoot())) {
                    messages.add(new ValidationMessage(defaultSeverity,
                            String.format(MESSAGE_INVALID_PATTERN, defaultPathFilter.getPattern(), pathFilterSet.getRoot())));
                }
            }
        }
        return messages;
    }

    /** Checks if the regex would at least have the chance to match if the matching path starts with root path.
     * 
     * @param regex
     * @param rootPath
     * @return */
    static boolean isRegexValidForRootPath(String regex, String rootPath) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(rootPath);
        if (matcher.matches()) {
            return true;
        }
        return matcher.hitEnd();
    }

    @Override
    public Collection<ValidationMessage> validateMetaInfData(InputStream input, Path filePath) throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try (InputStream xsdInput = getClass().getResourceAsStream("/filter.xsd")) {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

            // load a WXS schema, represented by a Schema instance
            Source schemaFile = new StreamSource(xsdInput);
            Schema schema = schemaFactory.newSchema(schemaFile);
            factory.setSchema(schema);
            Collection<ValidationMessage> messages = new LinkedList<>();
            if (xsdInput == null) {
                throw new IllegalStateException("Can not load filter.xsd");
            }
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder parser = factory.newDocumentBuilder();
            ValidationMessageErrorHandler errorHandler = new ValidationMessageErrorHandler(defaultSeverity);
            parser.setErrorHandler(errorHandler);
            Document document = parser.parse(input, "");

            messages.addAll(errorHandler.getValidationMessages());
            DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
            try {
                filter.load(document.getDocumentElement());
                // call all registered filter validators
                for (Map.Entry<String, FilterValidator> entry : filterValidators.entrySet()) {
                    messages.add(new ValidationMessage(ValidationMessageSeverity.DEBUG,
                            "Validating with validator " + entry.getKey() + "..."));
                    Collection<ValidationMessage> filterValidatorMessages = entry.getValue().validate(filter);
                    if (filterValidatorMessages != null) {
                        messages.addAll(ValidationViolation.wrapMessages(entry.getKey(), filterValidatorMessages, null, null, null, 0, 0));
                    }
                }
            } catch (ConfigurationException | PatternSyntaxException e) { // TODO: remove PatternSyntaxException once
                                                                          // https://issues.apache.org/jira/browse/JCRVLT-368 is fixed
                messages.add(new ValidationMessage(defaultSeverity, MESSAGE_INVALID_FILTER_XML, e));
            }
            return messages;
        } catch (SAXException e) {
            throw new IOException("Could not parse input as xml", e);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Could not instantiate DOM parser", e);
        }
    }

    @Override
    public boolean shouldValidateMetaInfData(Path filePath) {
        return FILTER_XML_PATH.equals(filePath);
    }

    private void removeFromOrphanedFilterEntries(String nodePath) {
        // find all filter roots which match
        Iterator<java.util.Map.Entry<PathFilterSet, List<Entry<PathFilter>>>> iter = orphanedFilterSets.entrySet().iterator();
        while (iter.hasNext()) {
            java.util.Map.Entry<PathFilterSet, List<Entry<PathFilter>>> orphanedFilterEntry = iter.next();
            if (orphanedFilterEntry.getKey().contains(nodePath)) {
                Iterator<Entry<PathFilter>> includeIterator = orphanedFilterEntry.getValue().iterator();
                // check all include and remove if they apply to the node path
                while (includeIterator.hasNext()) {
                    Entry<PathFilter> includeEntry = includeIterator.next();
                    if (includeEntry.isInclude() && includeEntry.getFilter().matches(nodePath)) {
                        includeIterator.remove();
                    }
                }
                // remove the whole entry if no includes are left
                if (orphanedFilterEntry.getValue().isEmpty()) {
                    // remove it
                    iter.remove();
                }
            }
        }
    }

    @Override
    public Collection<ValidationMessage> validate(String nodePath) {
        // remove from orphaned list
        removeFromOrphanedFilterEntries(nodePath);
        
        // now go through all includes
        if (!filter.contains(nodePath)) {
            if (filter.isAncestor(nodePath)) {
                // consider valid roots
                if (validRoots.contains(nodePath)) {
                    return Collections.singleton(
                            new ValidationMessage(severityForUncoveredAncestorNode,
                                    String.format(MESSAGE_ANCESTOR_NODE_NOT_COVERED_BUT_VALID_ROOT, nodePath)));
                } else {
                    return Collections.singleton(
                                new ValidationMessage(severityForUncoveredAncestorNode,
                                        String.format(MESSAGE_ANCESTOR_NODE_NOT_COVERED, nodePath)));
                }
            } else {
                return Collections
                        .singleton(new ValidationMessage(defaultSeverity, String.format(MESSAGE_NODE_NOT_CONTAINED, nodePath)));
            }
        } else {
            // is it a cleanup filter?
            PathFilterSet pathFilterSet = filter.getCoveringFilterSet(nodePath);
            if (pathFilterSet != null) {
                if (PathFilterSet.TYPE_CLEANUP.equals(pathFilterSet.getType())) {
                    return Collections
                            .singleton(new ValidationMessage(defaultSeverity, String.format(MESSAGE_NODE_BELOW_CLEANUP_FILTER, nodePath)));
                }
            }
        }
        // check that all ancestor nodes till the root node are contained as well
        String danglingNodePath = getDanglingAncestorNodePath(nodePath, filter);
        if (danglingNodePath != null) {
            return Collections.singleton(
                    new ValidationMessage(defaultSeverity, "Ancestor node (" + danglingNodePath + ") of Node '" + nodePath +"' which is contained in a filter include element is not included!"));
        }
        return null;
    }

    /**
     * 
     * @param nodePath
     * @return the path the ancestor node not contained in the filter or {@code null}
     */
     @CheckForNull String getDanglingAncestorNodePath(String nodePath, WorkspaceFilter filter) {
        // check cache first (in that case the issue has already been emitted)
        if (danglingNodePaths.contains(nodePath)) {
            return null;
        }
        // check that all ancestor nodes till the filter root node are contained as well
        for (PathFilterSet pathFilterSet : filter.getFilterSets()) {
            if (pathFilterSet.contains(nodePath)) {
                String parentNodePath = Text.getRelativeParent(nodePath, 1);
                // make sure that all ancestors till the root node are contained as well
                if (!nodePath.equals(pathFilterSet.getRoot()) && !parentNodePath.equals(pathFilterSet.getRoot())) {
                    // ancestor might also be contained in another filter
                    return getDanglingAncestorNodePath(parentNodePath, filter);
                } else {
                    // once the root level is reached this node path is contained
                    return null;
                }
            } 
        }
        danglingNodePaths.add(nodePath);
        return nodePath;
    }
}

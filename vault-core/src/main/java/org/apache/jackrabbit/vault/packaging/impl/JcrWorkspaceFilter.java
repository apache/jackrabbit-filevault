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

package org.apache.jackrabbit.vault.packaging.impl;

import java.util.LinkedList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.ItemFilterSet;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;

/**
 * <code>JcrWorkspaceFilter</code> implements serializations of a workspace
 * filter that is stored in the repository
 */
public class JcrWorkspaceFilter  {

    public static DefaultWorkspaceFilter loadFilter(Node defNode) throws RepositoryException {
        DefaultWorkspaceFilter wsp = new DefaultWorkspaceFilter();
        if (defNode.hasNode(JcrPackageDefinitionImpl.NN_FILTER)) {
            defNode = defNode.getNode(JcrPackageDefinitionImpl.NN_FILTER);
        }
        for (NodeIterator filters = defNode.getNodes(); filters.hasNext();) {
            Node filter = filters.nextNode();
            String root = filter.hasProperty(JcrPackageDefinitionImpl.PN_ROOT)
                    ? filter.getProperty(JcrPackageDefinitionImpl.PN_ROOT).getString()
                    : "";
            if (root.length() == 0) {
                continue;
            }
            String mode = filter.hasProperty(JcrPackageDefinitionImpl.PN_MODE)
                    ? filter.getProperty(JcrPackageDefinitionImpl.PN_MODE).getString()
                    : "";
            PathFilterSet set = new PathFilterSet(root);
            if (mode.length() > 0) {
                set.setImportMode(ImportMode.valueOf(mode.toUpperCase()));
            }
            if (filter.hasProperty(JcrPackageDefinitionImpl.PN_RULES)) {
                // new version with mv rules property
                Property p = filter.getProperty(JcrPackageDefinitionImpl.PN_RULES);
                Value[] values = p.getDefinition().isMultiple() ? p.getValues() : new Value[]{p.getValue()};
                for (Value value: values) {
                    String rule = value.getString();
                    int idx = rule.indexOf(':');
                    String type = idx > 0 ? rule.substring(0, idx) : "include";
                    String patt = idx > 0 ? rule.substring(idx + 1) : "";
                    DefaultPathFilter pf = new DefaultPathFilter(patt);
                    if (type.equals("include")) {
                        set.addInclude(pf);
                    } else {
                        set.addExclude(pf);
                    }
                }
            } else {
                for (NodeIterator rules = filter.getNodes(); rules.hasNext();) {
                    Node rule = rules.nextNode();
                    String type = rule.getProperty(JcrPackageDefinitionImpl.PN_TYPE).getString();
                    String pattern = rule.getProperty(JcrPackageDefinitionImpl.PN_PATTERN).getString();
                    DefaultPathFilter pf = new DefaultPathFilter(pattern);
                    if (type.equals("include")) {
                        set.addInclude(pf);
                    } else {
                        set.addExclude(pf);
                    }
                }
            }
            wsp.add(set);
        }
        return wsp;
    }

    public static void saveLegacyFilter(WorkspaceFilter filter, Node defNode, boolean save)
            throws RepositoryException {
        // delete all nodes first
        for (NodeIterator iter = defNode.getNodes(); iter.hasNext();) {
        iter.nextNode().remove();
        }
        int nr = 0;
        for (PathFilterSet set: filter.getFilterSets()) {
            Node setNode = defNode.addNode("f" + nr);
            setNode.setProperty(JcrPackageDefinitionImpl.PN_ROOT, set.getRoot());
            int eNr = 0;
            for (ItemFilterSet.Entry e: set.getEntries()) {
                // expect path filter
                if (!(e.getFilter() instanceof DefaultPathFilter)) {
                    throw new IllegalArgumentException("Can only handle default path filters.");
                }
                Node eNode = setNode.addNode("f" + eNr);
                eNode.setProperty(JcrPackageDefinitionImpl.PN_TYPE, e.isInclude() ? "include" : "exclude");
                eNode.setProperty(JcrPackageDefinitionImpl.PN_PATTERN, ((DefaultPathFilter) e.getFilter()).getPattern());
                eNr++;
            }
            nr++;
        }
        if (save) {
            defNode.save();
        }
    }


    public static void saveFilter(WorkspaceFilter filter, Node defNode, boolean save)
            throws RepositoryException {
        if (defNode.hasNode(JcrPackageDefinitionImpl.NN_FILTER)) {
            defNode.getNode(JcrPackageDefinitionImpl.NN_FILTER).remove();
        }
        Node filterNode = defNode.addNode(JcrPackageDefinitionImpl.NN_FILTER);
        int nr = 0;
        for (PathFilterSet set: filter.getFilterSets()) {
            Node setNode = filterNode.addNode("f" + nr);
            setNode.setProperty(JcrPackageDefinitionImpl.PN_ROOT, set.getRoot());
            setNode.setProperty(JcrPackageDefinitionImpl.PN_MODE, set.getImportMode().name().toLowerCase());
            List<String> rules = new LinkedList<String>();
            for (ItemFilterSet.Entry e: set.getEntries()) {
                // expect path filter
                if (!(e.getFilter() instanceof DefaultPathFilter)) {
                    throw new IllegalArgumentException("Can only handle default path filters.");
                }
                String type = e.isInclude() ? "include" : "exclude";
                String patt = ((DefaultPathFilter) e.getFilter()).getPattern();
                rules.add(type + ":" + patt);
            }
            setNode.setProperty(JcrPackageDefinitionImpl.PN_RULES, rules.toArray(new String[rules.size()]));
            nr++;
        }
        if (save) {
            defNode.save();
        }
    }


}
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

import org.apache.jackrabbit.vault.fs.api.Aggregator;
import org.apache.jackrabbit.vault.fs.api.ArtifactHandler;
import org.apache.jackrabbit.vault.fs.api.ItemFilter;
import org.apache.jackrabbit.vault.fs.api.ItemFilterSet;
import org.apache.jackrabbit.vault.fs.filter.IsMandatoryFilter;
import org.apache.jackrabbit.vault.fs.filter.IsNodeFilter;
import org.apache.jackrabbit.vault.fs.filter.NameItemFilter;
import org.apache.jackrabbit.vault.fs.filter.NodeTypeItemFilter;
import org.apache.jackrabbit.vault.fs.impl.aggregator.GenericAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

/**
 * <code>JcrFsConfig</code>...
 */
class VaultFsConfig11 extends AbstractVaultFsConfig {

    /**
     * the default logger
     */
    private static final Logger log = LoggerFactory.getLogger(VaultFsConfig11.class);

    public static final double SUPPORTED_VERSION = 1.1;

    private final ConfigHelper helper = new ConfigHelper();

    @SuppressWarnings("unchecked")
    public VaultFsConfig11() {
        helper.getDefaultPackages().put("include", "org.apache.jackrabbit.vault.fs.filter");
        helper.getDefaultPackages().put("exclude", "org.apache.jackrabbit.vault.fs.filter");
    }

    protected void process(Element elem) throws ConfigurationException {
        for (Element child: getChildElements(elem)) {
            if (child.getNodeName().equals("aggregates")) {
                for (Element agg: getChildElements(child)) {
                    if (agg.getNodeName().equals("aggregate")) {
                        processAggregate(agg);
                    } else {
                        log.warn("Unknown element name in config: " + agg.getNodeName());
                    }
                }

            } else if (child.getNodeName().equals("handlers")) {
                for (Element handler: getChildElements(child)) {
                    if (handler.getNodeName().equals("handler")) {
                        processHandler(handler);
                    } else {
                        log.warn("Unknown element name in config: " + handler.getNodeName());
                    }
                }

            } else if ("properties".equals(child.getNodeName())) {
                for (Element prop: getChildElements(child)) {
                    String value = prop.getTextContent();
                    getProperties().put(prop.getNodeName(), value == null ? "" : value.trim());
                }

            } else {
                log.warn("Unknown element name in config: " + child.getNodeName());
            }
        }
    }

    private void processAggregate(Element elem) throws ConfigurationException {
        String type = elem.getAttribute("type");
        if (type == null || type.equals("")) {
            type = "generic";
        }
        Aggregator aggregator = Registry.getInstance().createAggregator(type);
        if (aggregator == null) {
            fail("Aggregator of type " + type + " is not registered.", elem);
        }
        if (aggregator instanceof GenericAggregator) {
            GenericAggregator ga = (GenericAggregator) aggregator;
            String title = elem.getAttribute("title");
            if (title != null) {
                ga.setName(title);
            }
            if ("true".equals(elem.getAttribute("isDefault"))) {
                ga.setIsDefault("true");
            }
            for (Element child: getChildElements(elem)) {
                if (child.getNodeName().equals("matches")) {
                    processFilter(ga.getMatchFilter(), child);
                } else if (child.getNodeName().equals("contains")) {
                    processFilter(ga.getContentFilter(), child);
                }
            }
        }
        // finally add aggregator
        getAggregators().add(aggregator);
    }

    private void processFilter(ItemFilterSet filterSet, Element elem)
            throws ConfigurationException {
        for (Element child: getChildElements(elem)) {
            Boolean isInclude = null;
            if (child.getNodeName().equals("include")) {
                isInclude = true;
            } else if (child.getNodeName().equals("exclude")) {
                isInclude = false;
            } else {
                log.warn("Unknown filter type in list: " + child.getNodeName());
            }
            if (isInclude != null) {
                try {
                    NamedNodeMap attrs = child.getAttributes();
                    ItemFilter filter = null;
                    String clazz = child.getAttribute("class");
                    if (clazz != null && clazz.length() > 0) {
                        filter = (ItemFilter) helper.create(child);
                    } else {
                        // create filter based on some attributes
                        if (attrs.getNamedItem("nodeType") != null) {
                            filter = new NodeTypeItemFilter();
                        } else if (attrs.getNamedItem("isNode") != null) {
                            filter = new IsNodeFilter();
                        } else if (attrs.getNamedItem("name") != null) {
                            filter = new NameItemFilter();
                        } else if (attrs.getNamedItem("isMandatory") != null) {
                            filter = new IsMandatoryFilter();
                        }
                    }
                    if (filter != null) {
                        for (int i=0; i<attrs.getLength(); i++) {
                            Attr attr = (Attr) attrs.item(i);
                            if (ConfigHelper.hasSetter(filter, attr.getName())) {
                                ConfigHelper.setField(filter, attr.getName(), attr.getValue());
                            }
                        }
                        if (isInclude) {
                            filterSet.addInclude(filter);
                        } else {
                            filterSet.addExclude(filter);
                        }
                    }
                } catch (ConfigurationException e) {
                    throw e;
                } catch (Exception e) {
                    fail("Error while processing: " + e, child);
                }
            }
        }
    }

    private void processHandler(Element elem) throws ConfigurationException {
        String type = elem.getAttribute("type");
        if (type == null || type.equals("")) {
            type = "generic";
        }
        ArtifactHandler handler = Registry.getInstance().createHandler(type);
        if (handler == null) {
            fail("Handler of type " + type + " is not registered.", elem);
        }
        // finally add handler
        getHandlers().add(handler);
    }


}
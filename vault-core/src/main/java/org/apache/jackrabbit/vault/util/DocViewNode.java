/*************************************************************************
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
 ************************************************************************/
package org.apache.jackrabbit.vault.util;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.NamespaceException;
import javax.jcr.Value;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.util.ISO9075;
import org.xml.sax.Attributes;

/**
 * Helper class that represents a (jcr) node abstraction based on
 * {@link org.apache.jackrabbit.vault.util.DocViewProperty properties}.
 */
public class DocViewNode {

    public final String name;
    public final String label;
    public final Map<String, DocViewProperty> props = new HashMap<String, DocViewProperty>();
    public String uuid;
    public final String[] mixins;
    public final String primary;

    public DocViewNode(String name, String label, String uuid, Map<String, DocViewProperty> props, String[] mixins, String primary) {
        this.name = name;
        this.label = label;
        this.uuid = uuid;
        this.mixins = mixins;
        this.primary = primary;
        this.props.putAll(props);
    }

    public DocViewNode(String name, String label, Attributes attributes, NamePathResolver npResolver)
            throws NamespaceException {
        this.name = name;
        this.label = label;
        String uuid = null;
        String primary = null;
        String[] mixins = null;
        for (int i = 0; i < attributes.getLength(); i++) {
            // ignore non CDATA attributes
            if (!attributes.getType(i).equals("CDATA")) {
                continue;
            }
            Name pName = NameFactoryImpl.getInstance().create(
                    attributes.getURI(i),
                    ISO9075.decode(attributes.getLocalName(i)));
            DocViewProperty info = DocViewProperty.parse(
                    npResolver.getJCRName(pName),
                    attributes.getValue(i));
            props.put(info.name, info);
            if (pName.equals(NameConstants.JCR_UUID)) {
                uuid = info.values[0];
            } else if (pName.equals(NameConstants.JCR_PRIMARYTYPE)) {
                primary = info.values[0];
            } else if (pName.equals(NameConstants.JCR_MIXINTYPES)) {
                mixins = info.values;
            }
        }
        this.uuid = uuid;
        this.mixins = mixins;
        this.primary = primary;
    }

    public String[] getValues(String name) {
        DocViewProperty prop = props.get(name);
        return prop == null ? null : prop.values;
    }

    public String getValue(String name) {
        DocViewProperty prop = props.get(name);
        return prop == null ? null : prop.values[0];
    }

}
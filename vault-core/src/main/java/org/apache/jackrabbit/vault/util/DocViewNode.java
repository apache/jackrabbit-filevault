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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.NamespaceException;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.util.ISO9075;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;

/**
 * Helper class that represents a (jcr) node abstraction based on
 * {@link org.apache.jackrabbit.vault.util.DocViewProperty properties}.
 */
public class DocViewNode {

    public final @NotNull String name;
    public final @NotNull String label;
    public final @NotNull Map<String, DocViewProperty> props = new HashMap<>();
    public @Nullable String uuid;
    public final @Nullable String[] mixins;
    public final @Nullable String primary; // may be null for ordering items

    public DocViewNode(@NotNull String name, @NotNull String label, String uuid, Map<String, DocViewProperty> props, String[] mixins, String primary) {
        this.name = name;
        this.label = label;
        this.uuid = uuid;
        this.mixins = mixins;
        this.primary = primary;
        this.props.putAll(props);
    }

    public DocViewNode(@NotNull String name, @NotNull String label, Attributes attributes, NamePathResolver npResolver)
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((label == null) ? 0 : label.hashCode());
        result = prime * result + Arrays.hashCode(mixins);
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((primary == null) ? 0 : primary.hashCode());
        result = prime * result + ((props == null) ? 0 : props.hashCode());
        result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DocViewNode other = (DocViewNode) obj;
        if (label == null) {
            if (other.label != null)
                return false;
        } else if (!label.equals(other.label))
            return false;
        if (!Arrays.equals(mixins, other.mixins))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (primary == null) {
            if (other.primary != null)
                return false;
        } else if (!primary.equals(other.primary))
            return false;
        if (props == null) {
            if (other.props != null)
                return false;
        } else if (!props.equals(other.props))
            return false;
        if (uuid == null) {
            if (other.uuid != null)
                return false;
        } else if (!uuid.equals(other.uuid))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "DocViewNode [name=" + name + ", label=" + label + ", props=" + props + ", uuid=" + uuid + ", mixins="
                + Arrays.toString(mixins) + ", primary=" + primary + "]";
    }

}
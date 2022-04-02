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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.jcr.Node;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Helper class that represents an immutable JCR node abstraction encapsulating multiple
 * {@link org.apache.jackrabbit.vault.util.DocViewProperty2} properties.
 * @see <a href="https://jackrabbit.apache.org/filevault/docview.html">FileVault Document View Format</a>
 * @since 3.6.0
 */
public class DocViewNode2 {

    private final @NotNull Name name;
    private final int index;
    private final @NotNull Map<Name, DocViewProperty2> properties;

    public DocViewNode2(@NotNull Name name, @NotNull Collection<DocViewProperty2> properties) {
        this(name, 0, properties);
    }

    public DocViewNode2(@NotNull Name name, int index, @NotNull Collection<DocViewProperty2> properties) {
        Objects.requireNonNull(name, "name must not be null");
        this.name = name;
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        this.index = index;
        Objects.requireNonNull(properties, "properties must not be null");
        this.properties = properties.stream().collect(Collectors.toMap(
                DocViewProperty2::getName, 
                Function.identity(),
                (existing, replacement) -> existing,
                LinkedHashMap::new // keep order of properties in the map
                ));
    }

    public @NotNull DocViewNode2 cloneWithDifferentProperties(@NotNull Collection<DocViewProperty2> properties) {
        return new DocViewNode2(name, index, properties);
    }

    /** 
     * 
     * @return the name of the {@link Node} represented by this class
     */
    public @NotNull Name getName() {
        return name;
    }

    /**
     * 
     * @return 0, except if there is a same-name sibling in the docview. In that case the index gives the order of the SNS nodes.
     *  @see <a href="https://s.apache.org/jcr-2.0-spec/22_Same-Name_Siblings.html#22.2%20Addressing%20Same-Name%20Siblings%20by%20Path">Same-Name Siblings</a>
     */
    public int getIndex() {
        return index;
    }

    /**
     * 
     * @return the name suffixed by an index as outlined in <a href="https://s.apache.org/jcr-2.0-spec/22_Same-Name_Siblings.html#22.2%20Addressing%20Same-Name%20Siblings%20by%20Path">Addressing Same-Name Siblings by Path</a> in case there is a same-name sibling, otherwise the same value as for {@link #getName()}.
     */
    public @NotNull Name getSnsAwareName() {
        if (index > 0) {
            return NameFactoryImpl.getInstance().create(name.getNamespaceURI(), name.getLocalName() + "[" + getIndex() + "]");
        } else {
            return name;
        }
    }

    /**
     * 
     * @return all direct properties of the node represented by this object
     */
    public @NotNull Collection<DocViewProperty2> getProperties() {
        return Collections.unmodifiableCollection(properties.values());
    }

    public @NotNull Optional<DocViewProperty2> getProperty(Name name) {
        return Optional.ofNullable(properties.get(name));
    }

    public boolean hasProperty(Name name) {
        return properties.containsKey(name);
    }

    public @NotNull Collection<String> getPropertyValues(@NotNull Name name) {
        DocViewProperty2 prop = properties.get(name);
        return prop == null ? Collections.emptyList() : prop.getStringValues();
    }

    public @NotNull Optional<String> getPropertyValue(@NotNull Name name) {
        DocViewProperty2 prop = properties.get(name);
        if (prop == null) {
            return Optional.empty();
        } else {
            return prop.getStringValue();
        }
    }

    public @NotNull Optional<String> getPrimaryType() {
        return getPropertyValue(NameConstants.JCR_PRIMARYTYPE);
    }

    public @NotNull Collection<String> getMixinTypes() {
        return getPropertyValues(NameConstants.JCR_MIXINTYPES);
    }

    public @NotNull Optional<String> getIdentifier() {
        return getPropertyValue(NameConstants.JCR_UUID);
    }

    @Override
    public String toString() {
        return "DocViewNode2 [" + (name != null ? "name=" + name + ", " : "") + "index=" + index + ", "
                + (properties != null ? "properties=" + properties : "") + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, name, properties);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DocViewNode2 other = (DocViewNode2) obj;
        return index == other.index && areNamesEqual(name, other.name) && Objects.equals(properties, other.properties);
    }

    static boolean areNamesEqual(@NotNull Name name,  @NotNull Name otherName ) {
        return Objects.equals(name.getLocalName(), otherName.getLocalName()) && Objects.equals(name.getNamespaceURI(), otherName.getNamespaceURI());
    }
}
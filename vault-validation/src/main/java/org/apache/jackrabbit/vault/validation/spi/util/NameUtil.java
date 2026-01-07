/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.validation.spi.util;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Utility methods to generate JCR names in the <a href="https://docs.adobe.com/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.2.5.1%20Expanded%20Form">Expanded Form</a>.
 * @see Name
 */
public class NameUtil {

    private NameUtil() {}

    /**
     * Use {@link NameUtil#getExpandedNameString(String)} to get the expanded form of a name with no specific namespace (using the default namespace).
     * @param namespaceURI the namespace URI
     * @param localName the local name (without any namespace prefix)
     * @return the expanded form of a JCR name consisting out of the given namespace URI and local name
     * @see <a href="https://docs.adobe.com/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.2.5.1%20Expanded%20Form">Expanded Form</a>
     */
    public static @NotNull String getExpandedNameString(@NotNull String namespaceURI, @NotNull String localName) {
        return NameFactoryImpl.getInstance().create(namespaceURI, localName).toString();
    }

    /**
     * Use {@link NameUtil#getExpandedNameString(String, String)} to get the expanded form of a name with a namespace.
     * @param name the name without any namespace prefix
     * @return the expanded form of the given JCR name (without a specific namespace, i.e. using the default namespace)
     * @see <a href="https://docs.adobe.com/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.2.5.1%20Expanded%20Form">Expanded Form</a>
     */
    public static @NotNull String getExpandedNameString(@NotNull String name) {
        return NameFactoryImpl.getInstance().create(Name.NS_DEFAULT_URI, name).toString();
    }
}

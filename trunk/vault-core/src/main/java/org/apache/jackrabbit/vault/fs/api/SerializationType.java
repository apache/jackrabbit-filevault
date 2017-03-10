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

package org.apache.jackrabbit.vault.fs.api;

/**
 * Provides a simple enumeration of possible serialization types.
 *
 */
public enum SerializationType {

    /**
     * specifies that the source type is not known
     */
    UNKOWN("x-vaultfs/unknown", "application/octet-stream"),

    /**
     * specifies that the source type is not serializable
     */
    NONE("x-vaultfs/none", "application/octet-stream"),

    /**
     * specifies that the source is XML but the type not known
     */
    XML_GENERIC("xml/generic", "text/xml"),

    /**
     * specifies that the source is a docview serialization
     */
    XML_DOCVIEW("xml/docview", "text/xml"),

    /**
     * specifies that the source is a compact node type definition
     */
    CND("text/cnd", "text/cnd"),

    /**
     * specifies that the source is generic data.
     */
    GENERIC("x-jcrfs/binary", "application/octet-stream");

    /**
     * the string representation
     */
    private final String name;

    /**
     * the content type of the serialization
     */
    private final String contentType;

    /**
     * Creates a new serialization type
     * @param name the (debug) name of the type
     * @param contentType the default content type
     */
    private SerializationType(String name, String contentType) {
        this.name = name;
        this.contentType = contentType;
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        return name;
    }

    /**
     * Returns the name
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the content type of this serialization type
     * @return the content type
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Returns the serialization type with the given name
     * @param name the name to find
     * @return the serialization type or null
     */
    public static SerializationType fromName(String name) {
        for (SerializationType s: SerializationType.values()) {
            if (s.name.equals(name)) {
                return s;
            }
        }
        return null;
    }

}
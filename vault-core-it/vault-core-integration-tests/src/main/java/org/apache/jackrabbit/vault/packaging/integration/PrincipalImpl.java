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
package org.apache.jackrabbit.vault.packaging.integration;

import java.io.Serializable;
import java.security.Principal;

import org.apache.jackrabbit.api.security.principal.JackrabbitPrincipal;

/**
 * Copy from https://github.com/apache/jackrabbit/blob/8b46617e2d21cd504dde29d7fc52be1c5050bad1/jackrabbit-core/src/main/java/org/apache/jackrabbit/core/security/principal/PrincipalImpl.java#L27
 * Base class for implementations of <code>JackrabbitPrincipal</code>.
 */
public class PrincipalImpl implements JackrabbitPrincipal, Serializable {

    /** the serial number */
    private static final long serialVersionUID = 384040549033267804L;

    /**
     * the name of this principal
     */
    private final String name;

    /**
     * Creates a new principal with the given name.
     *
     * @param name the name of this principal
     */
    public PrincipalImpl(String name) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("Principal name can neither be null nor empty String.");
        }
        this.name = name;
    }

    // ----------------------------------------------------------< Principal >---
    /**
     * {@inheritDoc}
     */
    public String getName() {
        return name;
    }

    // -------------------------------------------------------------< Object >---
    /**
     * Two principals are equal, if their names are.
     * @see Object#equals(Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof JackrabbitPrincipal) {
            return name.equals(((Principal) obj).getName());
        }
        return false;
    }

    /**
     * @return the hash code of the principals name.
     * @see Object#hashCode()
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return getClass().getName() + ":" + name;
    }
}

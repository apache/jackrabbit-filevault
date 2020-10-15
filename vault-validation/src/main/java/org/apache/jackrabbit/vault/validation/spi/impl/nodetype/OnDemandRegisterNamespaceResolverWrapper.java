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
package org.apache.jackrabbit.vault.validation.spi.impl.nodetype;

import javax.jcr.NamespaceException;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.jcr2spi.NamespaceStorage;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;


public class OnDemandRegisterNamespaceResolverWrapper implements NamespaceResolver {

    private final NamespaceStorage nsStorage;
    static final String UNDECLARED_NAMESPACE_URI_PREFIX = "http://unknown.prefix.";
    
    
    public OnDemandRegisterNamespaceResolverWrapper(NamespaceStorage nsStorage) {
        super();
        this.nsStorage = nsStorage;
    }

    @Override
    public String getPrefix(String uri) throws NamespaceException {
        try {
            return nsStorage.getPrefix(uri);
        } catch (RepositoryException e) {
            throw new NamespaceException(e);
        }
    }

    @Override
    public String getURI(String prefix) throws NamespaceException {
        try {
            try {
                return nsStorage.getURI(prefix);
            } catch (NamespaceException e) {
                String newNamespaceUri = UNDECLARED_NAMESPACE_URI_PREFIX + prefix;
                nsStorage.registerNamespace(prefix, newNamespaceUri);
                return newNamespaceUri;
            }
        } catch (RepositoryException e)  {
            throw new NamespaceException("Could not register prefix " + prefix + " on demand", e);
        }
    }

}

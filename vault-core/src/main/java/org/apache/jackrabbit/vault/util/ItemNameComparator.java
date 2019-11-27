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

package org.apache.jackrabbit.vault.util;

import java.util.Comparator;

import javax.jcr.Item;
import javax.jcr.RepositoryException;
import javax.xml.namespace.QName;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.NameException;
import org.apache.jackrabbit.spi.commons.conversion.NameParser;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;

/**
 * {@code ItemNameComparator}...
 */
public class ItemNameComparator implements Comparator<Item> {

    
    /**
     * the session's namespace resolver
     */
    private final NamespaceResolver nsResolver;
    
    public ItemNameComparator(NamespaceResolver nsResolver) {
        super();
        this.nsResolver = nsResolver;
    }

    private QName getQName(String rawName) throws RepositoryException {
        try {
            Name name = NameParser.parse(rawName, nsResolver, NameFactoryImpl.getInstance());
            return new QName(name.getNamespaceURI(), name.getLocalName(), nsResolver.getPrefix(name.getNamespaceURI()));
        } catch (NameException e) {
            // should never get here...
            String msg = "internal error: failed to resolve namespace mappings";
            throw new RepositoryException(msg, e);
        }
    }
    
    public int compare(Item o1, Item o2) {
        try {
            return QNameComparator.INSTANCE.compare(getQName(o1.getName()), getQName(o2.getName()));
        } catch (RepositoryException e) {
            throw new IllegalStateException(e);
        }
    }
}
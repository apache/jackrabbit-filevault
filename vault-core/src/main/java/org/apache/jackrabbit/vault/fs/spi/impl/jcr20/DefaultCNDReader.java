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

package org.apache.jackrabbit.vault.fs.spi.impl.jcr20;

import java.io.IOException;
import java.io.Reader;

import org.apache.jackrabbit.commons.cnd.CompactNodeTypeDefReader;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.spi.QNodeTypeDefinition;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceMapping;
import org.apache.jackrabbit.spi.commons.nodetype.QDefinitionBuilderFactory;
import org.apache.jackrabbit.vault.fs.spi.CNDReader;
import org.apache.jackrabbit.vault.fs.spi.DefaultNodeTypeSet;

/**
 * <code>CNDReaderWrapper</code>...
 */
public class DefaultCNDReader extends DefaultNodeTypeSet implements CNDReader {

    public DefaultCNDReader() {
        super("undefined");
    }

    public void read(Reader reader, String systemId, NamespaceMapping namespaceMapping)
            throws IOException {
        try {
            setSystemId(systemId);
            CompactNodeTypeDefReader<QNodeTypeDefinition, NamespaceMapping> r;
            if (namespaceMapping == null) {
                r = new CompactNodeTypeDefReader<QNodeTypeDefinition, NamespaceMapping>(
                        reader, systemId, new QDefinitionBuilderFactory());
            } else {
                r = new CompactNodeTypeDefReader<QNodeTypeDefinition, NamespaceMapping>(
                        reader, systemId, namespaceMapping,
                        new QDefinitionBuilderFactory());
            }
            add(r.getNodeTypeDefinitions(), r.getNamespaceMapping());
        } catch (ParseException e) {
            IOException ie = new IOException("I/O Error while reading node types.");
            ie.initCause(e);
            throw ie;
        }
    }

}
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

package org.apache.jackrabbit.vault.fs.impl.io;

import java.io.IOException;
import java.io.OutputStream;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.impl.AggregateImpl;
import org.apache.jackrabbit.vault.fs.io.Serializer;
import org.apache.jackrabbit.vault.util.xml.serialize.OutputFormat;
import org.apache.jackrabbit.vault.util.xml.serialize.XMLSerializer;

/**
 * <code>DocViewSerializer</code>...
*
*/
public class DocViewSerializer implements Serializer {

    /**
     * the export context
     */
    private final AggregateImpl aggregate;

    /**
     * Creates a new doc view serializer
     * @param aggregate the export context
     */
    public DocViewSerializer(Aggregate aggregate) {
        this.aggregate = (AggregateImpl) aggregate;
    }

    /**
     * {@inheritDoc}
     */
    public void writeContent(OutputStream out) throws IOException, RepositoryException {
        // build content handler and add filter in case of original xml files
        OutputFormat oFmt = new OutputFormat("xml", "UTF-8", true);
        oFmt.setIndent(4);
        oFmt.setLineWidth(0);
        oFmt.setBreakEachAttribute(true);
        XMLSerializer ser = new XMLSerializer(out, oFmt);
        DocViewSAXFormatter fmt = new DocViewSAXFormatter(aggregate, ser);
        fmt.setUseJcrRoot(true);
        aggregate.walk(fmt);
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link SerializationType#XML_DOCVIEW}
     */
    public SerializationType getType() {
        return SerializationType.XML_DOCVIEW;
    }
}
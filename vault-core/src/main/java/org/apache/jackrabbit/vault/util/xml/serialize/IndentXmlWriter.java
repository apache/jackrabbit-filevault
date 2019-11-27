/**
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
package org.apache.jackrabbit.vault.util.xml.serialize;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.tika.sax.ToXMLContentHandler;
import org.xml.sax.SAXException;

/** A helper class for printing indented text */
public class IndentXmlWriter extends ToXMLContentHandler {

    private int indentLevel;
    private String indent;

    public IndentXmlWriter(OutputStream out) throws UnsupportedEncodingException {
        this(out, StandardCharsets.UTF_8, " ");
    }

    public IndentXmlWriter(OutputStream output, Charset charset, String indent) throws UnsupportedEncodingException {
        super(output, charset.name());
        this.indent = indent;
    }

    public void writeIndent() throws SAXException {
        writeIndent(false);
    }

    public void writeIndent(boolean isForAttribute) throws SAXException {
        for (int i=0;i<indentLevel;i++) {
            super.write(indent);
        }
        if (isForAttribute) {
            // attribute indent is one level more than current element level
            super.write(indent);
        }
    }

    public void indent() {
        indentLevel++;
    }

    public void outdent() {
        indentLevel--;
        if (indentLevel < 0) {
            throw new IllegalStateException("Can not outdent if already on root level!");
        }
    }
}

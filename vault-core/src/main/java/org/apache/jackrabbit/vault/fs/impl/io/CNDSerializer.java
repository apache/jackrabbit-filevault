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
import java.io.OutputStreamWriter;
import java.io.Writer;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.io.Serializer;
import org.apache.jackrabbit.vault.util.JcrConstants;

/**
 * <code>DocViewSerializer</code>...
*
*/
public class CNDSerializer implements Serializer {

    /**
     * the indention string
     */
    private static final String INDENT = "  ";

    /**
     * the export context
     */
    private final Aggregate aggregate;

    /**
     * Creates a new doc view serializer
     * @param aggregate the export context
     */
    public CNDSerializer(Aggregate aggregate) {
        this.aggregate = aggregate;
    }

    /**
     * {@inheritDoc}
     */
    public void writeContent(OutputStream out) throws IOException, RepositoryException {
        Writer w = new OutputStreamWriter(out, "utf-8");
        for (String prefix: aggregate.getNamespacePrefixes()) {
            w.write("<'");
            w.write(prefix);
            w.write("'='");
            w.write(escape(aggregate.getNamespaceURI(prefix)));
            w.write("'>\n");
        }
        w.write("\n");
        
        writeNodeTypeDef(w, aggregate.getNode());
        w.close();
        out.flush();
    }

    private void writeNodeTypeDef(Writer out, Node node) throws IOException, RepositoryException {
        writeName(out, node);
        writeSupertypes(out, node);
        writeOptions(out, node);
        writeDefs(out, node);
        out.write("\n");
    }

    private void writeName(Writer out, Node node)
            throws IOException, RepositoryException {
        out.write("[");
        out.write(node.getProperty(JcrConstants.JCR_NODETYPENAME).getString());
        out.write("]");
    }

    private void writeSupertypes(Writer out, Node node) throws IOException, RepositoryException {
        Value[] types = node.getProperty(JcrConstants.JCR_SUPERTYPES).getValues();
        String delim = " > ";
        for (Value s: types) {
            out.write(delim);
            out.write(s.getString());
            delim = ", ";
        }
    }

    private void writeOptions(Writer out, Node node) throws IOException, RepositoryException {
        String delim = "\n " + INDENT;
        if (node.getProperty(JcrConstants.JCR_HASORDERABLECHILDNODES).getBoolean()) {
            out.write(delim);
            out.write("orderable");
            delim = " ";
        }
        if (node.getProperty(JcrConstants.JCR_ISMIXIN).getBoolean()) {
            out.write(delim);
            out.write("mixin");
        }
    }

    private void writeDefs(Writer out, Node node) throws IOException, RepositoryException {
        NodeIterator iter = node.getNodes();
        String primary = null;
        if (node.hasProperty(JcrConstants.JCR_PRIMARYITEMNAME)) {
            primary = node.getProperty(JcrConstants.JCR_PRIMARYITEMNAME).getString();
        }
        while (iter.hasNext()) {
            Node child = iter.nextNode();
            if (child.getPrimaryNodeType().getName().equals(JcrConstants.NT_PROPERTYDEFINITION)) {
                writePropDef(out, child, primary);
            }
        }
        iter = node.getNodes();
        while (iter.hasNext()) {
            Node child = iter.nextNode();
            if (child.getPrimaryNodeType().getName().equals(JcrConstants.NT_CHILDNODEDEFINITION)) {
                writeNodeDef(out, child, primary);
            }
        }
    }

    private void writePropDef(Writer out, Node node, String primary) throws IOException, RepositoryException {
        out.write("\n" + INDENT + "- ");
        String name = "*";
        if (node.hasProperty(JcrConstants.JCR_NAME)) {
            name = node.getProperty(JcrConstants.JCR_NAME).getString();
        }
        out.write(name);
        out.write(" (");
        out.write(node.getProperty(JcrConstants.JCR_REQUIREDTYPE).getString().toLowerCase());
        out.write(")");
        if (node.hasProperty(JcrConstants.JCR_DEFAULTVALUES)) {
            writeDefaultValues(out, node.getProperty(JcrConstants.JCR_DEFAULTVALUES).getValues());
        }
        if (primary != null && primary.equals(name)) {
            out.write(" primary");
        }
        if (node.getProperty(JcrConstants.JCR_MANDATORY).getBoolean()) {
            out.write(" mandatory");
        }
        if (node.getProperty(JcrConstants.JCR_AUTOCREATED).getBoolean()) {
            out.write(" autocreated");
        }
        if (node.getProperty(JcrConstants.JCR_PROTECTED).getBoolean()) {
            out.write(" protected");
        }
        if (node.getProperty(JcrConstants.JCR_MULTIPLE).getBoolean()) {
            out.write(" multiple");
        }
        String opv = node.getProperty(JcrConstants.JCR_ONPARENTVERSION).getString().toLowerCase();
        if (!opv.equals("copy")) {
            out.write(" ");
            out.write(opv);
        }
        if (node.hasProperty(JcrConstants.JCR_VALUECONSTRAINTS)) {
            writeValueConstraints(out, node.getProperty(JcrConstants.JCR_VALUECONSTRAINTS).getValues());
        }
    }

    private void writeDefaultValues(Writer out, Value[] dva) throws IOException, RepositoryException {
        if (dva != null && dva.length > 0) {
            String delim = " = '";
            for (Value value : dva) {
                out.write(delim);
                out.write(escape(value.getString()));
                out.write("'");
                delim = ", '";
            }
        }
    }

    private void writeValueConstraints(Writer out, Value[] vca) throws IOException, RepositoryException {
        String delim = "\n" + INDENT  + "  < ";
        for (Value v: vca) {
            out.write(delim);
            out.write("'");
            out.write(escape(v.getString()));
            out.write("'");
            delim = ", ";
        }
    }

    private void writeNodeDef(Writer out, Node node, String primary) throws IOException, RepositoryException {
        out.write("\n" + INDENT + "+ ");
        String name = "*";
        if (node.hasProperty(JcrConstants.JCR_NAME)) {
            name = node.getProperty(JcrConstants.JCR_NAME).getString();
        }
        out.write(name);

        writeRequiredTypes(out, node.getProperty(JcrConstants.JCR_REQUIREDPRIMARYTYPES).getValues());
        if (node.hasProperty(JcrConstants.JCR_DEFAULTPRIMARYTYPE)) {
            writeDefaultType(out, node.getProperty(JcrConstants.JCR_DEFAULTPRIMARYTYPE).getString());
        }
        if (primary != null && primary.equals(name)) {
            out.write(" primary");
        }
        if (node.getProperty(JcrConstants.JCR_MANDATORY).getBoolean()) {
            out.write(" mandatory");
        }
        if (node.getProperty(JcrConstants.JCR_AUTOCREATED).getBoolean()) {
            out.write(" autocreated");
        }
        if (node.getProperty(JcrConstants.JCR_PROTECTED).getBoolean()) {
            out.write(" protected");
        }
        if (node.getProperty(JcrConstants.JCR_SAMENAMESIBLINGS).getBoolean()) {
            out.write(" multiple");
        }
        String opv = node.getProperty(JcrConstants.JCR_ONPARENTVERSION).getString().toLowerCase();
        if (!opv.equals("copy")) {
            out.write(" ");
            out.write(opv);
        }
    }

    private void writeRequiredTypes(Writer out, Value[] reqTypes) throws IOException, RepositoryException {
        if (reqTypes.length > 0) {
            String delim = " (";
            for (Value value : reqTypes) {
                out.write(delim);
                out.write(value.getString());
                delim = ", ";
            }
            out.write(")");
        }
    }

    private void writeDefaultType(Writer out, String defType) throws IOException {
        if (!defType.equals("*")) {
            out.write(" = ");
            out.write(defType);
        }
    }

    /**
     * escape
     * @param s the string to escape
     * @return the escaped string
     */
    private String escape(String s) {
        StringBuffer sb = new StringBuffer(s);
        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) == '\\') {
                sb.insert(i, '\\');
                i++;
            } else if (sb.charAt(i) == '\'') {
                sb.insert(i, '\'');
                i++;
            }
        }
        return sb.toString();
    }


    /**
     * {@inheritDoc}
     *
     * @return {@link SerializationType#CND}
     */
    public SerializationType getType() {
        return SerializationType.CND;
    }
}
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

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.vault.fs.impl.io.legacycnd.Lexer;
import org.apache.jackrabbit.vault.fs.impl.io.legacycnd.ParseException;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>CNDImporter</code>...
 *
 */
public class CNDImporter {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(CNDImporter.class);

    /**
     * the underlying lexer
     */
    private Lexer lexer;

    /**
     * the current token
     */
    private String currentToken;

    /**
     * old namespace mappings that need to be reverted
     */
    private Map<String, String> oldMappings = new HashMap<String, String>();

    public ImportInfoImpl doImport(Node parent, String name, Reader r, String systemId)
            throws RepositoryException {
        try {
            lexer = new Lexer(r, systemId);
            nextToken();
            ImportInfoImpl info = parse(parent, name);
            // reset name spaces
            for (String prefix: oldMappings.keySet()) {
                String uri = oldMappings.get(prefix);
                try {
                    parent.getSession().setNamespacePrefix(prefix, uri);
                } catch (RepositoryException e) {
                    // ignore
                }
            }
            return info;
        } catch (ParseException e) {
            log.error("Error while parsing.", e);
            return null;
        }
    }

    private ImportInfoImpl parse(Node parent, String name) throws ParseException, RepositoryException {
        while (!currentTokenEquals(Lexer.EOF)) {
            if (!doNameSpace(parent.getSession())) {
                break;
            }
        }
        ImportInfoImpl info = new ImportInfoImpl();
        while (!currentTokenEquals(Lexer.EOF)) {
            String ntName = doNodeTypeName();
            if (name == null) {
                name = ntName;
            }
            if (parent.hasNode(name)) {
                parent.getNode(name).remove();
            }
            Node node;
            if (parent.hasNode(name)) {
                parent.getNode(name).remove();
                node = parent.addNode(name, JcrConstants.NT_NODETYPE);
                info.onReplaced(node.getPath());
            } else {
                node = parent.addNode(name, JcrConstants.NT_NODETYPE);
                info.onCreated(node.getPath());
            }
            node.setProperty(JcrConstants.JCR_NODETYPENAME, name);
            
            // init mandatory props
            node.setProperty(JcrConstants.JCR_HASORDERABLECHILDNODES, false);
            node.setProperty(JcrConstants.JCR_ISMIXIN, false);
            doSuperTypes(node);
            doOptions(node);
            doItemDefs(node);
            name = null;
        }
        return info;
    }

    private boolean doNameSpace(Session session) throws ParseException {
        if (!currentTokenEquals('<')) {
            return false;
        }
        nextToken();
        String prefix = currentToken;
        nextToken();
        if (!currentTokenEquals('=')) {
            lexer.fail("Missing = in namespace decl.");
        }
        nextToken();
        String uri = currentToken;
        nextToken();
        if (!currentTokenEquals('>')) {
            lexer.fail("Missing > in namespace decl.");
        }
        String oldPrefix = null;
        try {
            oldPrefix = session.getNamespacePrefix(uri);
        } catch (RepositoryException e) {
            // assume does not exist yet, so register
        }
        try {
            if (oldPrefix == null) {
                session.getWorkspace().getNamespaceRegistry().registerNamespace(prefix, uri);
            } else if (!oldPrefix.equals(prefix)) {
                // remap
                oldMappings.put(oldPrefix, uri);
                session.setNamespacePrefix(prefix, uri);
            }
        } catch (RepositoryException e) {
            lexer.fail("unable to remap namespace", e);
        }
        nextToken();
        return true;
    }

    private String doNodeTypeName() throws ParseException {
        String name;
        if (!currentTokenEquals(Lexer.BEGIN_NODE_TYPE_NAME)) {
            lexer.fail("Missing '" + Lexer.BEGIN_NODE_TYPE_NAME + "' delimiter for beginning of node type name");
        }
        nextToken();
        name = ISO9075.decode(currentToken);

        nextToken();
        if (!currentTokenEquals(Lexer.END_NODE_TYPE_NAME)) {
            lexer.fail("Missing '" + Lexer.END_NODE_TYPE_NAME + "' delimiter for end of node type name, found " + currentToken);
        }
        nextToken();

        return name;
    }

    private static void setProperty(Node node, String name, List<String> values)
            throws RepositoryException {
        node.setProperty(name, values.toArray(new String[values.size()]));
    }

    private static void setProperty(Node node, String name, List<String> values, int type)
            throws RepositoryException {
        node.setProperty(name, values.toArray(new String[values.size()]), type);
    }

    private void doSuperTypes(Node ntd) throws ParseException, RepositoryException {
        // a set would be nicer here, in case someone defines a super type twice.
        // but due to issue [JCR-333], the resulting node type definition is
        // not symmetric anymore and the tests will fail.
        List<String> supertypes = new ArrayList<String>();
        if (!currentTokenEquals(Lexer.EXTENDS)) {
            return;
        }
        do {
            nextToken();
            supertypes.add(ISO9075.decode(currentToken));
            nextToken();
        } while (currentTokenEquals(Lexer.LIST_DELIMITER));
        setProperty(ntd, JcrConstants.JCR_SUPERTYPES, supertypes);
    }

    private void doOptions(Node ntd) throws ParseException, RepositoryException {
        if (currentTokenEquals(Lexer.ORDERABLE)) {
            ntd.setProperty(JcrConstants.JCR_HASORDERABLECHILDNODES, true);
            nextToken();
            if (currentTokenEquals(Lexer.MIXIN)) {
                ntd.setProperty(JcrConstants.JCR_ISMIXIN, true);
                nextToken();
            }
        } else if (currentTokenEquals(Lexer.MIXIN)) {
            ntd.setProperty(JcrConstants.JCR_ISMIXIN, true);
            nextToken();
            if (currentTokenEquals(Lexer.ORDERABLE)) {
                ntd.setProperty(JcrConstants.JCR_HASORDERABLECHILDNODES, true);
                nextToken();
            }
        }
    }

    private void doItemDefs(Node ntd) throws ParseException, RepositoryException {
        while (currentTokenEquals(Lexer.PROPERTY_DEFINITION) || currentTokenEquals(Lexer.CHILD_NODE_DEFINITION)) {
            if (currentTokenEquals(Lexer.PROPERTY_DEFINITION)) {
                Node pdi = ntd.addNode(JcrConstants.JCR_PROPERTYDEFINITION);
                nextToken();
                doPropertyDefinition(pdi);

            } else if (currentTokenEquals(Lexer.CHILD_NODE_DEFINITION)) {
                Node ndi = ntd.addNode(JcrConstants.JCR_CHILDNODEDEFINITION);

                nextToken();
                doChildNodeDefinition(ndi);
            }
        }
    }

    private void doPropertyDefinition(Node pdi) throws ParseException, RepositoryException {
        String name = ISO9075.decode(currentToken);
        if (!name.equals("") && !name.equals("*")) {
            pdi.setProperty(JcrConstants.JCR_NAME, name);
        }
        // init mandatory props
        pdi.setProperty(JcrConstants.JCR_AUTOCREATED, false);
        pdi.setProperty(JcrConstants.JCR_MANDATORY, false);
        pdi.setProperty(JcrConstants.JCR_MULTIPLE, false);
        pdi.setProperty(JcrConstants.JCR_ONPARENTVERSION, "COPY");
        pdi.setProperty(JcrConstants.JCR_PROTECTED, false);
        pdi.setProperty(JcrConstants.JCR_REQUIREDTYPE, "UNDEFINED");

        nextToken();
        int type = doPropertyType(pdi);
        doPropertyDefaultValue(pdi, type);
        doPropertyAttributes(pdi);
        doPropertyValueConstraints(pdi);
    }

    private int doPropertyType(Node pdi) throws ParseException, RepositoryException {
        if (!currentTokenEquals(Lexer.BEGIN_TYPE)) {
            return PropertyType.UNDEFINED;
        }
        nextToken();
        int type = PropertyType.UNDEFINED;
        if (currentTokenEquals(Lexer.STRING)) {
            type = PropertyType.STRING;
        } else if (currentTokenEquals(Lexer.BINARY)) {
            type = PropertyType.BINARY;
        } else if (currentTokenEquals(Lexer.LONG)) {
            type = PropertyType.LONG;
        } else if (currentTokenEquals(Lexer.DOUBLE)) {
            type = PropertyType.DOUBLE;
        } else if (currentTokenEquals(Lexer.BOOLEAN)) {
            type = PropertyType.BOOLEAN;
        } else if (currentTokenEquals(Lexer.DATE)) {
            type = PropertyType.DATE;
        } else if (currentTokenEquals(Lexer.NAME)) {
            type = PropertyType.NAME;
        } else if (currentTokenEquals(Lexer.PATH)) {
            type = PropertyType.PATH;
        } else if (currentTokenEquals(Lexer.REFERENCE)) {
            type = PropertyType.REFERENCE;
        } else if (currentTokenEquals(Lexer.UNDEFINED)) {
            type = PropertyType.UNDEFINED;
        } else {
            lexer.fail("Unknown property type '" + currentToken + "' specified");
        }
        pdi.setProperty(JcrConstants.JCR_REQUIREDTYPE, PropertyType.nameFromValue(type).toUpperCase());
        nextToken();
        if (!currentTokenEquals(Lexer.END_TYPE)) {
            lexer.fail("Missing '" + Lexer.END_TYPE + "' delimiter for end of property type");
        }
        nextToken();
        return type;
    }

    private void doPropertyAttributes(Node pdi) throws ParseException, RepositoryException {
        while (currentTokenEquals(Lexer.ATTRIBUTE)) {
            if (currentTokenEquals(Lexer.PRIMARY)) {
                Value name = pdi.getProperty(JcrConstants.JCR_NAME).getValue();
                if (pdi.getParent().hasProperty(JcrConstants.JCR_PRIMARYITEMNAME)) {
                    lexer.fail("More than one primary item specified in node type '" + name.getString() + "'");
                }
                pdi.getParent().setProperty(JcrConstants.JCR_PRIMARYITEMNAME, name);
            } else if (currentTokenEquals(Lexer.AUTOCREATED)) {
                pdi.setProperty(JcrConstants.JCR_AUTOCREATED, true);
            } else if (currentTokenEquals(Lexer.MANDATORY)) {
                pdi.setProperty(JcrConstants.JCR_MANDATORY, true);
            } else if (currentTokenEquals(Lexer.PROTECTED)) {
                pdi.setProperty(JcrConstants.JCR_PROTECTED, true);
            } else if (currentTokenEquals(Lexer.MULTIPLE)) {
                pdi.setProperty(JcrConstants.JCR_MULTIPLE, true);
            } else if (currentTokenEquals(Lexer.COPY)) {
                pdi.setProperty(JcrConstants.JCR_ONPARENTVERSION, "COPY");
            } else if (currentTokenEquals(Lexer.VERSION)) {
                pdi.setProperty(JcrConstants.JCR_ONPARENTVERSION, "VERSION");
            } else if (currentTokenEquals(Lexer.INITIALIZE)) {
                pdi.setProperty(JcrConstants.JCR_ONPARENTVERSION, "INITIALIZE");
            } else if (currentTokenEquals(Lexer.COMPUTE)) {
                pdi.setProperty(JcrConstants.JCR_ONPARENTVERSION, "COMPUTE");
            } else if (currentTokenEquals(Lexer.IGNORE)) {
                pdi.setProperty(JcrConstants.JCR_ONPARENTVERSION, "IGNORE");
            } else if (currentTokenEquals(Lexer.ABORT)) {
                pdi.setProperty(JcrConstants.JCR_ONPARENTVERSION, "ABORT");
            }
            nextToken();
        }
    }

    private void doPropertyDefaultValue(Node pdi, int type)
            throws ParseException, RepositoryException {
        if (!currentTokenEquals(Lexer.DEFAULT)) {
            return;
        }
        List<String> defaultValues = new ArrayList<String>();
        do {
            nextToken();
            defaultValues.add(currentToken);
            nextToken();
        } while (currentTokenEquals(Lexer.LIST_DELIMITER));
        // use required type
        setProperty(pdi, JcrConstants.JCR_DEFAULTVALUES, defaultValues, type);
    }

    private void doPropertyValueConstraints(Node pdi) throws ParseException, RepositoryException {
        if (!currentTokenEquals(Lexer.CONSTRAINT)) {
            return;
        }
        List<String> constraints = new ArrayList<String>();
        do {
            nextToken();
            constraints.add(currentToken);
            nextToken();
        } while (currentTokenEquals(Lexer.LIST_DELIMITER));
        setProperty(pdi, JcrConstants.JCR_VALUECONSTRAINTS, constraints);
    }

    private void doChildNodeDefinition(Node ndi) throws ParseException, RepositoryException {
        String name = ISO9075.decode(currentToken);
        if (!name.equals("") && !name.equals("*")) {
            ndi.setProperty(JcrConstants.JCR_NAME, name);
        }
        // init mandatory props
        ndi.setProperty(JcrConstants.JCR_AUTOCREATED, false);
        ndi.setProperty(JcrConstants.JCR_MANDATORY, false);
        ndi.setProperty(JcrConstants.JCR_SAMENAMESIBLINGS, false);
        ndi.setProperty(JcrConstants.JCR_ONPARENTVERSION, "COPY");
        ndi.setProperty(JcrConstants.JCR_PROTECTED, false);
        ndi.setProperty(JcrConstants.JCR_REQUIREDPRIMARYTYPES, Constants.EMPTY_STRING_ARRAY);

        nextToken();
        doChildNodeRequiredTypes(ndi);
        doChildNodeDefaultType(ndi);
        doChildNodeAttributes(ndi);
    }

    private void doChildNodeRequiredTypes(Node ndi) throws ParseException, RepositoryException {
        if (!currentTokenEquals(Lexer.BEGIN_TYPE)) {
            return;
        }
        List<String> types = new ArrayList<String>();
        do {
            nextToken();
            types.add(ISO9075.decode(currentToken));
            nextToken();
        } while (currentTokenEquals(Lexer.LIST_DELIMITER));
        setProperty(ndi, JcrConstants.JCR_REQUIREDPRIMARYTYPES, types);
        nextToken();
    }

    private void doChildNodeDefaultType(Node ndi) throws ParseException, RepositoryException {
        if (!currentTokenEquals(Lexer.DEFAULT)) {
            return;
        }
        nextToken();
        ndi.setProperty(JcrConstants.JCR_DEFAULTPRIMARYTYPE, ISO9075.decode(currentToken));
        nextToken();
    }

    private void doChildNodeAttributes(Node ndi) throws ParseException, RepositoryException {
        while (currentTokenEquals(Lexer.ATTRIBUTE)) {
            if (currentTokenEquals(Lexer.PRIMARY)) {
                Value name = ndi.getProperty(JcrConstants.JCR_NAME).getValue();
                if (ndi.getParent().hasProperty(JcrConstants.JCR_PRIMARYITEMNAME)) {
                    lexer.fail("More than one primary item specified in node type '" + name.getString() + "'");
                }
                ndi.getParent().setProperty(JcrConstants.JCR_PRIMARYITEMNAME, name);
            } else if (currentTokenEquals(Lexer.AUTOCREATED)) {
                ndi.setProperty(JcrConstants.JCR_AUTOCREATED, true);
            } else if (currentTokenEquals(Lexer.MANDATORY)) {
                ndi.setProperty(JcrConstants.JCR_MANDATORY, true);
            } else if (currentTokenEquals(Lexer.PROTECTED)) {
                ndi.setProperty(JcrConstants.JCR_PROTECTED, true);
            } else if (currentTokenEquals(Lexer.MULTIPLE)) {
                ndi.setProperty(JcrConstants.JCR_SAMENAMESIBLINGS, true);
            } else if (currentTokenEquals(Lexer.COPY)) {
                ndi.setProperty(JcrConstants.JCR_ONPARENTVERSION, "COPY");
            } else if (currentTokenEquals(Lexer.VERSION)) {
                ndi.setProperty(JcrConstants.JCR_ONPARENTVERSION, "VERSION");
            } else if (currentTokenEquals(Lexer.INITIALIZE)) {
                ndi.setProperty(JcrConstants.JCR_ONPARENTVERSION, "INITIALIZE");
            } else if (currentTokenEquals(Lexer.COMPUTE)) {
                ndi.setProperty(JcrConstants.JCR_ONPARENTVERSION, "COMPUTE");
            } else if (currentTokenEquals(Lexer.IGNORE)) {
                ndi.setProperty(JcrConstants.JCR_ONPARENTVERSION, "IGNORE");
            } else if (currentTokenEquals(Lexer.ABORT)) {
                ndi.setProperty(JcrConstants.JCR_ONPARENTVERSION, "ABORT");
            }
            nextToken();
        }
    }

    /**
     * Gets the next token from the underlying lexer.
     *
     * @see Lexer#getNextToken()
     * @throws ParseException if the lexer fails to get the next token.
     */
    private void nextToken() throws ParseException {
        currentToken = lexer.getNextToken();
    }

    /**
     * Checks if the {@link #currentToken} is semantically equal to the given
     * argument.
     *
     * @param s the tokens to compare with
     * @return <code>true</code> if equals; <code>false</code> otherwise.
     */
    private boolean currentTokenEquals(String[] s) {
        for (String value : s) {
            if (currentToken.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the {@link #currentToken} is semantically equal to the given
     * argument.
     *
     * @param c the tokens to compare with
     * @return <code>true</code> if equals; <code>false</code> otherwise.
     */
    private boolean currentTokenEquals(char c) {
        return currentToken.length() == 1 && currentToken.charAt(0) == c;
    }

    /**
     * Checks if the {@link #currentToken} is semantically equal to the given
     * argument.
     *
     * @param s the tokens to compare with
     * @return <code>true</code> if equals; <code>false</code> otherwise.
     */
    private boolean currentTokenEquals(String s) {
        return currentToken.equals(s);
    }

    
}
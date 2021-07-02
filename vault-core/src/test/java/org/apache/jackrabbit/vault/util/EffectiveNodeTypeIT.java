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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.nodetype.InvalidNodeTypeDefinitionException;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeExistsException;
import javax.jcr.nodetype.PropertyDefinition;

import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EffectiveNodeTypeIT extends IntegrationTestBase {

    private static final String MY_PRIMARY_TYPE = "my:Folder";
    private static final String MY_MIXIN = "my:Mixin";

    private Node node;

    @Before
    public void before() throws IOException, InvalidNodeTypeDefinitionException, NodeTypeExistsException, UnsupportedRepositoryOperationException, ParseException, RepositoryException {
        try (Reader reader = new InputStreamReader(getStream("mynodetypes.cnd"), StandardCharsets.UTF_8)) {
            // register cnd
            CndImporter.registerNodeTypes(reader, admin);
        }
        node = admin.getRootNode().addNode("test", MY_PRIMARY_TYPE);
    }

    @After
    public void after() {
        clean("/test");
    }

    @Test
    public void testGetApplicablePropertyDefinition() throws RepositoryException, ParseException, IOException {
        node.addMixin(MY_MIXIN);
        EffectiveNodeType effectiveNodeType = EffectiveNodeType.ofNode(node);

        // this should be the named property definition from mixin type
        PropertyDefinition pd = effectiveNodeType.getApplicablePropertyDefinition("my:protectedProperty", false, PropertyType.BOOLEAN);
        assertNotNull(pd);
        assertTrue(pd.isProtected());

        // this should be the residual property definition from primary type
        pd = effectiveNodeType.getApplicablePropertyDefinition("my:stringProperty", false, PropertyType.STRING);
        assertNotNull(pd);
        assertFalse(pd.isMandatory());

        // this should be inherited property definition from primary type
        pd = effectiveNodeType.getApplicablePropertyDefinition("jcr:createdBy", false, PropertyType.STRING);
        assertNotNull(pd);
        assertTrue(pd.isProtected());
        assertTrue(pd.isAutoCreated());
    }

    @Test
    public void testGetApplicableChildNodeDefinition() throws RepositoryException, ParseException, IOException {
        node.addMixin(MY_MIXIN);
        EffectiveNodeType effectiveNodeType = EffectiveNodeType.ofNode(node);
        NodeType myPrimaryType = admin.getWorkspace().getNodeTypeManager().getNodeType(MY_PRIMARY_TYPE);
        NodeType myMixinType = admin.getWorkspace().getNodeTypeManager().getNodeType(MY_MIXIN);

        // this should be the named child node definition from mixin type
        NodeDefinition nd = effectiveNodeType.getApplicableChildNodeDefinition("my:protectedChildNode", myPrimaryType, myMixinType);
        assertNotNull(nd);
        assertTrue(nd.isProtected());

        // this should be the residual child node definition from primary type
        nd = effectiveNodeType.getApplicableChildNodeDefinition("my:otherNode", myPrimaryType);
        assertNotNull(nd);
        assertTrue(nd.isMandatory());
    }
}

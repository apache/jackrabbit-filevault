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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.jcr.GuestCredentials;
import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;
import javax.jcr.version.VersionException;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal;
import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.vault.fs.Mounter;
import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFileSystem;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.junit.Assume;
import org.junit.Test;

public class DocViewSaxFormatterIT extends IntegrationTestBase {

    static String getSerializedAggregate(Session session, String rootNodePath) throws URISyntaxException, RepositoryException, IOException {
        return getSerializedAggregate(session, rootNodePath, rootNodePath.substring(1));
    }

    static String getSerializedAggregate(Session session, String filterRootNodePath, String relAggregatePath) throws URISyntaxException, RepositoryException, IOException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet(filterRootNodePath));
        RepositoryAddress addr = new RepositoryAddress("/" + session.getWorkspace().getName() + "/");
        VaultFileSystem jcrfs = Mounter.mount(null, filter, addr, null, session);
        Aggregate a = jcrfs.getAggregateManager().getRoot().getAggregate(relAggregatePath); //strip leading "/"
        DocViewSerializer s = new DocViewSerializer(a);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            s.writeContent(out);
            return out.toString("utf-8");
        }
    }

    /**
     * Tests if an 'empty' node serialization includes the jcr namespace. see JCRVLT-266
     */
    @Test
    public void testFormatterIncludesJcrNamespace() throws Exception {
        // rep:itemNames restrictions are only supported in oak.
        Assume.assumeTrue(isOak());

        JcrUtils.getOrCreateByPath("/testroot", NodeType.NT_UNSTRUCTURED, admin);
        admin.save();

        // setup access control
        AccessControlManager acMgr = admin.getAccessControlManager();
        JackrabbitAccessControlList acl = AccessControlUtils.getAccessControlList(acMgr, "/testroot");

        Privilege[] privs = new Privilege[]{acMgr.privilegeFromName(Privilege.JCR_READ)};
        Map<String, Value[]> rest = new HashMap<>();
        rest.put("rep:itemNames", new Value[]{
                admin.getValueFactory().createValue("jcr:mixinTypes", PropertyType.NAME),
                admin.getValueFactory().createValue("jcr:primaryType", PropertyType.NAME)
        });
        acl.addEntry(EveryonePrincipal.getInstance(), privs, false, null, rest);
        acMgr.setPolicy("/testroot", acl);
        admin.save();

        Session guest = repository.login(new GuestCredentials());
        String serialization = getSerializedAggregate(guest, "/testroot");

        assertEquals("valid xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\"/>\n", serialization);
    }

    /**
     * Tests minimal serialization
     */
    @Test
    public void testMinimalSerialization() throws Exception {
        JcrUtils.getOrCreateByPath("/testroot", NodeType.NT_UNSTRUCTURED, admin);
        admin.save();

        String serialization = getSerializedAggregate(admin, "/testroot");

        assertEquals("valid xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\"\n" +
                "    jcr:primaryType=\"nt:unstructured\"/>\n", serialization);
    }

    /**
     * Tests export of mixed case named properties serialization
     */
    @Test
    public void testMixedCaseSerialization() throws Exception {
        Node node = JcrUtils.getOrCreateByPath("/testroot", NodeType.NT_UNSTRUCTURED, admin);
        node.setProperty("testproperty", "lowercase");
        node.setProperty("TestProperty", "MixedCase");
        admin.save();

        String serialization = getSerializedAggregate(admin, "/testroot");

        assertEquals("valid xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\"\n" +
                "    jcr:primaryType=\"nt:unstructured\"\n" +
                "    TestProperty=\"MixedCase\"\n" +
                "    testproperty=\"lowercase\"/>\n", serialization);
    }

    @Test
    public void testSnsNodeNames() throws RepositoryException, URISyntaxException, IOException {
        Assume.assumeFalse(isOak()); // same-name siblings are only supported in Jackrabbit2
        Node node = JcrUtils.getOrCreateByPath("/testroot", NodeType.NT_UNSTRUCTURED, admin);
        node.addNode("childnode",  NodeType.NT_UNSTRUCTURED);
        node.addNode("childnode",  NodeType.NT_UNSTRUCTURED);
        node.addNode("childnode",  NodeType.NT_UNSTRUCTURED);
        admin.save();

        String serialization = getSerializedAggregate(admin, "/testroot");

        assertEquals("valid xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\"\n" +
                "    jcr:primaryType=\"nt:unstructured\">\n" +
                "    <childnode jcr:primaryType=\"nt:unstructured\"/>\n" +
                "    <childnode_x005b_2_x005d_ jcr:primaryType=\"nt:unstructured\"/>\n" +
                "    <childnode_x005b_3_x005d_ jcr:primaryType=\"nt:unstructured\"/>\n" +
                "</jcr:root>\n", serialization);
    }

    @Test
    public void testSpecialNames() throws RepositoryException, URISyntaxException, IOException {
        Node node = JcrUtils.getOrCreateByPath("/testroot", NodeType.NT_UNSTRUCTURED, admin);
        node.addNode("1test",  NodeType.NT_UNSTRUCTURED);
        Node childNode = node.addNode("test%test",  NodeType.NT_UNSTRUCTURED);
        childNode.setProperty("_test&test", "test");
        admin.save();

        String serialization = getSerializedAggregate(admin, "/testroot");

        assertEquals("valid xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\"\n" +
                "    jcr:primaryType=\"nt:unstructured\">\n" +
                "    <_x0031_test jcr:primaryType=\"nt:unstructured\"/>\n" +
                "    <test_x0025_test\n" +
                "        jcr:primaryType=\"nt:unstructured\"\n" +
                "        _test_x0026_test=\"test\"/>\n" +
                "</jcr:root>\n", serialization);
    }

    @Test
    public void testEmptyOrderNodes() throws ItemExistsException, PathNotFoundException, NoSuchNodeTypeException, LockException, VersionException, ConstraintViolationException, RepositoryException, URISyntaxException, IOException {
        // orderable child nodes
        Node node = JcrUtils.getOrCreateByPath("/testroot", NodeType.NT_UNSTRUCTURED, admin);
        node.addNode("child1", NodeType.NT_UNSTRUCTURED);
        node.addNode("child2", NodeType.NT_UNSTRUCTURED);
        node.addNode("child3", NodeType.NT_UNSTRUCTURED);
        admin.save();

        String serialization = getSerializedAggregate(admin, "/testroot/child2", "testroot");

        assertEquals("valid xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\"\n" +
                "    jcr:primaryType=\"nt:unstructured\">\n" +
                "    <child1/>\n" +
                "    <child2 jcr:primaryType=\"nt:unstructured\"/>\n" +
                "    <child3/>\n" +
                "</jcr:root>\n", serialization);
    }

    @Test
    public void testSortedMixinValues() throws RepositoryException, URISyntaxException, IOException {
        Node node = JcrUtils.getOrCreateByPath("/testroot", NodeType.NT_UNSTRUCTURED, admin);
        node.addMixin(JcrConstants.MIX_LOCKABLE);
        node.addMixin(JcrConstants.MIX_TITLE);
        node.addMixin(JcrConstants.MIX_CREATED);
        node.setProperty("customMv", new String[] { "value2", "value3", "value1" });
        admin.save();

        String serialization = getSerializedAggregate(admin, "/testroot");

        assertEquals("valid xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\" xmlns:mix=\"http://www.jcp.org/jcr/mix/1.0\"\n" +
                "    jcr:mixinTypes=\"[mix:created,mix:lockable,mix:title]\"\n" + // sort mixins alphabetically
                "    jcr:primaryType=\"nt:unstructured\"\n" +
                "    customMv=\"[value2,value3,value1]\"/>\n", serialization); // don't sort other mv-properties
    }

    @Test
    public void testUnprotectedAndProtectedProperties() throws RepositoryException, URISyntaxException, IOException {
        Node node = JcrUtils.getOrCreateByPath("/testroot", NodeType.NT_UNSTRUCTURED, admin);
        node.addMixin(JcrConstants.MIX_LOCKABLE);
        node.addMixin(JcrConstants.MIX_LAST_MODIFIED); // adds protected, auto-created properties jcr:lastModified (DATE) and jcr:lastModifiedBy (String)
        node.addMixin(JcrConstants.MIX_VERSIONABLE);
        Calendar date = Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC), Locale.ROOT);
        node.setProperty("jcr:created", date);
        admin.save();

        String serialization = getSerializedAggregate(admin, "/testroot");

        assertTrue(node.hasProperty(JcrConstants.JCR_BASEVERSION));
        // export should contain both protected and unprotected properties
        // only the ones which have a special name and(!) are protected are excluded (like jcr:baseVersion)
        assertEquals("valid xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\" xmlns:mix=\"http://www.jcp.org/jcr/mix/1.0\"\n" +
                "    jcr:created=\"{Date}"+ ISO8601.format(date) + "\"\n" +
                "    jcr:isCheckedOut=\"{Boolean}true\"\n" +
                "    jcr:lastModified=\"{Date}" + ISO8601.format(node.getProperty("jcr:lastModified").getDate()) + "\"\n" +
                "    jcr:lastModifiedBy=\"admin\"\n" +
                "    jcr:mixinTypes=\"[mix:lastModified,mix:lockable,mix:versionable]\"\n" +
                "    jcr:primaryType=\"nt:unstructured\"\n" +
                "    jcr:uuid=\"" + node.getIdentifier() + "\"/>\n"
                , serialization); 
    }
}

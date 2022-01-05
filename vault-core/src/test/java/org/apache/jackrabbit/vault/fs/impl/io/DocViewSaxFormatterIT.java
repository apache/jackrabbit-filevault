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

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.GuestCredentials;
import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.nodetype.NodeType;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.core.security.principal.EveryonePrincipal;
import org.apache.jackrabbit.vault.fs.Mounter;
import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFileSystem;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DocViewSaxFormatterIT extends IntegrationTestBase {


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

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/testroot"));
        RepositoryAddress addr = new RepositoryAddress("/" + admin.getWorkspace().getName() + "/");
        VaultFileSystem jcrfs = Mounter.mount(null, filter, addr, null, guest);
        Aggregate a = jcrfs.getAggregateManager().getRoot().getAggregate("testroot");
        DocViewSerializer s = new DocViewSerializer(a);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        s.writeContent(out);

        assertEquals("valid xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\"/>\n", out.toString("utf-8"));
    }

    /**
     * Tests minimal serialization
     */
    @Test
    public void testMinimalSerialization() throws Exception {
        JcrUtils.getOrCreateByPath("/testroot", NodeType.NT_UNSTRUCTURED, admin);
        admin.save();

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/testroot"));
        RepositoryAddress addr = new RepositoryAddress("/" + admin.getWorkspace().getName() + "/");
        VaultFileSystem jcrfs = Mounter.mount(null, filter, addr, null, admin);
        Aggregate a = jcrfs.getAggregateManager().getRoot().getAggregate("testroot");
        DocViewSerializer s = new DocViewSerializer(a);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        s.writeContent(out);

        assertEquals("valid xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\"\n" +
                "    jcr:primaryType=\"nt:unstructured\"/>\n", out.toString("utf-8"));
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

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/testroot"));
        RepositoryAddress addr = new RepositoryAddress("/" + admin.getWorkspace().getName() + "/");
        VaultFileSystem jcrfs = Mounter.mount(null, filter, addr, null, admin);
        Aggregate a = jcrfs.getAggregateManager().getRoot().getAggregate("testroot");
        DocViewSerializer s = new DocViewSerializer(a);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        s.writeContent(out);

        assertEquals("valid xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\"\n" +
                "    jcr:primaryType=\"nt:unstructured\"\n" +
                "    TestProperty=\"MixedCase\"\n" +
                "    testproperty=\"lowercase\"/>\n", out.toString("utf-8"));
    }

}

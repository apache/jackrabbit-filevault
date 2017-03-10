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

import java.io.IOException;
import java.util.Calendar;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.junit.Test;

/**
 * {@code RCPTest}...
 */
public class RCPTest extends IntegrationTestBase {


    public static final String SRC_PATH = "/testroot/src";

    public static final String SRC_TEST_NODE_PATH = "/testroot/src/a";

    public static final String DST_PATH = "/testroot/dst";

    public static final String DST_TEST_NODE_PATH = "/testroot/dst/a";

    @Test
    public void testSimple() throws IOException, RepositoryException, ConfigurationException {
        Node a = JcrUtils.getOrCreateByPath(SRC_TEST_NODE_PATH, NodeType.NT_UNSTRUCTURED, NodeType.NT_UNSTRUCTURED, admin, true);
        a.setProperty("p0", "0");
        a.setProperty("p1", "1");
        a.setProperty("m0", new String[]{"0", "1", "2"}, PropertyType.STRING);
        admin.save();

        assertNodeExists(SRC_TEST_NODE_PATH);

        RepositoryCopier rcp = new RepositoryCopier();
        rcp.copy(admin, SRC_PATH, admin, DST_PATH, true);

        assertProperty(DST_TEST_NODE_PATH + "/p0", "0");
        assertProperty(DST_TEST_NODE_PATH + "/p1", "1");
        assertProperty(DST_TEST_NODE_PATH + "/m0", new String[]{"0", "1", "2"});
    }

    @Test
    public void testMixin() throws IOException, RepositoryException, ConfigurationException {
        Node a = JcrUtils.getOrCreateByPath(SRC_TEST_NODE_PATH, NodeType.NT_FOLDER, NodeType.NT_FOLDER, admin, true);
        RepositoryCopier rcp = new RepositoryCopier();
        a.addMixin(NodeType.MIX_TITLE);
        a.setProperty("jcr:title", "Hello");
        admin.save();

        rcp.copy(admin, SRC_PATH, admin, DST_PATH, true);
        assertProperty(DST_TEST_NODE_PATH + "/jcr:title", "Hello");
        assertProperty(DST_TEST_NODE_PATH + "/jcr:mixinTypes", new String[]{"mix:title"});
    }

    @Test
    public void testAddMixin() throws IOException, RepositoryException, ConfigurationException {
        Node a = JcrUtils.getOrCreateByPath(SRC_TEST_NODE_PATH, NodeType.NT_FOLDER, NodeType.NT_FOLDER, admin, true);
        RepositoryCopier rcp = new RepositoryCopier();
        rcp.copy(admin, SRC_PATH, admin, DST_PATH, true);
        assertNodeExists(DST_TEST_NODE_PATH);
        assertPropertyMissing(DST_TEST_NODE_PATH + "/jcr:title");

        a.addMixin(NodeType.MIX_TITLE);
        a.setProperty("jcr:title", "Hello");
        admin.save();
        assertProperty(SRC_TEST_NODE_PATH + "/jcr:title", "Hello");

        rcp = new RepositoryCopier();
        rcp.setOnlyNewer(false);
        rcp.setUpdate(true);
        rcp.copy(admin, SRC_PATH, admin, DST_PATH, true);

        assertProperty(DST_TEST_NODE_PATH + "/jcr:title", "Hello");
        assertProperty(DST_TEST_NODE_PATH + "/jcr:mixinTypes", new String[]{"mix:title"});
    }

    @Test
    public void testRemoveMixin() throws IOException, RepositoryException, ConfigurationException {
        Node a = JcrUtils.getOrCreateByPath(SRC_TEST_NODE_PATH, NodeType.NT_FOLDER, NodeType.NT_FOLDER, admin, true);
        RepositoryCopier rcp = new RepositoryCopier();
        a.addMixin(NodeType.MIX_TITLE);
        a.setProperty("jcr:title", "Hello");
        admin.save();

        rcp.copy(admin, SRC_PATH, admin, DST_PATH, true);
        assertProperty(DST_TEST_NODE_PATH + "/jcr:title", "Hello");
        assertProperty(DST_TEST_NODE_PATH + "/jcr:mixinTypes", new String[]{"mix:title"});


        a.removeMixin(NodeType.MIX_TITLE);
        admin.save();
        // removing a mixing should remove the undeclared properties
        assertPropertyMissing(SRC_TEST_NODE_PATH + "/jcr:title");

        rcp = new RepositoryCopier();
        rcp.setOnlyNewer(false);
        rcp.setUpdate(true);
        rcp.copy(admin, SRC_PATH, admin, DST_PATH, true);

        assertNodeExists(DST_TEST_NODE_PATH);
        assertPropertyMissing(DST_TEST_NODE_PATH + "/jcr:mixinTypes");
        assertPropertyMissing(DST_TEST_NODE_PATH + "/jcr:title");
    }

    @Test
    public void testOnlyNewer() throws IOException, RepositoryException, ConfigurationException {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(now.getTimeInMillis() + 1);

        // create /testroot/src/a/jcr:content with 'now' as last modified
        Node a = JcrUtils.getOrCreateByPath(SRC_TEST_NODE_PATH, NodeType.NT_FOLDER, NodeType.NT_FILE, admin, false);
        Node content = a.addNode(Node.JCR_CONTENT, NodeType.NT_UNSTRUCTURED);
        content.setProperty(Property.JCR_LAST_MODIFIED, now);
        content.setProperty("p0", "0");
        admin.save();
        assertProperty(SRC_TEST_NODE_PATH + "/jcr:content/p0", "0");

        RepositoryCopier rcp = new RepositoryCopier();
        rcp.setOnlyNewer(false);
        rcp.setUpdate(true);
        rcp.copy(admin, SRC_PATH, admin, DST_PATH, true);
        assertProperty(DST_TEST_NODE_PATH + "/jcr:content/p0", "0");

        // modify property but don't update last modified
        content.setProperty("p0", "1");
        admin.save();
        rcp = new RepositoryCopier();
        rcp.setOnlyNewer(true);
        rcp.setUpdate(true);
        rcp.copy(admin, SRC_PATH, admin, DST_PATH, true);
        // property should still be the old value, since src is not "newer"
        assertProperty(DST_TEST_NODE_PATH + "/jcr:content/p0", "0");

        // now update last modified
        content.setProperty(Property.JCR_LAST_MODIFIED, then);
        admin.save();
        rcp = new RepositoryCopier();
        rcp.setOnlyNewer(true);
        rcp.setUpdate(true);
        rcp.copy(admin, SRC_PATH, admin, DST_PATH, true);
        // property should now be the new value, since src is now "newer"
        assertProperty(DST_TEST_NODE_PATH + "/jcr:content/p0", "1");

    }

    /**
     * Special test where the source node is nt:file with no mixins, and the destination node is nt:file with a mixin
     * and properties, and content is updated since it is newer (JCRVLT-87)
     */
    @Test
    public void testMissingMixinWithNewer() throws IOException, RepositoryException, ConfigurationException {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(now.getTimeInMillis() + 1);

        // create /testroot/src/a/jcr:content with 'now' as last modified
        Node a = JcrUtils.getOrCreateByPath(SRC_TEST_NODE_PATH, NodeType.NT_FOLDER, NodeType.NT_FILE, admin, false);
        Node content = a.addNode(Node.JCR_CONTENT, NodeType.NT_UNSTRUCTURED);
        content.setProperty(Property.JCR_LAST_MODIFIED, now);
        content.setProperty("p0", "0");
        admin.save();
        assertProperty(SRC_TEST_NODE_PATH + "/jcr:content/p0", "0");

        RepositoryCopier rcp = new RepositoryCopier();
        rcp.setOnlyNewer(false);
        rcp.setUpdate(true);
        rcp.copy(admin, SRC_PATH, admin, DST_PATH, true);
        assertProperty(DST_TEST_NODE_PATH + "/jcr:content/p0", "0");

        // modify source property and add mixin to destination
        content.setProperty("p0", "1");
        Node dst = admin.getNode(DST_TEST_NODE_PATH);
        dst.addMixin(NodeType.MIX_TITLE);
        dst.setProperty(Property.JCR_TITLE, "Hello");
        admin.save();
        assertProperty(DST_TEST_NODE_PATH + "/jcr:title", "Hello");
        assertProperty(DST_TEST_NODE_PATH + "/jcr:mixinTypes", new String[]{"mix:title"});

        // now perform copy
        rcp = new RepositoryCopier();
        rcp.setOnlyNewer(true);
        rcp.setUpdate(true);
        rcp.copy(admin, SRC_PATH, admin, DST_PATH, true);

        // property should still be the old value, since src is not "newer"
        assertProperty(DST_TEST_NODE_PATH + "/jcr:content/p0", "0");
        // mixin should already be gone, since file does not have a lastModified.
        assertPropertyMissing(DST_TEST_NODE_PATH + "/jcr:mixinTypes");
        assertPropertyMissing(DST_TEST_NODE_PATH + "/jcr:title");

        // now update last modified
        content.setProperty(Property.JCR_LAST_MODIFIED, then);
        admin.save();
        rcp = new RepositoryCopier();
        rcp.setOnlyNewer(true);
        rcp.setUpdate(true);
        rcp.copy(admin, SRC_PATH, admin, DST_PATH, true);

        // property should now be the new value, since src is now "newer"
        assertProperty(DST_TEST_NODE_PATH + "/jcr:content/p0", "1");
    }


}
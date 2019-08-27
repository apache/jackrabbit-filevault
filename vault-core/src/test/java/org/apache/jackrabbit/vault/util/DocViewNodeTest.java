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

import java.util.HashMap;
import java.util.Map;

import javax.jcr.NamespaceException;
import javax.jcr.PropertyType;

import org.junit.Assert;
import org.junit.Test;

public class DocViewNodeTest {

    @Test
    public void testEquals() throws NamespaceException {
        Map<String, DocViewProperty> properties1 = new HashMap<>();
        properties1.put("property1", new DocViewProperty("property1", new String[] {"value1"}, false, PropertyType.STRING));
        DocViewNode node1 = new DocViewNode("name", "label", "uuid1", properties1, new String[] { "mixin1", "mixin2" }, "primary");
        DocViewNode node2 = new DocViewNode("name", "label", "uuid1", properties1, new String[] { "mixin1", "mixin2" }, "primary");
        Assert.assertEquals(node1, node2);
        DocViewNode node3 = new DocViewNode("name", "label", "uuid1", properties1, new String[] { "mixin1", "mixin3" }, "primary");
        Assert.assertNotEquals(node1, node3);
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.validation.spi.impl.nodetype;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class NodeTypeValidatorFactoryTest {

    @Test
    public void testParseNamespaces() {
        String optionValue = "ns1=http://uri1,\n"
                + "     ns2 = http://uri2,ns3=http://uri3";
        Map<String,String> actual = NodeTypeValidatorFactory.parseNamespaces(optionValue);

        Map<String,String> expected = new HashMap<>();
        expected.put("ns1", "http://uri1");
        expected.put("ns2", "http://uri2");
        expected.put("ns3", "http://uri3");
        assertEquals(expected, actual);
    }

    @Test
    public void testParseNamespacesMixedInvalid() {
        String optionValue = "ns1=http://uri1,\n"
                + "     abc,def=\n,"
                + "=http://xyz,aa=bb=cc";
        Map<String,String> actual = NodeTypeValidatorFactory.parseNamespaces(optionValue);

        Map<String,String> expected = new HashMap<>();
        expected.put("ns1", "http://uri1");
        assertEquals(expected, actual);
    }

}

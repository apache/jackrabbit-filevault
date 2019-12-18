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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.NamespaceRegistry;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

public class QNameComparatorTest {

    @Test
    public void testCompare() {
        List<QName> names = new LinkedList<>();
        QName name1 = new QName("localonly");
        names.add(name1);
        QName name2 = new QName(XMLConstants.XML_NS_URI, "xhtml", XMLConstants.XML_NS_PREFIX);
        names.add(name2);
        QName name3 = new QName(NamespaceRegistry.NAMESPACE_JCR, "UpperCase", NamespaceRegistry.PREFIX_JCR);
        names.add(name3);
        QName name4 = new QName(NamespaceRegistry.NAMESPACE_JCR, "primaryType", NamespaceRegistry.PREFIX_JCR);
        names.add(name4);
        QName name5 = new QName(NamespaceRegistry.NAMESPACE_JCR, "PrimaryType", NamespaceRegistry.PREFIX_JCR);
        names.add(name5);
        Collections.sort(names, new QNameComparator());
        
        Assert.assertThat(names, Matchers.contains(name2, name5, name4, name3, name1));
    }
}

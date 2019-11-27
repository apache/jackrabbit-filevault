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

import java.util.Comparator;

import javax.xml.XMLConstants;

public class AttributeNameComparator implements Comparator<String> {

    public static final AttributeNameComparator INSTANCE = new AttributeNameComparator();

    @Override
    public int compare(final String o1, final String o2) {
        final String n1 = o1.toLowerCase();
        final String n2 = o2.toLowerCase();
        // order xmlns(:<prefix>)? attributes always to the front
        final boolean isXmlNs1 = n1.startsWith(XMLConstants.XML_NS_PREFIX);
        final boolean isXmlNs2 = n2.startsWith(XMLConstants.XML_NS_PREFIX);
        if (isXmlNs1 && !isXmlNs2) {
            return -1;
        } else if (!isXmlNs1 && isXmlNs2) {
            return 1;
        }
        final int i1 = n1.indexOf(':');
        final int i2 = n2.indexOf(':');
        if (i1 >=0 && i2 < 0) {
            return -1;
        } else if (i1 < 0 && i2 >=0) {
            return 1;
        } else {
            // if the lowercase versions are equal, they could differ in case (see JCRVLT-334)
            final int c = n1.compareTo(n2);
            if (c == 0) {
                return o1.compareTo(o2);
            }
            return c;
        }
    }
}
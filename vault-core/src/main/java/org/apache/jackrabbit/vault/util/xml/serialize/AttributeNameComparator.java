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

package org.apache.jackrabbit.vault.util.xml.serialize;

import java.util.Comparator;

import org.apache.jackrabbit.vault.util.xml.xerces.util.XMLSymbols;

public class AttributeNameComparator implements Comparator<String> {

    public static final AttributeNameComparator INSTANCE = new AttributeNameComparator();

    @Override
    public int compare(String o1, String o2) {
        String n1 = o1.toLowerCase();
        String n2 = o2.toLowerCase();
        // order xmlns(:<prefix>)? attributes always to the front
        boolean isXmlNs1 = n1.startsWith(XMLSymbols.PREFIX_XMLNS);
        boolean isXmlNs2 = n2.startsWith(XMLSymbols.PREFIX_XMLNS);
        if (isXmlNs1 && !isXmlNs2) {
            return -1;
        } else if (!isXmlNs1 && isXmlNs2) {
            return 1;
        }
        int i1 = n1.indexOf(':');
        int i2 = n2.indexOf(':');
        if (i1 >=0 && i2 < 0) {
            return -1;
        } else if (i1 < 0 && i2 >=0) {
            return 1;
        } else {
            return n1.compareTo(n2);
        }
    }
}

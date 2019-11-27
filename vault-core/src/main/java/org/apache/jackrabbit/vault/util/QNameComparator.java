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
import java.util.Locale;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

/** 
 * Compares based on prefixes and local names as follows:
 * <ol>
 * <li>first ns prefixes</li>
 * <li>then prefixed attributes</li>
 * <li>only afterwards non-prefixed attributes</li>
 * </ol>
 * The letter case does not matter for the order, except when two names are equal. Only then the case will be considered as well.
 * 
 */
public class QNameComparator implements Comparator<QName> {

    public static final QNameComparator INSTANCE = new QNameComparator();

    private static final int LESS_THAN = -1;
    private static final int EQUAL = 0;
    private static final int GREATER_THAN = 1;
    
    @Override
    public int compare(final QName o1, final QName o2) {
        // first namespace declarations
        final boolean isXmlNs1 = o1.getPrefix().equalsIgnoreCase(XMLConstants.XML_NS_PREFIX);
        final boolean isXmlNs2 = o2.getPrefix().equalsIgnoreCase(XMLConstants.XML_NS_PREFIX);
        if (isXmlNs1 && !isXmlNs2) {
            return LESS_THAN;
        } else if (!isXmlNs1 && isXmlNs2) {
            return GREATER_THAN;
        }
        // prefixed attributes before non-prefixed ones...
        if (!o1.getPrefix().equalsIgnoreCase(XMLConstants.DEFAULT_NS_PREFIX) && o2.getPrefix().equalsIgnoreCase(XMLConstants.DEFAULT_NS_PREFIX)) {
            return LESS_THAN;
        } else if (o1.getPrefix().equalsIgnoreCase(XMLConstants.DEFAULT_NS_PREFIX) && !o2.getPrefix().equalsIgnoreCase(XMLConstants.DEFAULT_NS_PREFIX)) {
            return GREATER_THAN;
        } else {
            // order first by prefix, then by local name
            String lowerCaseQName1 = o1.getPrefix().toLowerCase(Locale.ROOT) + o1.getLocalPart().toLowerCase(Locale.ROOT);
            String lowerCaseQName2 = o2.getPrefix().toLowerCase(Locale.ROOT) + o2.getLocalPart().toLowerCase(Locale.ROOT);
            final int c = lowerCaseQName1.compareTo(lowerCaseQName2);
            // if the lowercase versions are equal, they could differ in case (see JCRVLT-334)
            if (c == EQUAL) {
                String qName1 = o1.getPrefix() + o1.getLocalPart();
                String qName2 = o2.getPrefix() + o2.getLocalPart();
                return qName1.compareTo(qName2);
            }
            return c;
        }
    }
}
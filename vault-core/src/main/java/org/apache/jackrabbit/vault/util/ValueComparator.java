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

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

/**
 * Comparator for values
 */
public class ValueComparator implements Comparator<Value> {

    private static final ValueComparator INSTANCE = new ValueComparator();

    private ValueComparator() {
    }

    public static ValueComparator getInstance() {
        return INSTANCE;
    }

    public int compare(Value o1, Value o2) {
        try {
            // assume types are equal
            switch (o1.getType()) {
                case PropertyType.BINARY:
                    throw new IllegalArgumentException("sorting of binary values not supported.");
                case PropertyType.DATE:
                    return o1.getDate().compareTo(o2.getDate());
                case PropertyType.DECIMAL:
                    return o1.getDecimal().compareTo(o2.getDecimal());
                case PropertyType.DOUBLE:
                    return ((Double) o1.getDouble()).compareTo(o2.getDouble());
                case PropertyType.LONG:
                    return ((Long) o1.getLong()).compareTo(o2.getLong());
                default:
                    return o1.getString().compareTo(o2.getString());
            }
        } catch (RepositoryException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
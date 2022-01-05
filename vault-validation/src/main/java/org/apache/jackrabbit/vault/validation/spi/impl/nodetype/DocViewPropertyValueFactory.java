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
package org.apache.jackrabbit.vault.validation.spi.impl.nodetype;

import java.util.Collection;
import java.util.LinkedList;

import javax.jcr.PropertyType;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.ValueFormatException;

import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.apache.jackrabbit.vault.util.DocViewProperty;

/** Creates JCR values from DocViewProperties */
public class DocViewPropertyValueFactory {

    private final ValueFactory valueFactory;
    public DocViewPropertyValueFactory() {
        valueFactory = ValueFactoryImpl.getInstance();
    }
    
    private Value getValue(String value, int type) throws ValueFormatException {
        if (type == PropertyType.UNDEFINED) {
            // simulate behaviour of DocViewProperty.apply(...) which leverages setProperty(String name, String value)
            type = PropertyType.STRING;
        }
        return valueFactory.createValue(value, type);
    }

    public Value getValue(DocViewProperty property) throws ValueFormatException {
        return getValue(property.values[0], property.type);
    }

    public Value[] getValues(DocViewProperty property) throws ValueFormatException {
        Collection<Value> values = new LinkedList<>();
        for (String value : property.values) {
            values.add(getValue(value, property.type));
        }
        return values.toArray(new Value[values.size()]);
    }

}

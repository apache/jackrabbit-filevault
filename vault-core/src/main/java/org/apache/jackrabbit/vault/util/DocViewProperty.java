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

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;

import org.apache.jackrabbit.api.ReferenceBinary;
import org.apache.jackrabbit.commons.jackrabbit.SimpleReferenceBinary;
import org.apache.jackrabbit.util.XMLChar;
import org.apache.jackrabbit.value.ValueHelper;

/**
 * Helper class that represents a (jcr) property in the document view format.
 * It contains formatting and parsing methods for writing/reading enhanced
 * docview properties.
 *
 * <code>prop:= [ "{" type "}" ] ( value | "[" [ value { "," value } ] "]" )</code>
 */
public class DocViewProperty {

    private static final String BINARY_REF = "BinaryRef";

    /**
     * name of the property
     */
    public final String name;

    /**
     * value(s) of the property. always contains at least one value if this is
     * not a mv property.
     */
    public final String[] values;

    /**
     * indicates a MV property
     */
    public final boolean isMulti;

    /**
     * type of this property (can be undefined)
     */
    public final int type;

    /**
     * indicates a binary ref property
     */
    public final boolean isRef;

    /**
     * set of unambigous property names
     */
    private static final Set<String> UNAMBIGOUS = new HashSet<String>();
    static {
        UNAMBIGOUS.add("jcr:primaryType");
        UNAMBIGOUS.add("jcr:mixinTypes");
    }

    /**
     * Creates a new property.
     * @param name name of the property
     * @param values values.
     * @param multi multiple flag
     * @param type type of the property
     * @throws IllegalArgumentException if single value property and not
     *         exactly 1 value is given.
     */
    public DocViewProperty(String name, String[] values, boolean multi, int type) {
        this(name, values, multi, type, false);
    }

    public DocViewProperty(String name, String[] values, boolean multi, int type, boolean ref) {
        this.name = name;
        this.values = values;
        isMulti = multi;
        // validate type
        if (type == PropertyType.UNDEFINED) {
            if (name.equals("jcr:primaryType") || name.equals("jcr:mixinTypes")) {
                type = PropertyType.NAME;
            }
        }
        this.type = type;
        if (!isMulti && values.length != 1) {
            throw new IllegalArgumentException("Single value property needs exactly 1 value.");
        }
        this.isRef = ref;
    }

    /**
     * Parses a enhanced docview property string and returns the property.
     * @param name name of the property
     * @param value (attribute) value
     * @return a property
     */
    public static DocViewProperty parse(String name, String value) {
        boolean isMulti = false;
        boolean isBinaryRef = false;
        int type = PropertyType.UNDEFINED;
        int pos = 0;
        char state = 'b';
        List<String> vals = null;
        StringBuffer tmp = new StringBuffer();
        int unicode = 0;
        int unicodePos = 0;
        while (pos < value.length()) {
            char c = value.charAt(pos++);
            switch (state) {
                case 'b': // begin (type or array or value)
                    if (c == '{') {
                        state = 't';
                    } else if (c == '[') {
                        isMulti = true;
                        state = 'v';
                    } else if (c == '\\') {
                        state = 'e';
                    } else {
                        tmp.append(c);
                        state = 'v';
                    }
                    break;
                case 'a': // array (array or value)
                    if (c == '[') {
                        isMulti = true;
                        state = 'v';
                    } else if (c == '\\') {
                        state = 'e';
                    } else {
                        tmp.append(c);
                        state = 'v';
                    }
                    break;
                case 't':
                    if (c == '}') {
                        if (BINARY_REF.equals(tmp.toString())) {
                            type = PropertyType.BINARY;
                            isBinaryRef = true;
                        } else {
                            type = PropertyType.valueFromName(tmp.toString());
                        }
                        tmp.setLength(0);
                        state = 'a';
                    } else {
                        tmp.append(c);
                    }
                    break;
                case 'v': // value
                    if (c == '\\') {
                        state = 'e';
                    } else if (c == ',' && isMulti) {
                        if (vals == null) {
                            vals = new LinkedList<String>();
                        }
                        vals.add(tmp.toString());
                        tmp.setLength(0);
                    } else if (c == ']' && isMulti && pos == value.length()) {
                        if (tmp.length() > 0 || vals != null) {
                            if (vals == null) {
                                vals = new LinkedList<String>();
                            }
                            vals.add(tmp.toString());
                            tmp.setLength(0);
                        }
                    } else {
                        tmp.append(c);
                    }
                    break;
                case 'e': // escaped
                    if (c == 'u') {
                        state = 'u';
                        unicode = 0;
                        unicodePos = 0;
                    } else if (c == '0') {
                        // special case to treat empty values. see JCR-3661
                        state = 'v';
                        if (vals == null) {
                            vals = new LinkedList<String>();
                        }
                    } else {
                        state = 'v';
                        tmp.append(c);
                    }
                    break;
                case 'u': // unicode escaped
                    unicode = (unicode << 4) + Character.digit(c, 16);
                    if (++unicodePos == 4) {
                        tmp.appendCodePoint(unicode);
                        state = 'v';
                    }
                    break;

            }
        }
        String[] values;
        if (isMulti) {
            // add value if missing ']'
            if (tmp.length() > 0) {
                if (vals == null) {
                    vals = new LinkedList<String>();
                }
                vals.add(tmp.toString());
            }
            if (vals == null) {
                values = Constants.EMPTY_STRING_ARRAY;
            } else {
                values = vals.toArray(new String[vals.size()]);
            }
        } else {
            values = new String[]{tmp.toString()};
        }
        return new DocViewProperty(name, values, isMulti, type, isBinaryRef);
    }
    /**
     * Formats the given jcr property to the enhanced docview syntax.
     * @param prop the jcr property
     * @return the formatted string
     * @throws RepositoryException if a repository error occurs
     */
    public static String format(Property prop) throws RepositoryException {
        return format(prop, false, false);
    }
    
    /**
     * Formats the given jcr property to the enhanced docview syntax.
     * @param prop the jcr property
     * @param sort if <code>true</code> multivalue properties are sorted
     * @return the formatted string
     * @throws RepositoryException if a repository error occurs
     */
    public static String format(Property prop, boolean sort, boolean useBinaryReferences)
            throws RepositoryException {
        StringBuffer attrValue = new StringBuffer();
        int type = prop.getType();
        if (type == PropertyType.BINARY || isAmbiguous(prop)) {
            String referenceBinary = null;
            if (useBinaryReferences && type == PropertyType.BINARY) {
                Binary bin = prop.getBinary();
                if (bin != null && bin instanceof ReferenceBinary) {
                    referenceBinary = ((ReferenceBinary) bin).getReference();
                }
            }

            if (referenceBinary == null) {
                attrValue.append("{");
                attrValue.append(PropertyType.nameFromValue(prop.getType()));
                attrValue.append("}");
            } else {
                attrValue.append("{");
                attrValue.append(BINARY_REF);
                attrValue.append("}");
                attrValue.append(referenceBinary);
            }
        }
        // only write values for non binaries
        if (prop.getType() != PropertyType.BINARY) {
            if (prop.getDefinition().isMultiple()) {
                attrValue.append('[');
                Value[] values = prop.getValues();
                if (sort) {
                    Arrays.sort(values, ValueComparator.getInstance());
                }
                for (int i = 0; i < values.length; i++) {
                    if (i > 0) {
                        attrValue.append(',');
                    }
                    String strValue = ValueHelper.serialize(values[i], false);
                    if (values.length == 1 && strValue.length() == 0) {
                        // special case for empty string MV value (JCR-3661)
                        attrValue.append("\\0");
                    } else {
                        switch (prop.getType()) {
                            case PropertyType.STRING:
                            case PropertyType.NAME:
                            case PropertyType.PATH:
                                escape(attrValue, strValue, true);
                                break;
                            default:
                                attrValue.append(strValue);
                        }
                    }
                }
                attrValue.append(']');
            } else {
                String strValue = ValueHelper.serialize(prop.getValue(), false);
                escape(attrValue, strValue, false);
            }
        }
        return attrValue.toString();
    }

    /**
     * Escapes the value
     * @param buf buffer to append to
     * @param value value to escape
     * @param isMulti indicates multi value property
     */
    protected static void escape(StringBuffer buf, String value, boolean isMulti) {
        for (int i=0; i<value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                buf.append("\\\\");
            } else if (c == ',' && isMulti) {
                buf.append("\\,");
            } else if (i == 0 && !isMulti && (c == '[' || c == '{')) {
                buf.append('\\').append(c);
            } else if ( XMLChar.isInvalid(c)) {
                buf.append("\\u");
                buf.append(Text.hexTable[(c >> 12) & 15]);
                buf.append(Text.hexTable[(c >> 8) & 15]);
                buf.append(Text.hexTable[(c >> 4) & 15]);
                buf.append(Text.hexTable[c & 15]);
            } else {
                buf.append(c);
            }
        }
    }
    
    /**
     * Checks if the type of the given property is ambiguous in respect to it's
     * property definition. the current implementation just checks some well
     * known properties.
     *
     * @param prop the property
     * @return type
     * @throws RepositoryException if a repository error occurs
     */
    public static boolean isAmbiguous(Property prop) throws RepositoryException {
        return prop.getType() != PropertyType.STRING && !UNAMBIGOUS.contains(prop.getName());
    }

    /**
     * Sets this property on the given node
     *
     * @param node the node
     * @return <code>true</code> if the value was modified.
     * @throws RepositoryException if a repository error occurs
     */
    public boolean apply(Node node) throws RepositoryException {
        Property prop = node.hasProperty(name) ? node.getProperty(name) : null;
        // check if multiple flags are equal
        if (prop != null && isMulti != prop.getDefinition().isMultiple()) {
            prop.remove();
            prop = null;
        }
        if (prop != null) {
            int propType = prop.getType();
            if (propType != type && (propType != PropertyType.STRING || type != PropertyType.UNDEFINED)) {
                // never compare if types differ
                prop = null;
            }
        }
        if (isMulti) {
            Value[] vs = prop == null ? null : prop.getValues();
            if (vs != null && vs.length == values.length) {
                // quick check all values
                boolean modified = false;
                for (int i=0; i<vs.length; i++) {
                    if (!vs[i].getString().equals(values[i])) {
                        modified = true;
                    }
                }
                if (!modified) {
                    return false;
                }
            }
            if (type == PropertyType.UNDEFINED) {
                node.setProperty(name, values);
            } else {
                node.setProperty(name, values, type);
            }
            // assume modified
            return true;
        } else {
            Value v = prop == null ? null : prop.getValue();
            if (type == PropertyType.BINARY && isRef) {
                ReferenceBinary ref = new SimpleReferenceBinary(values[0]);
                Binary binary = node.getSession().getValueFactory().createValue(ref).getBinary();
                node.setProperty(name, binary);
                return true;
            }
            if (v == null || !v.getString().equals(values[0])) {
                try {
                    if (type == PropertyType.UNDEFINED) {
                        node.setProperty(name, values[0]);
                    } else {
                        node.setProperty(name, values[0], type);
                    }
                } catch (ValueFormatException e) {
                    // forcing string
                    node.setProperty(name, values[0], PropertyType.STRING);
                }
                return true;
            }
        }
        return false;
    }
}
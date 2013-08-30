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

import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.Value;
import javax.jcr.nodetype.PropertyDefinition;

import org.apache.jackrabbit.util.Text;
import org.mockito.Mockito;

import junit.framework.TestCase;

/**
 * <code>DocViewPropertyTest</code>...
 */
public class DocViewPropertyTest extends TestCase {

    public void testParseUndefined() {
        DocViewProperty p = DocViewProperty.parse("foo", "hello");
        assertEquals(p, false, PropertyType.UNDEFINED, "hello");
    }

    public void testParseLong() {
        DocViewProperty p = DocViewProperty.parse("foo", "{Long}1234");
        assertEquals(p, false, PropertyType.LONG, "1234");
    }

    public void testParseEmpty() {
        DocViewProperty p = DocViewProperty.parse("foo", "{Binary}");
        assertEquals(p, false, PropertyType.BINARY, "");
    }

    public void testParseSpecial() {
        DocViewProperty p = DocViewProperty.parse("foo", "\\{hello, world}");
        assertEquals(p, false, PropertyType.UNDEFINED, "{hello, world}");
        p = DocViewProperty.parse("foo", "{String}\\[hello");
        assertEquals(p, false, PropertyType.STRING, "[hello");
    }

    public void testParseStringTyped() {
        DocViewProperty p = DocViewProperty.parse("foo", "{String}hello");
        assertEquals(p, false, PropertyType.STRING, "hello");
    }

    public void testParseStringUnicode() {
        DocViewProperty p = DocViewProperty.parse("foo", "{String}he\\u000fllo");
        assertEquals(p, false, PropertyType.STRING, "he\u000fllo");
    }

    public void testParseMVString() {
        DocViewProperty p = DocViewProperty.parse("foo", "[hello,world]");
        assertEquals(p, true, PropertyType.UNDEFINED, "hello", "world");
        p = DocViewProperty.parse("foo", "[hello\\,world]");
        assertEquals(p, true, PropertyType.UNDEFINED, "hello,world");
    }

    public void testParseEmptyMVStrings() {
        DocViewProperty p = DocViewProperty.parse("foo", "[,a,b,c]");
        assertEquals(p, true, PropertyType.UNDEFINED, "", "a", "b", "c");
        p = DocViewProperty.parse("foo", "[a,b,c,]");
        assertEquals(p, true, PropertyType.UNDEFINED, "a", "b", "c", "");
        p = DocViewProperty.parse("foo", "[,,,]");
        assertEquals(p, true, PropertyType.UNDEFINED, "", "", "", "");
    }

    public void testParseMVSpecial() {
        DocViewProperty p = DocViewProperty.parse("foo", "[\\[hello,world]");
        assertEquals(p, true, PropertyType.UNDEFINED, "[hello", "world");
        p = DocViewProperty.parse("foo", "[[hello],[world]]");
        assertEquals(p, true, PropertyType.UNDEFINED, "[hello]", "[world]");
        p = DocViewProperty.parse("foo", "[he\\[llo,world]");
        assertEquals(p, true, PropertyType.UNDEFINED, "he[llo", "world");
        p = DocViewProperty.parse("foo", "[hello\\[,world]");
        assertEquals(p, true, PropertyType.UNDEFINED, "hello[", "world");
        p = DocViewProperty.parse("foo", "[hello,\\[world]");
        assertEquals(p, true, PropertyType.UNDEFINED, "hello", "[world");
        p = DocViewProperty.parse("foo", "[hello,world\\[]");
        assertEquals(p, true, PropertyType.UNDEFINED, "hello", "world[");
        p = DocViewProperty.parse("foo", "[hello,world");
        assertEquals(p, true, PropertyType.UNDEFINED, "hello", "world");
        p = DocViewProperty.parse("foo", "[bla{a\\,b},foo{a\\,b},bar{a\\,b}]");
        assertEquals(p, true, PropertyType.UNDEFINED, "bla{a,b}", "foo{a,b}", "bar{a,b}");
        p = DocViewProperty.parse("foo", "[/content/[a-z]{2\\,3}/[a-z]{2\\,3}(/.*)]");
        assertEquals(p, true, PropertyType.UNDEFINED, "/content/[a-z]{2,3}/[a-z]{2,3}(/.*)");
    }

    public void testParseMVLong() {
        DocViewProperty p = DocViewProperty.parse("foo", "{Long}[1,2]");
        assertEquals(p, true, PropertyType.LONG, "1", "2");
    }

    public void testParseMVLongEmpty() {
        DocViewProperty p = DocViewProperty.parse("foo", "{Long}[]");
        assertEquals(p, true, PropertyType.LONG);
    }

    public void testParseMVStringEmpty() {
        DocViewProperty p = DocViewProperty.parse("foo", "[]");
        assertEquals(p, true, PropertyType.UNDEFINED);
    }

    /**
     * Special test for mv properties with 1 empty string value (JCR-3661)
     * @throws Exception
     */
    public void testEmptyMVString() throws Exception {
        Property p = Mockito.mock(Property.class);
        Value value = Mockito.mock(Value.class);

        Mockito.when(value.getString()).thenReturn("");
        Value[] values = new Value[]{value};
        PropertyDefinition pd = Mockito.mock(PropertyDefinition.class);
        Mockito.when(pd.isMultiple()).thenReturn(true);

        Mockito.when(p.getType()).thenReturn(PropertyType.STRING);
        Mockito.when(p.getName()).thenReturn("foo");
        Mockito.when(p.getValues()).thenReturn(values);
        Mockito.when(p.getDefinition()).thenReturn(pd);

        String result = DocViewProperty.format(p);
        assertEquals("formatted property", "[\\0]", result);

        // now round trip back
        DocViewProperty dp = DocViewProperty.parse("foo", result);
        assertEquals(dp, true, PropertyType.UNDEFINED, "");
    }

    public void testEmptyMVBoolean() throws Exception {
        Property p = Mockito.mock(Property.class);
        Value value = Mockito.mock(Value.class);

        Mockito.when(value.getString()).thenReturn("false");
        Value[] values = new Value[]{value};
        PropertyDefinition pd = Mockito.mock(PropertyDefinition.class);
        Mockito.when(pd.isMultiple()).thenReturn(true);

        Mockito.when(p.getType()).thenReturn(PropertyType.BOOLEAN);
        Mockito.when(p.getName()).thenReturn("foo");
        Mockito.when(p.getValues()).thenReturn(values);
        Mockito.when(p.getDefinition()).thenReturn(pd);

        String result = DocViewProperty.format(p);
        assertEquals("formatted property", "{Boolean}[false]", result);

        // now round trip back
        DocViewProperty dp = DocViewProperty.parse("foo", result);
        assertEquals(dp, true, PropertyType.BOOLEAN, "false");
    }

    public void testEscape() {
        assertEscaped("hello", "hello", false);
        assertEscaped("hello, world", "hello, world", false);
        assertEscaped("hello, world", "hello\\, world", true);
        assertEscaped("[hello]", "\\[hello]", false);
        assertEscaped("[hello]", "[hello]", true);
        assertEscaped("{hello}", "\\{hello}", false);
        assertEscaped("{hello}", "{hello}", true);
        assertEscaped("hello\u000fworld", "hello\\u000fworld", false);
        assertEscaped("hello\u000fworld", "hello\\u000fworld", true);
        assertEscaped("hello\\world", "hello\\\\world", false);
        assertEscaped("hello\\world", "hello\\\\world", true);
    }

    private void assertEscaped(String original, String expected, boolean multi) {
        StringBuffer buf = new StringBuffer();
        DocViewProperty.escape(buf, original, multi);
        assertEquals(expected, buf.toString());
    }

    private void assertEquals(DocViewProperty p, boolean m, int type, String ... values) {
        assertEquals("Multiple", m, p.isMulti);
        assertEquals("Type", type, p.type);
        assertEquals("Array Length", values.length, p.values.length);
        assertEquals("Values", Text.implode(values, ","), Text.implode(p.values, ","));
    }
}
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

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;


/**
 * {@code DocViewPropertyTest}...
 */
public class DocViewPropertyTest {

    @Test
    public void testParseUndefined() {
        DocViewProperty p = DocViewProperty.parse("foo", "hello");
        Assert.assertEquals(new DocViewProperty("foo", new String[] {"hello"}, false, PropertyType.UNDEFINED), p);
    }

    @Test
    public void testParseLong() {
        DocViewProperty p = DocViewProperty.parse("foo", "{Long}1234");
        Assert.assertEquals(new DocViewProperty("foo", new String[] {"1234"}, false, PropertyType.LONG), p);
    }

    @Test
    public void testEquals() {
        DocViewProperty p1 = DocViewProperty.parse("foo", "{Long}1234");
        DocViewProperty p2 = DocViewProperty.parse("foo", "{Long}1234");
        Assert.assertEquals(p1, p2);
        DocViewProperty p3 = DocViewProperty.parse("foo", "{String}1234");
        Assert.assertNotEquals(p1, p3);
    }

    @Test
    public void testParseEmpty() {
        DocViewProperty p = DocViewProperty.parse("foo", "{Binary}");
        Assert.assertEquals(new DocViewProperty("foo", new String[] { ""}, false, PropertyType.BINARY), p);
    }

    
    @Test
    public void testParseSpecial() {
        DocViewProperty p = DocViewProperty.parse("foo", "\\{hello, world}");
        assertEquals(p, false, PropertyType.UNDEFINED, "{hello, world}");
        p = DocViewProperty.parse("foo", "{String}\\[hello");
        assertEquals(p, false, PropertyType.STRING, "[hello");
    }

    @Test
    public void testParseStringTyped() {
        DocViewProperty p = DocViewProperty.parse("foo", "{String}hello");
        assertEquals(p, false, PropertyType.STRING, "hello");
    }

    @Test
    public void testParseStringUnicode() {
        DocViewProperty p = DocViewProperty.parse("foo", "{String}he\\u000fllo");
        assertEquals(p, false, PropertyType.STRING, "he\u000fllo");
    }

    @Test
    public void testParseMVString() {
        DocViewProperty p = DocViewProperty.parse("foo", "[hello,world]");
        assertEquals(p, true, PropertyType.UNDEFINED, "hello", "world");
        p = DocViewProperty.parse("foo", "[hello\\,world]");
        assertEquals(p, true, PropertyType.UNDEFINED, "hello,world");
    }

    @Test
    public void testParseEmptyMVStrings() {
        DocViewProperty p = DocViewProperty.parse("foo", "[,a,b,c]");
        assertEquals(p, true, PropertyType.UNDEFINED, "", "a", "b", "c");
        p = DocViewProperty.parse("foo", "[a,b,c,]");
        assertEquals(p, true, PropertyType.UNDEFINED, "a", "b", "c", "");
        p = DocViewProperty.parse("foo", "[,,,]");
        assertEquals(p, true, PropertyType.UNDEFINED, "", "", "", "");
    }

    @Test
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

    @Test
    public void testParseMVLong() {
        DocViewProperty p = DocViewProperty.parse("foo", "{Long}[1,2]");
        assertEquals(p, true, PropertyType.LONG, "1", "2");
    }

    @Test
    public void testParseMVLongEmpty() {
        DocViewProperty p = DocViewProperty.parse("foo", "{Long}[]");
        assertEquals(p, true, PropertyType.LONG);
    }

    @Test
    public void testParseMVStringEmpty() {
        DocViewProperty p = DocViewProperty.parse("foo", "[]");
        assertEquals(p, true, PropertyType.UNDEFINED);
    }

    /**
     * Special test for mv properties with 1 empty string value (JCR-3661)
     * @throws Exception
     */
    @Test
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
        Assert.assertEquals("formatted property", "[\\0]", result);

        // now round trip back
        DocViewProperty dp = DocViewProperty.parse("foo", result);
        Assert.assertEquals(new DocViewProperty("foo", new String[] {""}, true, PropertyType.UNDEFINED), dp);
    }

    @Test
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
        Assert.assertEquals("formatted property", "{Boolean}[false]", result);

        // now round trip back
        DocViewProperty dp = DocViewProperty.parse("foo", result);
        Assert.assertEquals(new DocViewProperty("foo", new String[] {"false"}, true, PropertyType.BOOLEAN), dp);
    }

    @Test
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
        Assert.assertEquals(expected, DocViewProperty.escape(original, multi));
    }

    private void assertEquals(DocViewProperty p, boolean multi, int type, String... values) {
        Assert.assertEquals(new DocViewProperty(p.name, values, multi, type), p);
    }

}
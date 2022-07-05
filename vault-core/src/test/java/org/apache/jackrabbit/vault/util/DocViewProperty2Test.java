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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import javax.jcr.Binary;
import javax.jcr.NamespaceException;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.nodetype.PropertyDefinition;

import org.apache.jackrabbit.commons.jackrabbit.SimpleReferenceBinary;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.NameResolver;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceMapping;
import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;


public class DocViewProperty2Test {

    private NameResolver nameResolver;
    private Name nameFoo = NameFactoryImpl.getInstance().create("{}foo");

    @Before
    public void setUp() throws NamespaceException {
        NamespaceMapping nsMapping = new NamespaceMapping();
        nsMapping.setMapping(Name.NS_EMPTY_PREFIX, Name.NS_DEFAULT_URI);
        nameResolver = new DefaultNamePathResolver(nsMapping);
    }

    @Test
    public void testParseUndefined() throws IllegalNameException, NamespaceException {
        DocViewProperty2 p = DocViewProperty2.parse("foo", "hello", nameResolver);
        Assert.assertEquals(new DocViewProperty2(nameFoo, "hello", PropertyType.UNDEFINED), p);
    }

    @Test
    public void testParseLong() throws IllegalNameException, NamespaceException {
        DocViewProperty2 p = DocViewProperty2.parse("foo", "{Long}1234", nameResolver);
        Assert.assertEquals(new DocViewProperty2(nameFoo, "1234", PropertyType.LONG), p);
    }

    @Test
    public void testParseUnknownPrefix() throws IllegalNameException, NamespaceException {
        Assert.assertThrows(NamespaceException.class, () -> DocViewProperty2.parse("cq:foo", "test", nameResolver));
    }

    @Test
    public void testEquals() throws IllegalNameException, NamespaceException {
        DocViewProperty2 p1 = DocViewProperty2.parse("foo", "{Long}1234", nameResolver);
        DocViewProperty2 p2 = DocViewProperty2.parse("foo", "{Long}1234", nameResolver);
        Assert.assertEquals(p1, p2);
        DocViewProperty2 p3 = DocViewProperty2.parse("foo", "{String}1234", nameResolver);
        Assert.assertNotEquals(p1, p3);
    }

    @Test
    public void testParseEmpty() throws IllegalNameException, NamespaceException {
        DocViewProperty2 p = DocViewProperty2.parse("foo", "{Binary}", nameResolver);
        Assert.assertEquals(new DocViewProperty2(nameFoo, "", PropertyType.BINARY), p);
    }

    @Test
    public void testParseSpecial() throws IllegalNameException, NamespaceException {
        DocViewProperty2 p = DocViewProperty2.parse("foo", "\\{hello, world}", nameResolver);
        assertEquals(p, false, PropertyType.UNDEFINED, "{hello, world}");
        p = DocViewProperty2.parse("foo", "{String}\\[hello", nameResolver);
        assertEquals(p, false, PropertyType.STRING, "[hello");
    }

    @Test
    public void testParseStringTyped() throws IllegalNameException, NamespaceException {
        DocViewProperty2 p = DocViewProperty2.parse("foo", "{String}hello", nameResolver);
        assertEquals(p, false, PropertyType.STRING, "hello");
    }

    @Test
    public void testParseStringUnicode() throws IllegalNameException, NamespaceException {
        DocViewProperty2 p = DocViewProperty2.parse("foo", "{String}he\\u000fllo", nameResolver);
        assertEquals(p, false, PropertyType.STRING, "he\u000fllo");
    }

    @Test
    public void testParseMVString() throws IllegalNameException, NamespaceException {
        DocViewProperty2 p = DocViewProperty2.parse("foo", "[hello,world]", nameResolver);
        assertEquals(p, true, PropertyType.UNDEFINED, "hello", "world");
        p = DocViewProperty2.parse("foo", "[hello\\,world]", nameResolver);
        assertEquals(p, true, PropertyType.UNDEFINED, "hello,world");
    }

    @Test
    public void testParseEmptyMVStrings() throws IllegalNameException, NamespaceException {
        DocViewProperty2 p = DocViewProperty2.parse("foo", "[,a,b,c]", nameResolver);
        assertEquals(p, true, PropertyType.UNDEFINED, "", "a", "b", "c");
        p = DocViewProperty2.parse("foo", "[a,b,c,]", nameResolver);
        assertEquals(p, true, PropertyType.UNDEFINED, "a", "b", "c", "");
        p = DocViewProperty2.parse("foo", "[,,,]", nameResolver);
        assertEquals(p, true, PropertyType.UNDEFINED, "", "", "", "");
    }

    @Test
    public void testParseMVSpecial() throws IllegalNameException, NamespaceException {
        DocViewProperty2 p = DocViewProperty2.parse("foo", "[\\[hello,world]", nameResolver);
        assertEquals(p, true, PropertyType.UNDEFINED, "[hello", "world");
        p = DocViewProperty2.parse("foo", "[[hello],[world]]", nameResolver);
        assertEquals(p, true, PropertyType.UNDEFINED, "[hello]", "[world]");
        p = DocViewProperty2.parse("foo", "[he\\[llo,world]", nameResolver);
        assertEquals(p, true, PropertyType.UNDEFINED, "he[llo", "world");
        p = DocViewProperty2.parse("foo", "[hello\\[,world]", nameResolver);
        assertEquals(p, true, PropertyType.UNDEFINED, "hello[", "world");
        p = DocViewProperty2.parse("foo", "[hello,\\[world]", nameResolver);
        assertEquals(p, true, PropertyType.UNDEFINED, "hello", "[world");
        p = DocViewProperty2.parse("foo", "[hello,world\\[]", nameResolver);
        assertEquals(p, true, PropertyType.UNDEFINED, "hello", "world[");
        p = DocViewProperty2.parse("foo", "[hello,world", nameResolver);
        assertEquals(p, true, PropertyType.UNDEFINED, "hello", "world");
        p = DocViewProperty2.parse("foo", "[bla{a\\,b},foo{a\\,b},bar{a\\,b}]", nameResolver);
        assertEquals(p, true, PropertyType.UNDEFINED, "bla{a,b}", "foo{a,b}", "bar{a,b}");
        p = DocViewProperty2.parse("foo", "[/content/[a-z]{2\\,3}/[a-z]{2\\,3}(/.*)]", nameResolver);
        assertEquals(p, true, PropertyType.UNDEFINED, "/content/[a-z]{2,3}/[a-z]{2,3}(/.*)");
    }

    @Test
    public void testParseMVLong() throws IllegalNameException, NamespaceException {
        DocViewProperty2 p = DocViewProperty2.parse("foo", "{Long}[1,2]", nameResolver);
        assertEquals(p, true, PropertyType.LONG, "1", "2");
    }

    @Test
    public void testParseMVLongEmpty() throws IllegalNameException, NamespaceException {
        DocViewProperty2 p = DocViewProperty2.parse("foo", "{Long}[]", nameResolver);
        assertEquals(p, true, PropertyType.LONG);
    }

    @Test
    public void testParseMVStringEmpty() throws IllegalNameException, NamespaceException {
        DocViewProperty2 p = DocViewProperty2.parse("foo", "[]", nameResolver);
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
        Session session = Mockito.mock(Session.class);

        Mockito.when(value.getString()).thenReturn("");
        Value[] values = new Value[]{value};
        PropertyDefinition pd = Mockito.mock(PropertyDefinition.class);
        Mockito.when(pd.isMultiple()).thenReturn(true);

        Mockito.when(p.getType()).thenReturn(PropertyType.STRING);
        Mockito.when(p.getName()).thenReturn("foo");
        Mockito.when(p.getValues()).thenReturn(values);
        Mockito.when(p.getDefinition()).thenReturn(pd);
        Mockito.when(p.getSession()).thenReturn(session);

        Mockito.when(session.getNamespaceURI(Name.NS_EMPTY_PREFIX)).thenReturn(Name.NS_DEFAULT_URI);

        String result = DocViewProperty2.format(p);
        Assert.assertEquals("formatted property", "[\\0]", result);

        // now round trip back
        DocViewProperty2 dp = DocViewProperty2.parse("foo", result, nameResolver);
        Assert.assertEquals(new DocViewProperty2(nameFoo, Arrays.asList(""), PropertyType.UNDEFINED), dp);
    }

    @Test
    public void testEmptyMVBoolean() throws Exception {
        Property p = Mockito.mock(Property.class);
        Value value = Mockito.mock(Value.class);
        Session session = Mockito.mock(Session.class);

        Mockito.when(value.getString()).thenReturn("false");
        Value[] values = new Value[]{value};
        PropertyDefinition pd = Mockito.mock(PropertyDefinition.class);
        Mockito.when(pd.isMultiple()).thenReturn(true);

        Mockito.when(p.getType()).thenReturn(PropertyType.BOOLEAN);
        Mockito.when(p.getName()).thenReturn("foo");
        Mockito.when(p.getValues()).thenReturn(values);
        Mockito.when(p.getDefinition()).thenReturn(pd);
        Mockito.when(p.getSession()).thenReturn(session);

        Mockito.when(session.getNamespaceURI(Name.NS_EMPTY_PREFIX)).thenReturn(Name.NS_DEFAULT_URI);

        String result = DocViewProperty2.format(p);
        Assert.assertEquals("formatted property", "{Boolean}[false]", result);

        // now round trip back
        DocViewProperty2 dp = DocViewProperty2.parse("foo", result, nameResolver);
        Assert.assertEquals(new DocViewProperty2(nameFoo, Arrays.asList("false"), PropertyType.BOOLEAN), dp);
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

    @Test
    public void testFromValues() throws RepositoryException, IOException {
        ValueFactory valueFactory = ValueFactoryImpl.getInstance();
        // test empty multi-value
        assertEquals(DocViewProperty2.fromValues(nameFoo, new Value[0], PropertyType.BINARY, true, false, false), true, PropertyType.BINARY);
        // test single value
        assertEquals(DocViewProperty2.fromValues(nameFoo, new Value[] {valueFactory.createValue(1.1)}, PropertyType.DOUBLE, false, false, false), false, PropertyType.DOUBLE, "1.1");

        // binary reference (enabled)
        Binary binary = new SimpleReferenceBinary("myid");
        Value value = valueFactory.createValue(binary);
        assertEquals(DocViewProperty2.fromValues(nameFoo, new Value[]{ value }, PropertyType.BINARY, false, false, true), false, PropertyType.BINARY, true, "myid");
        // binary reference (disabled)
        assertEquals(DocViewProperty2.fromValues(nameFoo, new Value[]{ value }, PropertyType.BINARY, false, false, false), false, PropertyType.BINARY, false, "");

        // binary reference multi-value (enabled)
        Binary binary2 = new SimpleReferenceBinary("myid2");
        Value value2 = valueFactory.createValue(binary2);
        assertEquals(DocViewProperty2.fromValues(nameFoo, new Value[]{ value, value2 }, PropertyType.BINARY, true, false, true), true, PropertyType.BINARY, true, "myid", "myid2");
        // binary reference multi-value (disabled)
        assertEquals(DocViewProperty2.fromValues(nameFoo, new Value[]{ value, value2 }, PropertyType.BINARY, true, false, false), true, PropertyType.BINARY, false, "", "");

        // regular binary (references enabled)
        try (InputStream input = new ByteArrayInputStream("testüøö".getBytes(StandardCharsets.UTF_8))) {
            value = valueFactory.createValue(valueFactory.createBinary(input));
        }
        assertEquals(DocViewProperty2.fromValues(nameFoo, new Value[]{ value }, PropertyType.BINARY, false, false, true), false, PropertyType.BINARY, false, "");
        // regular binary (references disabled)
        assertEquals(DocViewProperty2.fromValues(nameFoo, new Value[]{ value }, PropertyType.BINARY, false, false, false), false, PropertyType.BINARY, false, "");

        // regular binary  multi-value (references enabled)
        assertEquals(DocViewProperty2.fromValues(nameFoo, new Value[]{ value, value }, PropertyType.BINARY, true, false, true), true, PropertyType.BINARY, false, "", "");
        // regular binary  multi-value (references disabled)
        assertEquals(DocViewProperty2.fromValues(nameFoo, new Value[]{ value, value }, PropertyType.BINARY, true, false, false), true, PropertyType.BINARY, false, "", "");
    }

    @Test
    public void testFormat() {
        DocViewProperty2 property = new DocViewProperty2(nameFoo, "value");
        Assert.assertEquals("value", property.formatValue());
        property = new DocViewProperty2(nameFoo, "true", PropertyType.BOOLEAN);
        Assert.assertEquals("{Boolean}true", property.formatValue());
        property = new DocViewProperty2(nameFoo, Arrays.asList("path1", "path2"), PropertyType.PATH);
        Assert.assertEquals("{Path}[path1,path2]", property.formatValue());
        property = new DocViewProperty2(nameFoo, Arrays.asList(""), PropertyType.STRING);
        Assert.assertEquals("[\\0]", property.formatValue());
        property = new DocViewProperty2(nameFoo, Collections.singletonList("1234"), false, PropertyType.BINARY, true);
        Assert.assertEquals("{BinaryRef}1234", property.formatValue());
    }

    private void assertEscaped(String original, String expected, boolean multi) {
        Assert.assertEquals(expected, DocViewProperty2.escape(original, multi));
    }

    private void assertEquals(DocViewProperty2 p, boolean multi, int type, String... values) {
        assertEquals(p, multi, type, false, values);
    }

    private void assertEquals(DocViewProperty2 p, boolean multi, int type, boolean isReferenceProperty, String... values) {
        Assert.assertEquals(new DocViewProperty2(p.getName(), Arrays.asList(values), multi, type, isReferenceProperty), p);
    }
}
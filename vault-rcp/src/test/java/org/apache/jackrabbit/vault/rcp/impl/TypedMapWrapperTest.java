/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.rcp.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TypedMapWrapperTest {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(JSON_FACTORY);
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
            new TypeReference<Map<String, Object>>() {};

    @Test
    public void testParsedJson() throws IOException {
        try (InputStream is = this.getClass().getResourceAsStream("/edit_task.json")) {
            assertNotNull(is);
            Map<String, Object> parsed;
            try (JsonParser parser = JSON_FACTORY.createParser(is)) {
                parsed = OBJECT_MAPPER.readValue(parser, MAP_TYPE_REFERENCE);
            }
            TypedMapWrapper map = new TypedMapWrapper(parsed);
            assertTrue(map.getBoolean("onlyNewer"));
            assertEquals("copy-geometrixx", map.getString("id"));
            MatcherAssert.assertThat(
                    map.getStringList("excludes"), Matchers.contains("/content/geometrixx/en/tools(/.*)?"));
            assertThrows(IllegalArgumentException.class, () -> map.getString("invalid")); // no key with that name
            assertThrows(IllegalArgumentException.class, () -> map.getString("onlyNewer")); // value of different type
        }
    }
}

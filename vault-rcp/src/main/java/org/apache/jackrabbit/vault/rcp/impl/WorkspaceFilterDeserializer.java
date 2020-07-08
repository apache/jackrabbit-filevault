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
package org.apache.jackrabbit.vault.rcp.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

public class WorkspaceFilterDeserializer extends StdDeserializer<WorkspaceFilter> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    protected WorkspaceFilterDeserializer() {
        super(WorkspaceFilter.class);
    }

    @Override
    public WorkspaceFilter deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        if (p.nextValue() != JsonToken.VALUE_STRING) {
            throw new JsonParseException("Expected string value", p.getCurrentLocation());
        }
        if (!p.getCurrentName().equals("sourceXml")) {
            throw new JsonParseException("Unexpected field name", p.getCurrentLocation());
        }
        String filterXml = p.getText();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = new ByteArrayInputStream(filterXml.getBytes(StandardCharsets.UTF_8))) {
            filter.load(input);
        } catch (ConfigurationException e) {
            throw JsonMappingException.from(p, "Invalid filter", e);
        }
        p.nextToken();
        return filter;
    }

}

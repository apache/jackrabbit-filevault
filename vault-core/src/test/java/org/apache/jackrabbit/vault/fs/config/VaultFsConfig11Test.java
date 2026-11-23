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
package org.apache.jackrabbit.vault.fs.config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.jackrabbit.vault.fs.api.ArtifactHandler;
import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.fs.impl.aggregator.FileFolderAggregator;
import org.apache.jackrabbit.vault.fs.impl.aggregator.UnsupportedAggregator;
import org.apache.jackrabbit.vault.fs.impl.io.FolderArtifactHandler;
import org.apache.jackrabbit.vault.fs.impl.io.GenericArtifactHandler;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VaultFsConfig11Test {

    private static final String LEGACY_CONFIG = "<vaultfs version=\"1.1\">"
            + "<aggregates>"
            + "<aggregate type=\"filefolder\"/>"
            + "<aggregate type=\"nodetype\" title=\"Node Type Aggregate\"/>"
            + "</aggregates>"
            + "<handlers>"
            + "<handler type=\"folder\"/>"
            + "<handler type=\"nodetype\"/>"
            + "<handler type=\"generic\"/>"
            + "</handlers>"
            + "</vaultfs>";

    @Test
    public void testLoadLegacyNodeTypeEntries() throws Exception {
        VaultFsConfig config = AbstractVaultFsConfig.load(
                new ByteArrayInputStream(LEGACY_CONFIG.getBytes(StandardCharsets.UTF_8)), "config.xml");

        assertEquals(2, config.getAggregators().size());
        assertTrue(config.getAggregators().get(0) instanceof FileFolderAggregator);
        UnsupportedAggregator unsupported =
                (UnsupportedAggregator) config.getAggregators().get(1);
        assertEquals("nodetype", unsupported.getType());

        assertEquals(2, config.getHandlers().size());
        for (ArtifactHandler handler : config.getHandlers()) {
            assertTrue(handler instanceof FolderArtifactHandler || handler instanceof GenericArtifactHandler);
        }
    }
}

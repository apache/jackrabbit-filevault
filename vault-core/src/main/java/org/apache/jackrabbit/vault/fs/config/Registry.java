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

package org.apache.jackrabbit.vault.fs.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.jackrabbit.vault.fs.api.Aggregator;
import org.apache.jackrabbit.vault.fs.api.ArtifactHandler;
import org.apache.jackrabbit.vault.fs.impl.aggregator.FileAggregator;
import org.apache.jackrabbit.vault.fs.impl.aggregator.FileFolderAggregator;
import org.apache.jackrabbit.vault.fs.impl.aggregator.FullCoverageAggregator;
import org.apache.jackrabbit.vault.fs.impl.aggregator.GenericAggregator;
import org.apache.jackrabbit.vault.fs.impl.aggregator.NodeTypeAggregator;
import org.apache.jackrabbit.vault.fs.impl.io.FileArtifactHandler;
import org.apache.jackrabbit.vault.fs.impl.io.FolderArtifactHandler;
import org.apache.jackrabbit.vault.fs.impl.io.GenericArtifactHandler;
import org.apache.jackrabbit.vault.fs.impl.io.NodeTypeArtifactHandler;

/**
 * <code>Registry</code>...
 */
public class Registry {

    public static Registry instance;

    private final Map<String, Class<? extends Aggregator>> aggregators;

    private final Map<String, Class<? extends ArtifactHandler>> handlers;

    public synchronized static Registry getInstance() {
        if (instance == null) {
            instance = new Registry();
        }
        return instance;
    }

    private Registry() {
        aggregators = new HashMap<String, Class<? extends Aggregator>>();
        aggregators.put("file", FileAggregator.class);
        aggregators.put("full", FullCoverageAggregator.class);
        aggregators.put("generic", GenericAggregator.class);
        aggregators.put("nodetype", NodeTypeAggregator.class);
        aggregators.put("filefolder", FileFolderAggregator.class);

        handlers = new HashMap<String, Class<? extends ArtifactHandler>>();
        handlers.put("file", FileArtifactHandler.class);
        handlers.put("folder", FolderArtifactHandler.class);
        handlers.put("nodetype", NodeTypeArtifactHandler.class);
        handlers.put("generic", GenericArtifactHandler.class);
    }

    public Class<? extends Aggregator> getAggregatorClass(String type) {
        return aggregators.get(type);
    }

    public Class<? extends ArtifactHandler> getHandlerClass(String type) {
        return handlers.get(type);
    }

    public Aggregator createAggregator(String type) {
        if (aggregators.containsKey(type)) {
            try {
                return aggregators.get(type).newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException("Unable to create aggregator of type: " + type, e);
            }
        }
        return null;
    }

    public ArtifactHandler createHandler(String type) {
        if (handlers.containsKey(type)) {
            try {
                return handlers.get(type).newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException("Unable to create handler of type: " + type, e);
            }
        }
        return null;
    }
}
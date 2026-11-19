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
package org.apache.jackrabbit.vault.fs.impl.aggregator;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 * Placeholder for an aggregate type that is no longer supported. Configurations containing it can still be loaded
 * (e.g. the {@code META-INF/vault/config.xml} of an old package which is installed), but they cannot be used for
 * aggregation (i.e. for exporting).
 */
public class UnsupportedAggregator extends GenericAggregator {

    private String type = "";

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean matches(Node node, String path) throws RepositoryException {
        throw new RepositoryException("Aggregate type '" + type + "' is no longer supported.");
    }
}

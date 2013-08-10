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

package org.apache.jackrabbit.vault.fs.impl.aggregator;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.ImportInfo;
import org.apache.jackrabbit.vault.fs.filter.IsNodeFilter;

/**
 */
public class RootAggregator extends GenericAggregator {

    /**
     * Default constructor that initializes the filters.
     */
    public RootAggregator() {
        getContentFilter().addExclude(new IsNodeFilter()).seal();
        getMatchFilter().seal();
    }

    /**
     * {@inheritDoc}
     *
     * @return <code>false</code> always
     */
    public boolean hasFullCoverage() {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @return <code>false</code> always
     */
    public boolean isDefault() {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @return "root" always
     */
    public String getName() {
        return "root";
    }


    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException since removing the root is not
     *         valid.
     */
    public ImportInfo remove(Node node, boolean recursive, boolean trySave)
            throws RepositoryException {
        throw new UnsupportedOperationException("Cannot remove root node.");
    }

}
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

import org.apache.jackrabbit.vault.fs.filter.FileFolderNodeFilter;
import org.apache.jackrabbit.vault.fs.filter.NodeTypeItemFilter;
import org.apache.jackrabbit.vault.util.JcrConstants;

/**
 * Defines an aggregator that handles file/folder like nodes. It matches
 * all nt:hierarchyNode nodes that have or define a jcr:content
 * child node and excludes child nodes that are nt:hierarchyNodes.
 */
public class FileFolderAggregator extends GenericAggregator {

    public FileFolderAggregator() {
        getMatchFilter().addInclude(
                new FileFolderNodeFilter()
        ).seal();
        getContentFilter().addExclude(
                new NodeTypeItemFilter(JcrConstants.NT_HIERARCHYNODE, true, 1, Integer.MAX_VALUE)
        ).seal();
    }

}
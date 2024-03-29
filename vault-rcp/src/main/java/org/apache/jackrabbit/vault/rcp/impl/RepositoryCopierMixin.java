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

import java.util.HashMap;
import java.util.Map;

import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.util.CredentialsProvider;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

// Don't call any functions when serializing or deserializing.
// Only look at the class variables of any visibility (including private)
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        creatorVisibility = JsonAutoDetect.Visibility.NONE,
        fieldVisibility = JsonAutoDetect.Visibility.ANY)
public abstract class RepositoryCopierMixin {
    @JsonCreator
    public RepositoryCopierMixin() {}

    @JsonIgnore
    protected ProgressTrackerListener tracker;

    @JsonIgnore
    private int numNodes;

    @JsonIgnore
    private int totalNodes;

    @JsonIgnore
    private long totalSize;

    @JsonIgnore
    private long currentSize;

    @JsonIgnore
    private long start;

    @JsonIgnore
    private String lastKnownGood;

    @JsonIgnore
    private String currentPath;

    @JsonIgnore
    private String cqLastModified;

    @JsonIgnore
    private boolean abort;

    @JsonIgnore
    private String resumeFrom;

    @JsonIgnore
    private WorkspaceFilter srcFilter;

    @JsonIgnore
    private Map<String, String> prefixMapping = new HashMap<>();

    @JsonIgnore
    private CredentialsProvider credentialsProvider;
    
    @JsonIgnore
    private Session srcSession;
    
    @JsonIgnore
    private Session dstSession;
}


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

package org.apache.jackrabbit.vault.fs.api;

/**
 * <code>ImportMode</code> is used to define how importing content is applied
 * to the existing content in the repository.
 */
public enum ImportMode {

    /**
     * Normal behavior. Existing content is replaced completely by the imported
     * content, i.e. is overridden or deleted accordingly.
     */
    REPLACE,

    /**
     * Existing content is not modified, i.e. only new content is added and
     * none is deleted or modified.
     */
    MERGE,

    /**
     * Existing content is updated, new content is added and none is deleted.
     */
    UPDATE
}
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

package org.apache.jackrabbit.vault.fs.io;

/**
 * <code>AccessControlHandling</code> defines the behavior when importing
 * access control nodes.
 */
public enum AccessControlHandling {

    /**
     * Ignores the packaged access control and leaves the target unchanged.
     */
    IGNORE,

    /**
     * Applies the access control provided with the package to the target. this
     * also removes existing access control.
     */
    OVERWRITE,

    /**
     * Tries to merge access control provided with the package with the one on
     * the target.
     *
     * This is currently not fully supported and behaves like {@link #OVERWRITE}
     * for existing ACLs. ACLs not in the package are retained.
     */
    MERGE,

    /**
     * Tries to merge access control in the content with the one provided by the package.
     *
     * This is currently not fully supported and behaves like {@link #IGNORE}
     * for existing ACLs. ACLs not in the package are retained.
     */
    MERGE_PRESERVE,

    /**
     * Clears all access control on the target system.
     */
    CLEAR,

}

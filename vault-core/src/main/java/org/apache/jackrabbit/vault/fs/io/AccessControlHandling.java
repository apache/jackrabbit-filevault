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
     * Merge access control provided with the package with the one in the
     * content by replacing the access control entries of corresponding
     * principals (i.e. package first). It never alters access control entries
     * of principals not present in the package.
     * <p/>
     * Example:<br/>
     *
     * Content ACL:
     * <pre>
     *     everyone, deny, jcr:all
     *     bob, allow, jcr:read
     *     bob, allow, jcr:write
     * </pre>
     *
     * Package ACL:
     * <pre>
     *     bob, deny, jcr:all
     *     alice, allow, jcr:read
     * </pre>
     *
     * Result ACL:
     * <pre>
     *     everyone, deny, jcr:all
     *     bob, deny, jcr:all
     *     alice, allow, jcr:read
     * </pre>
     */
    MERGE,

    /**
     * Merge access control in the content with the one provided with the
     * package by adding the access control entries of principals not present in the
     * content (i.e. content first). It never alters access control entries already
     * existing in the content.
     *
     * <p/>
     * Example:<br/>
     *
     * Content ACL:
     * <pre>
     *     everyone, deny, jcr:all
     *     bob, allow, jcr:read
     *     bob, allow, jcr:write
     * </pre>
     *
     * Package ACL:
     * <pre>
     *     bob, deny, jcr:all
     *     alice, allow, jcr:read
     * </pre>
     *
     * Result ACL:
     * <pre>
     *     everyone, deny, jcr:all
     *     bob, allow, jcr:read
     *     bob, allow, jcr:write
     *     alice, allow, jcr:read
     * </pre>
     */
    MERGE_PRESERVE,

    /**
     * Clears all access control on the target system.
     */
    CLEAR,

}

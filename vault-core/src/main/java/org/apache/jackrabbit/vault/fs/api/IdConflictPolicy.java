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

import javax.jcr.ImportUUIDBehavior;

/**
 * Defines the handling of IDs on import.
 * <p>
 * Note: {@code CREATE_NEW_ID} affects imports of nodes without id conflicts as well.
 */
public enum IdConflictPolicy {
    /** Default handling, fail in case of unresolvable conflicts. Conflicts are automatically resolved in case the conflicting UUID and all its references are inside the package filter. */
    FAIL,
    /** Create a new ID for the imported node (for all referenceable nodes), this may break existing references in the package. This fails if a referenced node is overwritten by a node with a different id */
    CREATE_NEW_ID,
    /** Remove the node with the conflicting id along with its references (even if outside the filters). This goes beyond {@link ImportUUIDBehavior#IMPORT_UUID_COLLISION_REMOVE_EXISTING}, as it also does not only resolve UUID collisions but also replacements of referenceable nodes with different ids.
     * Use with care, as this may remove references outside the filter. */
    FORCE_REMOVE_CONFLICTING_ID,
    /** Assign the newly imported conflicting node a new id in case the conflicting existing node does not have the same parent (i.e. is no sibling).
     * If the newly imported node is a sibling of the existing conflicting one either remove the existing node with the conflicting id but keep its references (in case the conflicting one is contained in the filter) 
     * or skip the to be imported node (and continue with importing its children as if they were below the existing one).
     * This was the policy which was always used in FileVault prior version 3.5.2.
     * @since 3.6.2
     */
    LEGACY
}

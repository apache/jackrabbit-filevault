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

package org.apache.jackrabbit.vault.fs.spi;

import java.io.Writer;
import java.util.Set;

import javax.jcr.Session;

/**
 * Defines a general provider for the JCR version specific services such as node
 * type management and access control handling.
 */
public interface ServiceProvider {

    /**
     * Returns the JCR version of the underlying repository
     * @return the jcr version
     */
    JcrVersion getJCRVersion();

    /**
     * Returns the names of the default built in nodetypes of the underlying
     * repository.
     *
     * @return a set of names
     */
    Set<String> getBuiltInNodeTypeNames();

    /**
     * Returns the default node type installer.
     * @param session the session to use
     * @return the default node type installer.
     */
    NodeTypeInstaller getDefaultNodeTypeInstaller(Session session);

    /**
     * Returns the default privilege installer.
     * @param session the session to use
     * @return the default privilege installer.
     */
    PrivilegeInstaller getDefaultPrivilegeInstaller(Session session);

    /**
     * Returns the default CND reader.
     * @return the default CND reader.
     */
    CNDReader getCNDReader();

    /**
     * Returns the default CND writer
     * @param out the writer
     * @param s the session
     * @param includeNS <code>true</code> if namespace should be included
     * @return the default CND writer
     */
    CNDWriter getCNDWriter(Writer out, Session s, boolean includeNS);

    /**
     * Returns the repository dependant ACL management
     * @return the ACL management
     */
    ACLManagement getACLManagement();

    /**
     * Returns the repository dependant User management or <code>null</code> if
     * the repository does not require a separate user handling.
     *
     * @return repository dependant User management or <code>null</code>
     */
    UserManagement getUserManagement();
}
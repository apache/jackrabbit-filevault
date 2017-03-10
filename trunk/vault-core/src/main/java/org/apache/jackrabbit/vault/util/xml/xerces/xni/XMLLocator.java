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

package org.apache.jackrabbit.vault.util.xml.xerces.xni;

/**
 * Location information.
 *
 * @author Andy Clark, IBM
 * @version $Id$
 */
public interface XMLLocator {

    //
    // XMLLocator methods
    //

    /**
     * Returns the public identifier.
     */
    public String getPublicId();

    /**
     * Returns the literal system identifier.
     */
    public String getLiteralSystemId();

    /**
     * Returns the base system identifier.
     */
    public String getBaseSystemId();

    /**
     * Returns the expanded system identifier.
     */
    public String getExpandedSystemId();

    /**
     * Returns the line number.
     */
    public int getLineNumber();

    /**
     * Returns the column number.
     */
    public int getColumnNumber();

    /**
     * Returns the encoding of the current entity.
     * Note that, for a given entity, this value can only be
     * considered final once the encoding declaration has been read (or once it
     * has been determined that there is no such declaration) since, no encoding
     * having been specified on the XMLInputSource, the parser
     * will make an initial "guess" which could be in error.
     */
    public String getEncoding();

} // interface XMLLocator

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
package org.apache.jackrabbit.vault.util.diff;

/**
 * Provides a default document source
 */
public class DefaultDocumentSource implements DocumentSource {

    /**
     * some location information
     */
    private final String location;

    /**
     * the author
     */
    private final String author;

    /**
     * the date
     */
    private final long date;

    /**
     * the revision
     */
    private final String revision;

    /**
     * Creates a new default document source
     *
     * @param location some location information
     * @param author the author of the document
     * @param date some date of the document
     * @param revision some revision of the document
     */
    public DefaultDocumentSource(String location, String author, long date, String revision) {
        this.location = location;
        this.author = author;
        this.date = date;
        this.revision = revision;
    }

    /**
     * {@inheritDoc}
     */
    public String getLocation() {
        return location;
    }

    /**
     * Returns the revision information.
     *
     * {@inheritDoc}
     */
    public String getLabel() {
        return revision;
    }

    /**
     * Returns the author
     * @return the author
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Returns the date
     * @return the date
     */
    public long getDate() {
        return date;
    }

    /**
     * Returns the revision
     * @return the revision
     */
    public String getRevision() {
        return revision;
    }


    public String toString() {
        return author + ", " + revision;
    }
}

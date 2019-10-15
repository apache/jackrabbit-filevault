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

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.jackrabbit.vault.fs.config.ConfigurationException;

/**
 * The item filter is used to include or exclude a set of paths.
 * It is usually part of a {@link PathFilterSet}.
 *
 */
public interface PathFilter extends Filter, Dumpable {

    /**
     * The "Catch all" item filter.
     */
    PathFilter ALL = new PathFilter() {

        /**
         * Returns always {@code true}
         */
        public boolean matches(String path) {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        public void dump(DumpContext ctx, boolean isLast) {
            ctx.println(isLast, "ALL");
        }

        /**
         * {@inheritDoc}
         */
        public boolean isAbsolute() {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        public PathFilter translate(PathMapping mapping) {
            return this;
        }

        @Override
        public int hashCode() {
            return 1;
        }

        @Override
        public boolean equals(Object obj) {
            return (obj == this);
        }

        @Override
        public String toString() {
            StringWriter stringWriter = new StringWriter();
            dump(new DumpContext(new PrintWriter(stringWriter)), true);
            return stringWriter.toString();
        }

    };

    /**
     * The "Miss all" item filter.
     */
    PathFilter NONE = new PathFilter() {

        /**
         * Returns always {@code false}
         */
        public boolean matches(String path) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        public void dump(DumpContext ctx, boolean isLast) {
            ctx.println(isLast, "NONE");
        }

        /**
         * {@inheritDoc}
         */
        public boolean isAbsolute() {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        public PathFilter translate(PathMapping mapping) {
            return this;
        }

        @Override
        public int hashCode() {
            return 2;
        }

        @Override
        public boolean equals(Object obj) {
            return (obj == this);
        }

        @Override
        public String toString() {
            StringWriter stringWriter = new StringWriter();
            dump(new DumpContext(new PrintWriter(stringWriter)), true);
            return stringWriter.toString();
        }
    };

    /**
     * Checks if the given path matches this filters criteria.
     *
     * @param path the path to check
     * @return {@code true} if this filter matches the criteria;
     *         {@code false} otherwise.
     */
    boolean matches(@Nonnull String path);

    /**
     * Checks if the pattern is absolute, i.e. does not start with a wildcard.
     * @return {@code true} if pattern is absolute
     */
    boolean isAbsolute();

    /**
     * Translates this path filter with the given mapping. Note that only absolute filters can be translated.
     * @param mapping the mapping to apply
     * @return the new filter
     * @since 2.4.10
     */
    @Nonnull
    PathFilter translate(@Nullable PathMapping mapping);
}
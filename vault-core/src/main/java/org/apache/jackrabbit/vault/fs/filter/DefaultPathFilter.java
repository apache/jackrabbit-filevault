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

package org.apache.jackrabbit.vault.fs.filter;

import java.util.regex.Pattern;

import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.PathFilter;
import org.apache.jackrabbit.vault.fs.api.PathMapping;

/**
 * The default path filter provides hierarchical filtering.
 */
public class DefaultPathFilter implements PathFilter {

    /**
     * the internal regex pattern
     */
    private Pattern regex;

    /**
     * Default constructor
     */
    public DefaultPathFilter() {
    }

    /**
     * Creates a new default path filter
     * @param pattern the pattern
     * @see #setPattern
     */
    public DefaultPathFilter(String pattern) {
        setPattern(pattern);
    }

    /**
     * Sets the regexp pattern for this filter.
     *
     * Examples:
     * <xmp>
     * | Pattern        | Matches
     * | /foo           | exactly "/foo"
     * | /foo.*         | all paths starting with "/foo"
     * | ^.* /foo[^/]*$ | all files starting with "foo"
     * | /foo/[^/]*$    | all direct children of /foo
     * | /foo/.*        | all children of /foo
     * | /foo(/.*)?     | all children of /foo and foo itself
     * </xmp>
     *
     * @param pattern the pattern.
     */
    public void setPattern(String pattern) {
        regex = Pattern.compile(pattern);
    }

    /**
     * Returns the pattern
     * @return the pattern
     */
    public String getPattern() {
        return regex.pattern();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(String path) {
        return regex.matcher(path).matches();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAbsolute() {
        return regex.pattern().startsWith("/");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PathFilter translate(PathMapping mapping) {
        if (mapping == null) {
            return this;
        }
        String pattern = regex.pattern();
        if (!pattern.startsWith("/")) {
            return this;
        }
        return new DefaultPathFilter(mapping.map(pattern));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DefaultPathFilter)) return false;
        DefaultPathFilter that = (DefaultPathFilter) o;
        return regex.toString().equals(that.regex.toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return regex.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.printf(isLast, "%s:", getClass().getSimpleName());
        ctx.indent(isLast);
        ctx.printf(true, "regex: %s", regex.toString());
        ctx.outdent();
    }
}
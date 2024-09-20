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
package org.apache.jackrabbit.vault.validation.context;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.jackrabbit.vault.validation.context.AbstractDependencyResolver.MavenCoordinates;
import org.junit.Assert;
import org.junit.Test;

public class AbstractDependencyResolverTest {

    @Test
    public void testUriToMavenCoordinates() throws URISyntaxException {
        Assert.assertEquals(new MavenCoordinates("group1", "name1", "1.0.0"), MavenCoordinates.parse(new URI("maven", "group1:name1:1.0.0", null)));
        Assert.assertEquals(new MavenCoordinates("group1", "name1", "1.0.0", "test", "myclassifier"), MavenCoordinates.parse(new URI("maven", "group1:name1:1.0.0:test:myclassifier", null)));
        Assert.assertThrows(IllegalArgumentException.class, () -> MavenCoordinates.parse(new URI("maven", "group:name", null)));
        Assert.assertNull(MavenCoordinates.parse(new URI("http://example.com")));
    }

}

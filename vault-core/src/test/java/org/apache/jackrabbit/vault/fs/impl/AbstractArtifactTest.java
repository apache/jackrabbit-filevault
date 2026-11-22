/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.fs.impl;

import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.impl.io.InputSourceArtifact;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link AbstractArtifact}. */
public class AbstractArtifactTest {

    /** Checks each path component while leaving generated directory markers intact. */
    @Test
    public void escapesRepositoryPathsBeforeAppendingExtensions() {
        DirectoryArtifact artifact = new DirectoryArtifact("foo.dir/bar.binary", ".dir");
        Assert.assertEquals("foo.dir/bar.binary", artifact.getRelativePath());
        Assert.assertEquals("foo%2edir/bar%2ebinary.dir", artifact.getPlatformPath());
        Assert.assertEquals(
                "foo.dir/bar.binary", PlatformNameFormat.getRepositoryPath(artifact.getPlatformPath(), true));
    }

    /** Checks nested artifacts, including binary properties with reserved names. */
    @Test
    public void escapesChildrenBeforeAppendingExtensions() {
        DirectoryArtifact parent = new DirectoryArtifact("foo.dir", ".dir");
        InputSourceArtifact child = new InputSourceArtifact(
                parent, "rep:policy.binary", ".binary", ArtifactType.BINARY, null, SerializationType.GENERIC);
        Assert.assertEquals("foo.dir/rep:policy.binary", child.getRelativePath());
        Assert.assertEquals("foo%2edir.dir/_rep_policy%2ebinary.binary", child.getPlatformPath());
    }

    /** Checks that document-view markers remain serialization extensions. */
    @Test
    public void preservesGeneratedContentXml() {
        DirectoryArtifact parent = new DirectoryArtifact("foo", ".dir");
        InputSourceArtifact child = new InputSourceArtifact(
                parent, "", ".content.xml", ArtifactType.PRIMARY, null, SerializationType.XML_DOCVIEW);
        Assert.assertEquals("foo.dir/.content.xml", child.getPlatformPath());
    }

    /** Checks ordinary paths and distinguishes a literal marker from a generated one. */
    @Test
    public void preservesOrdinaryPathsAndSeparatesLiteralMarkers() {
        Assert.assertEquals("foo.xml", new DirectoryArtifact("foo", ".xml").getPlatformPath());
        Assert.assertEquals("foo", new DirectoryArtifact("foo").getPlatformPath());
        Assert.assertEquals("%2econtent.xml", new DirectoryArtifact(".content.xml").getPlatformPath());
        Assert.assertEquals("foo%2edir", new DirectoryArtifact("foo.dir").getPlatformPath());
        Assert.assertEquals("foo.dir", new DirectoryArtifact("foo", ".dir").getPlatformPath());
    }
}

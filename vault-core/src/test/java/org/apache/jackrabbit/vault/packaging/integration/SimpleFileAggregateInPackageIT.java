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

package org.apache.jackrabbit.vault.packaging.integration;

import java.io.IOException;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Test;

public class SimpleFileAggregateInPackageIT extends IntegrationTestBase {

    @Test
    public void testSimpleFileAggregateOverwritesChildNodes()
            throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/testroot");

        extractVaultPackage("/test-packages/simple_file_aggregate_with_children.zip");

        // test if nodes exist
        assertNodeExists("/testroot/thumbnail.png");
        assertNodeExists("/testroot/thumbnail.png/jcr:content/dam:thumbnails/dam:thumbnail_48.png");

        extractVaultPackage("/test-packages/simple_file_aggregate_without_children.zip");

        assertNodeExists("/testroot/thumbnail.png");
        assertNodeMissing("/testroot/thumbnail.png/jcr:content/dam:thumbnails");

    }

}

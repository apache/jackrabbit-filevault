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
package org.apache.jackrabbit.vault;

import java.io.File;
import java.io.IOException;

import org.apache.jackrabbit.core.data.FileDataStore;
import org.apache.jackrabbit.oak.plugins.blob.datastore.DataStoreBlobStore;
import org.apache.jackrabbit.oak.spi.blob.BlobStore;
import org.apache.jackrabbit.vault.it.OakBlobStoreFactory;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class OakBlobStoreFactoryImpl implements OakBlobStoreFactory {

    @Override
    public BlobStore createBlobStore(File oakRepoHomeDirectory, File oakBlobStoreDirectory) throws IOException {
        oakBlobStoreDirectory.mkdirs();
        FileDataStore fds = new FileDataStore();
        fds.setMinRecordLength(4092);
        fds.setPath(oakBlobStoreDirectory.getAbsolutePath());
        fds.init(oakRepoHomeDirectory.getAbsolutePath());
        // https://issues.apache.org/jira/browse/OAK-8840
        return new DataStoreBlobStore(fds);
    }
}

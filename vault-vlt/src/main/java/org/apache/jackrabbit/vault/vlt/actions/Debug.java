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
package org.apache.jackrabbit.vault.vlt.actions;

import java.io.ByteArrayInputStream;
import java.io.File;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.VltDirectory;
import org.apache.jackrabbit.vault.vlt.VltException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>Resolved</code>...
 *
 */
public class Debug extends AbstractAction {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(Debug.class);

    private final File localDir;

    public Debug(File localDir) {
        this.localDir = localDir;
    }

    public void run(VltContext ctx) throws VltException {
        VltDirectory root = new VltDirectory(ctx, localDir);
        // mount fs at the top most directory
        if (root.isControlled()) {
            ctx.setFsRoot(root);
        }
        Session s = ctx.getFileSystem(ctx.getMountpoint()).getAggregateManager().getSession();
        byte[] buf = new byte[0x5000];
        try {
            Node tmp = s.getNode("/tmp");
            tmp.setProperty("test_binary", new ByteArrayInputStream(buf));
            tmp.setProperty("test_binary", new ByteArrayInputStream(buf));
            s.save();
        } catch (RepositoryException e) {
            log.error("Failed", e);
        }
        ctx.printMessage("done.");
    }

}
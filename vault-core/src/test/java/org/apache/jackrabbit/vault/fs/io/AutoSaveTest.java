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

package org.apache.jackrabbit.vault.fs.io;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.jcr.AccessDeniedException;
import javax.jcr.InvalidItemStateException;
import javax.jcr.ItemExistsException;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.version.VersionException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AutoSaveTest {

    @Mock
    private Session session;

    @Test
    public void testNeedsSave() {
        AutoSave autoSave = new AutoSave(100);
        assertFalse(autoSave.needsSave());
        autoSave.modified(99);
        // still below threshold
        assertFalse(autoSave.needsSave());
        autoSave.modified(1);
        assertTrue(autoSave.needsSave());
    }

    @Test
    public void testNeedsSaveAfterFailedSave() throws AccessDeniedException, ItemExistsException, ReferentialIntegrityException, ConstraintViolationException, InvalidItemStateException, VersionException, LockException, NoSuchNodeTypeException, RepositoryException {
        Mockito.doThrow(new ConstraintViolationException("Forced exception")).when(session).save();
        AutoSave autoSave = new AutoSave(100);
        autoSave.modified(100);
        assertTrue(autoSave.needsSave());
        // failed save attempt
        // need a retry after 100 more nodes
        autoSave.save(session);
        assertFalse(autoSave.needsSave());
        autoSave.modified(1);
        assertFalse(autoSave.needsSave());
        autoSave.modified(99);
        assertTrue(autoSave.needsSave());
        // 2nd failed attempt
        autoSave.save(session);
        assertFalse(autoSave.needsSave());
        autoSave.modified(1);
        assertFalse(autoSave.needsSave());
        autoSave.modified(99);
        Mockito.reset(session);
        // retry successfull
        autoSave.save(session);
        assertFalse(autoSave.needsSave());
        autoSave.modified(10);
        assertFalse(autoSave.needsSave());
        autoSave.modified(90);
        // next regular save after 100 more nodes
        assertTrue(autoSave.needsSave());
    }
}

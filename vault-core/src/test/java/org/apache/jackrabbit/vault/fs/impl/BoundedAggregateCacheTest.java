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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the bounded LRU cache behavior used for aggregate namespace prefixes.
 * This tests the memory-safe caching strategy that prevents unbounded growth
 * while still providing performance benefits through LRU eviction.
 */
public class BoundedAggregateCacheTest {

    private Map<String, String[]> cache;
    private static final int CACHE_SIZE = 10;

    @Before
    public void setUp() {
        // Create a bounded LRU cache similar to what AggregateManagerImpl uses
        cache = Collections.synchronizedMap(new LinkedHashMap<String, String[]>(CACHE_SIZE + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String[]> eldest) {
                return size() > CACHE_SIZE;
            }
        });
    }

    @Test
    public void testCacheHitAndMiss() {
        String path1 = "/content/test/page1";
        String[] prefixes1 = new String[] {"jcr", "nt", "cq"};

        // Cache miss initially
        assertNull("Cache should be empty initially", cache.get(path1));

        // Cache the prefixes
        cache.put(path1, prefixes1);

        // Cache hit
        String[] cached = cache.get(path1);
        assertNotNull("Cached prefixes should not be null", cached);
        assertArrayEquals("Cached prefixes should match", prefixes1, cached);
    }

    @Test
    public void testCacheInvalidation() {
        String path1 = "/content/test/page1";
        String[] prefixes1 = new String[] {"jcr", "nt"};

        // Cache the prefixes
        cache.put(path1, prefixes1);
        assertNotNull("Cache should contain entry", cache.get(path1));

        // Invalidate specific path
        cache.remove(path1);
        assertNull("Cache should be empty after invalidation", cache.get(path1));
    }

    @Test
    public void testCacheBoundedSize() {
        // Fill the cache to max capacity
        for (int i = 0; i < CACHE_SIZE; i++) {
            String path = "/content/test/page" + i;
            String[] prefixes = new String[] {"jcr", "nt"};
            cache.put(path, prefixes);
        }

        assertEquals("Cache size should be at max capacity", CACHE_SIZE, cache.size());

        // Add one more entry - should trigger LRU eviction
        String newPath = "/content/test/page" + CACHE_SIZE;
        String[] newPrefixes = new String[] {"jcr", "sling"};
        cache.put(newPath, newPrefixes);

        // Cache size should still be at max (oldest entry evicted)
        assertEquals("Cache size should not exceed max capacity", CACHE_SIZE, cache.size());

        // The newest entry should be present
        assertNotNull("Newest entry should be in cache", cache.get(newPath));

        // The oldest entry (page0) should have been evicted
        assertNull("Oldest entry should have been evicted", cache.get("/content/test/page0"));
    }

    @Test
    public void testLRUBehavior() {
        // Add entries up to capacity
        for (int i = 0; i < CACHE_SIZE; i++) {
            cache.put("/path" + i, new String[] {"jcr"});
        }

        // Access path0 to make it "recently used"
        cache.get("/path0");

        // Add one more entry, forcing eviction
        cache.put("/path" + CACHE_SIZE, new String[] {"jcr"});

        // path0 should still be present because we accessed it (LRU)
        // path1 should have been evicted (least recently used after path0 was accessed)
        assertNotNull("Accessed entry should still be in cache", cache.get("/path0"));
        assertNull("Least recently used entry should be evicted", cache.get("/path1"));
    }

    @Test
    public void testCacheWithMultipleEvictions() {
        // Fill cache to capacity
        for (int i = 0; i < CACHE_SIZE; i++) {
            cache.put("/path" + i, new String[] {"jcr"});
        }

        // Add 5 more entries, which should evict the 5 oldest
        for (int i = CACHE_SIZE; i < CACHE_SIZE + 5; i++) {
            cache.put("/path" + i, new String[] {"jcr"});
        }

        assertEquals("Cache size should still be at max", CACHE_SIZE, cache.size());

        // First 5 entries should be evicted
        for (int i = 0; i < 5; i++) {
            assertNull("Old entry should be evicted", cache.get("/path" + i));
        }

        // Entries 5-14 should still be present
        for (int i = 5; i < CACHE_SIZE + 5; i++) {
            assertNotNull("Recent entry should still be in cache", cache.get("/path" + i));
        }
    }

    @Test
    public void testConcurrentAccess() throws Exception {
        final int threadCount = 10;
        final int operationsPerThread = 100;

        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    String path = "/content/thread" + threadId + "/path" + i;
                    String[] prefixes = new String[] {"jcr", "nt", "thread" + threadId};

                    // Cache operation
                    cache.put(path, prefixes);

                    // Retrieve operation
                    cache.get(path);

                    // Invalidate some entries
                    if (i % 10 == 0) {
                        cache.remove(path);
                    }
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(5000); // 5 second timeout
        }

        // Verify cache is still functional and bounded
        assertTrue("Cache size should not exceed max", cache.size() <= CACHE_SIZE);
        cache.put("/test", new String[] {"jcr"});
        assertNotNull("Cache should still be functional", cache.get("/test"));
    }

    @Test
    public void testAccessOrderPreservation() {
        // Fill cache
        for (int i = 0; i < CACHE_SIZE; i++) {
            cache.put("/path" + i, new String[] {"prefix" + i});
        }

        // Access some entries in a specific order to make them more recent
        cache.get("/path5");
        cache.get("/path3");
        cache.get("/path7");

        // Add 3 new entries, forcing eviction of the 3 least recently used
        cache.put("/newpath1", new String[] {"new1"});
        cache.put("/newpath2", new String[] {"new2"});
        cache.put("/newpath3", new String[] {"new3"});

        // The accessed entries should still be present
        assertNotNull("Accessed entry should survive", cache.get("/path5"));
        assertNotNull("Accessed entry should survive", cache.get("/path3"));
        assertNotNull("Accessed entry should survive", cache.get("/path7"));

        // Some of the non-accessed entries should be evicted
        int evictedCount = 0;
        for (int i = 0; i < CACHE_SIZE; i++) {
            if (cache.get("/path" + i) == null) {
                evictedCount++;
            }
        }
        assertEquals("Should have evicted 3 old entries", 3, evictedCount);
    }
}

package com.sogeti.droidnetworking.test.unit;

import com.sogeti.droidnetworking.external.LruCache;

import junit.framework.TestCase;

public class LruCacheTest extends TestCase {
    @Override public void setUp() throws Exception {
        super.setUp(); 
    }

    @Override protected void tearDown() throws Exception { 
        super.tearDown();
    }
    
    public void testCache() {
        LruCache<String, String> cache = new LruCache<String, String>(2);
        
        // The cache should be able to hold 2 entries
        assertTrue(cache.maxSize() == 2);
        
        // Put two values and check that the size is correct
        assertTrue(cache.size() == 0);   
        cache.put("1", "AA");
        assertTrue(cache.size() == 1);
        cache.put("2", "BB");
        assertTrue(cache.size() == 2);
        
        // Put one more value, the oldest value should be evicted.
        cache.put("3", "CC");
        
        // Total number of puts should be 3
        assertTrue(cache.putCount() == 3);
        
        // Key 1 should not be in the cache any more
        assertTrue(cache.get("1") == null);
        
        // One value should have been evicted so far
        assertTrue(cache.evictionCount() == 1);
        
        // No hits in the cache so far and one miss
        assertTrue(cache.hitCount() == 0);
        assertTrue(cache.missCount() == 1);
        
        // Get some data from the cache
        assertTrue(cache.get("2").equalsIgnoreCase("BB"));
        assertTrue(cache.get("3").equalsIgnoreCase("CC"));
        
        // To hits in the cache should have been recorded
        assertTrue(cache.hitCount() == 2);
        
        // Remove one value
        cache.remove("2");
        assertTrue(cache.get("2") == null);
        
        cache.put("4", "DD");
        assertTrue(cache.size() == 2);
        
        // Evict all values
        cache.evictAll();
        assertTrue(cache.size() == 0);
        
        cache.resize(3);
        
        // The cache should be able to hold 3 entries
        assertTrue(cache.maxSize() == 3);
        
        assertTrue(cache.size() == 0);   
        cache.put("1", "AA");
        assertTrue(cache.size() == 1);
        cache.put("2", "BB");
        assertTrue(cache.size() == 2);
        cache.put("3", "CC");
        assertTrue(cache.size() == 3);   
    }
}

package com.sogeti.droidnetworking.test.integration;

import com.google.mockwebserver.MockWebServer;

import com.sogeti.droidnetworking.NetworkEngine;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.RobolectricBackgroundExecutorService;

import android.app.Activity;

@RunWith(RobolectricTestRunner.class)
public class MemoryCacheTests extends CacheTests {
    @Before
    public void setup() {
        Robolectric.getFakeHttpLayer().interceptHttpRequests(false);

        server = new MockWebServer();

        NetworkEngine.getInstance().setHttpPort(8080);
        NetworkEngine.getInstance().setDiskCacheSize(0);
        NetworkEngine.getInstance().setMemoryCacheSize(1024);
        NetworkEngine.getInstance().init(new Activity());
        NetworkEngine.getInstance().setUseCache(true);
        NetworkEngine.getInstance().setSharedNetworkQueue(new RobolectricBackgroundExecutorService());

        Robolectric.getBackgroundScheduler().pause();
        Robolectric.getUiThreadScheduler().pause();
    }
}
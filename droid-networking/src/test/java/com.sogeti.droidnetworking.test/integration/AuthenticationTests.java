package com.sogeti.droidnetworking.test.integration;

import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;
import com.sogeti.droidnetworking.NetworkEngine;
import com.sogeti.droidnetworking.NetworkOperation;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.RobolectricBackgroundExecutorService;

import android.app.Activity;

@RunWith(RobolectricTestRunner.class)
public class AuthenticationTests {
    private MockWebServer server;

    @Before
    public void setup() {
        Robolectric.getFakeHttpLayer().interceptHttpRequests(false);

        server = new MockWebServer();

        NetworkEngine.getInstance().setHttpPort(8080);
        NetworkEngine.getInstance().init(new Activity());
        NetworkEngine.getInstance().setUseCache(false);
        NetworkEngine.getInstance().setSharedNetworkQueue(new RobolectricBackgroundExecutorService());

        Robolectric.getBackgroundScheduler().pause();
        Robolectric.getUiThreadScheduler().pause();
    }

    @After
    public void tearDown() throws Throwable {
        server.shutdown();
    }

    @Test
    public void basicAuthenticationWithNoUsernameOrPassword() throws Throwable {
        server.enqueue(new MockResponse().setBody("HTTP Basic: Access denied.").setResponseCode(401));
        server.play(8080);

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_PENDING);

        NetworkEngine.getInstance().executeOperation(operation);

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_ERROR);
        assertTrue(operation.getHttpStatusCode() == 401);
        assertTrue(operation.getResponseString().equals("HTTP Basic: Access denied."));

        // Verify that a authorization header wasn't sent
        RecordedRequest request = server.takeRequest();
        assertTrue(request.getHeader("Authorization") == null);
    }

    @Test
    public void basicAuthenticationWithUsernameAndPassword() throws Throwable {
        server.enqueue(new MockResponse().setBody("").setResponseCode(200));
        server.play(8080);

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        // Set a username and password
        operation.setBasicAuthenticationHeader("droid", "networking");

        NetworkEngine.getInstance().executeOperation(operation);

        // We did supply a username and password. 200 OK
        assertTrue(operation.getStatus() == NetworkOperation.STATUS_COMPLETED);
        assertTrue(operation.getHttpStatusCode() == 200);
        assertTrue(operation.getResponseString().equals(""));

        // Verify the authorizaion header
        RecordedRequest request = server.takeRequest();
        assertTrue(request.getHeader("Authorization").equals("Basic ZHJvaWQ6bmV0d29ya2luZw=="));
    }

    @Test
    public void basicAuthenticationWithIncorrectUsernameAndPassword() throws Throwable {
        server.enqueue(new MockResponse().setBody("HTTP Basic: Access denied.").setResponseCode(401));
        server.play(8080);

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        // Set a username and password
        operation.setBasicAuthenticationHeader("wrong", "password");

        NetworkEngine.getInstance().executeOperation(operation);

        // We did supply a username and password, but the wrong ones. 401 Unauthorized
        assertTrue(operation.getStatus() == NetworkOperation.STATUS_ERROR);
        assertTrue(operation.getHttpStatusCode() == 401);
        assertTrue(operation.getResponseString().equals("HTTP Basic: Access denied."));

        // Verify the authorizaion header
        RecordedRequest request = server.takeRequest();
        assertTrue(request.getHeader("Authorization").equals("Basic d3Jvbmc6cGFzc3dvcmQ="));
    }
}
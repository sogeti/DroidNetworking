package com.sogeti.droidnetworking.test.integration;

import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;
import com.sogeti.droidnetworking.NetworkEngine;
import com.sogeti.droidnetworking.NetworkOperation;

import org.junit.Test;

import java.lang.Throwable;
import java.util.HashMap;

import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.RobolectricBackgroundExecutorService;

import android.app.Activity;

@RunWith(RobolectricTestRunner.class)
public class BasicHttpTests {
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
    public void getWithStatus404() throws Throwable {
        server.enqueue(new MockResponse().setBody("The page you were looking for doesn't exist.").setResponseCode(404));
        server.play(8080);

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_PENDING);

        NetworkEngine.getInstance().executeOperation(operation);

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_ERROR);
        assertTrue(operation.getHttpStatusCode() == 404);
        assertTrue(operation.getResponseString().equals("The page you were looking for doesn't exist."));

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getRequestLine().equals("GET / HTTP/1.1"));
    }

    @Test
    public void get() throws Throwable {
        server.enqueue(new MockResponse().setBody("OK").setResponseCode(200));
        server.play(8080);

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_PENDING);

        NetworkEngine.getInstance().executeOperation(operation);

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_COMPLETED);
        assertTrue(operation.getHttpStatusCode() == 200);
        assertTrue(operation.getResponseString().equals("OK"));

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getRequestLine().equals("GET / HTTP/1.1"));
    }

    @Test
    public void getWithParameters() throws Throwable {
        server.enqueue(new MockResponse().setBody("OK").setResponseCode(200));
        server.play(8080);

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        Map<String, String> params = new HashMap<String,String>();
        params.put("hello", "world");
        operation.addParams(params);

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_PENDING);

        NetworkEngine.getInstance().executeOperation(operation);

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_COMPLETED);
        assertTrue(operation.getHttpStatusCode() == 200);
        assertTrue(operation.getResponseString().equals("OK"));

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getRequestLine().equals("GET /?hello=world HTTP/1.1"));
    }


    @Test
    public void post() throws Throwable {
        server.enqueue(new MockResponse().setBody("").setResponseCode(201));
        server.play(8080);

        Map<String, String> params = new HashMap<String,String>();
        params.put("hello", "world");

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost", params, NetworkEngine.HttpMethod.POST);

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_PENDING);

        NetworkEngine.getInstance().executeOperation(operation);

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_COMPLETED);
        assertTrue(operation.getHttpStatusCode() == 201);
        assertTrue(operation.getResponseString().equals(""));

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getRequestLine().equals("POST / HTTP/1.1"));
        assertTrue(request.getBodySize() == 11);
        assertTrue(request.getUtf8Body().equals("hello=world"));
    }

    @Test
    public void put() throws Throwable {
        server.enqueue(new MockResponse().setBody("").setResponseCode(200));
        server.play(8080);

        Map<String, String> params = new HashMap<String,String>();
        params.put("hello", "world");

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost", params, NetworkEngine.HttpMethod.PUT);

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_PENDING);

        NetworkEngine.getInstance().executeOperation(operation);

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_COMPLETED);
        assertTrue(operation.getHttpStatusCode() == 200);
        assertTrue(operation.getResponseString().equals(""));

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getRequestLine().equals("PUT / HTTP/1.1"));
        assertTrue(request.getBodySize() == 11);
        assertTrue(request.getUtf8Body().equals("hello=world"));
    }

    @Test
    public void delete() throws Throwable {
        server.enqueue(new MockResponse().setBody("").setResponseCode(200));
        server.play(8080);

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost", null, NetworkEngine.HttpMethod.DELETE);

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_PENDING);

        NetworkEngine.getInstance().executeOperation(operation);

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_COMPLETED);
        assertTrue(operation.getHttpStatusCode() == 200);

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getRequestLine().equals("DELETE / HTTP/1.1"));
        assertTrue(request.getBodySize() == 0);
    }

    @Test
    public void head() throws Throwable {
        server.enqueue(new MockResponse().setBody("").setResponseCode(200));
        server.play(8080);

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost", null, NetworkEngine.HttpMethod.HEAD);

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_PENDING);

        NetworkEngine.getInstance().executeOperation(operation);

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_COMPLETED);
        assertTrue(operation.getHttpStatusCode() == 200);

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getRequestLine().equals("HEAD / HTTP/1.1"));
        assertTrue(request.getBodySize() == 0);
    }
}
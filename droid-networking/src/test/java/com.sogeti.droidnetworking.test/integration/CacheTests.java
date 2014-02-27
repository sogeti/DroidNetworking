package com.sogeti.droidnetworking.test.integration;

import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;
import com.sogeti.droidnetworking.NetworkEngine;
import com.sogeti.droidnetworking.NetworkOperation;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


@RunWith(RobolectricTestRunner.class)
public abstract class CacheTests {
    protected MockWebServer server;

    @After
    public void tearDown() throws Throwable {
        server.shutdown();
    }

    @Test
    public void cacheHeaders() throws Throwable {
        MockResponse response = new MockResponse();

        response.setBody("");
        response.setResponseCode(200);
        response.addHeader("Cache-Control", "max-age=0");
        response.addHeader("Etag", "\"831363a99b0f91672533a537c2304208\"");

        server.enqueue(response);
        server.play(8080);

        NetworkEngine.getInstance().clearCache();

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        NetworkEngine.getInstance().executeOperation(operation);

        assertTrue(operation.getHttpStatusCode() == 200);

        Map<String, String> cacheHeaders = operation.getCacheHeaders();

        assertFalse(cacheHeaders.get("Expires") == null);

        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE, dd MMM yyyyy HH:mm:ss z");
            Date expires = simpleDateFormat.parse(cacheHeaders.get("Expires"));
            assertTrue(expires.before(new Date()));
        } catch (ParseException e) {
            assertTrue(false);
        }

        // ETag is a MD5 sum of the content. If the content changes this ETag will not be valid.
        assertFalse(cacheHeaders.get("ETag") == null);
        assertTrue(cacheHeaders.get("ETag").equalsIgnoreCase("\"831363a99b0f91672533a537c2304208\""));

        assertTrue(cacheHeaders.get("Last-Modified") == null);
    }

    @Test
    public void notModified() throws Throwable {
        MockResponse response = new MockResponse();

        response.setBody("");
        response.setResponseCode(200);
        response.addHeader("Cache-Control", "max-age=0");
        response.addHeader("Etag", "\"831363a99b0f91672533a537c2304208\"");

        server.enqueue(response);

        response.setBody("");
        response.setResponseCode(304);
        response.addHeader("Cache-Control", "max-age=0");
        response.addHeader("Etag", "\"831363a99b0f91672533a537c2304208\"");

        server.enqueue(response);

        server.play(8080);

        NetworkEngine.getInstance().clearCache();

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        NetworkEngine.getInstance().executeOperation(operation);

        assertTrue(operation.getHttpStatusCode() == 200);

        // Save the cache headers from the first request
        Map<String, String> cacheHeaders = operation.getCacheHeaders();

        NetworkEngine.getInstance().clearCache();

        // Create a new request
        operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        // Update the headers with the saved cache headers (ETag)
        operation.updateOperation(cacheHeaders);

        // Execute the operation
        NetworkEngine.getInstance().executeOperation(operation);

        // The server should respond with 304 Not Modified since we supplied a ETag
        assertTrue(operation.getHttpStatusCode() == 304);

        // Verify that an Etag was not sent to server in the first request
        RecordedRequest request = server.takeRequest();
        assertTrue(request.getHeader("If-None-Match") == null);

        // Verify that an Etag was sent to server in the second request
        request = server.takeRequest();
        assertTrue(request.getHeader("If-None-Match").equals("\"831363a99b0f91672533a537c2304208\""));
    }

    @Test
    public void cacheTC1() throws Throwable {
        MockResponse response = new MockResponse();

        response.setBody("OK");
        response.setResponseCode(200);
        response.addHeader("Cache-Control", "max-age=1");
        response.addHeader("Etag", "\"831363a99b0f91672533a537c2304208\"");

        server.enqueue(response);

        server.play(8080);

        NetworkEngine.getInstance().clearCache();

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        NetworkEngine.getInstance().executeOperation(operation);

        assertTrue(operation.getHttpStatusCode() == 200);
        assertTrue(operation.isCachedResponse() == false);
        assertTrue(operation.getResponseString().equalsIgnoreCase("OK"));

        byte[] responseData = operation.getResponseData();

        // There should be some response data
        assertFalse(responseData == null);

        // Create an new operation
        operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        NetworkEngine.getInstance().executeOperation(operation);

        // Check that we get a 200 response, that is cached with the same data as before
        assertTrue(operation.getHttpStatusCode() == 200);
        assertTrue(operation.isCachedResponse() == true);
        assertTrue(operation.getResponseString().equalsIgnoreCase("OK"));
    }


    @Test
    public void cacheTC2() throws Throwable {
        MockResponse response = new MockResponse();

        response.setBody("OK");
        response.setResponseCode(200);
        response.addHeader("Cache-Control", "max-age=1");
        response.addHeader("Etag", "\"831363a99b0f91672533a537c2304208\"");

        server.enqueue(response);

        response.setBody("OK");
        response.setResponseCode(200);
        response.addHeader("Cache-Control", "max-age=1");
        response.addHeader("Etag", "\"831363a99b0f91672533a537c2304208\"");

        server.enqueue(response);

        server.play(8080);

        NetworkEngine.getInstance().clearCache();

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        NetworkEngine.getInstance().executeOperation(operation);

        assertTrue(operation.getHttpStatusCode() == 200);
        assertTrue(operation.isCachedResponse() == false);

        operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        NetworkEngine.getInstance().executeOperation(operation);

        // Check that we get a 200 response, that is cached with the same data as before
        assertTrue(operation.getHttpStatusCode() == 200);
        assertTrue(operation.isCachedResponse() == true);

        try {
            Thread.sleep(1500L);    // after 2 seconds the cache is no longer valid
        } catch (Exception e) {

        }

        // Create an new operation
        operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        NetworkEngine.getInstance().executeOperation(operation);

        // Check that we get a 200 response, that is not cached
        assertTrue(operation.getHttpStatusCode() == 200);
        assertTrue(operation.isCachedResponse() == false);
    }

    @Test
    public void cacheTC3() throws Throwable {
        MockResponse response = new MockResponse();

        response.setBody("OK");
        response.setResponseCode(200);
        response.addHeader("Cache-Control", "max-age=1");
        response.addHeader("Etag", "\"831363a99b0f91672533a537c2304208\"");

        server.enqueue(response);

        response.setBody("OK");
        response.setResponseCode(304);
        response.addHeader("Cache-Control", "max-age=1");
        response.addHeader("Etag", "\"831363a99b0f91672533a537c2304208\"");

        server.enqueue(response);

        server.play(8080);

        NetworkEngine.getInstance().clearCache();

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        NetworkEngine.getInstance().executeOperation(operation);

        assertTrue(operation.getHttpStatusCode() == 200);
        assertTrue(operation.isCachedResponse() == false);

        operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        NetworkEngine.getInstance().executeOperation(operation);

        // Check that we get a 200 response, that is cached with the same data as before
        assertTrue(operation.getHttpStatusCode() == 200);
        assertTrue(operation.isCachedResponse() == true);

        try {
            Thread.sleep(1500L);    // after 2 seconds the cache is no longer valid
        } catch (Exception e) {

        }

        // Create an new operation
        operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        NetworkEngine.getInstance().executeOperation(operation);

        // Check that we get a 200 response, that is cached since Etag hasen't changed
        assertTrue(operation.getHttpStatusCode() == 200);
        assertTrue(operation.isCachedResponse() == true);
    }

    @Test
    public void uniqueIdentifier() {
        NetworkEngine.getInstance().clearCache();

        NetworkOperation operation = NetworkEngine.getInstance()
                .createOperationWithURLString("http://localhost");

        assertFalse(operation == null);

        assertTrue(operation.getUniqueIdentifier().equalsIgnoreCase("c6c9381420cb8621b071d157b1876901"));
    }
}
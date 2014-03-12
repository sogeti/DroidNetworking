package com.sogeti.droidnetworking.test.integration;

import android.app.Activity;

import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;
import com.sogeti.droidnetworking.NetworkEngine;
import com.sogeti.droidnetworking.NetworkOperation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.RobolectricBackgroundExecutorService;

import java.lang.System;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class MultipartTests {
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
    public void postMultipartWithData() throws Throwable {
        server.enqueue(new MockResponse().setBody("").setResponseCode(201));
        server.play(8080);

        Map<String, String> params = new HashMap<String,String>();
        params.put("hello", "world");

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost", params, NetworkEngine.HttpMethod.POST);

        byte[] data = new byte[] {'A', 'B', 'C', 'D'};
        operation.addData(data, "data");

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_PENDING);

        NetworkEngine.getInstance().executeOperation(operation);

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_COMPLETED);
        assertTrue(operation.getHttpStatusCode() == 201);
        assertTrue(operation.getResponseString().equals(""));

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getRequestLine().equals("POST / HTTP/1.1"));

        String contentType = request.getHeader("Content-Type");
        String boundary = getBoundaryFromContentType(contentType);

        assertTrue(boundary.length() == 30);
        assertTrue(numberOfBoundariesInBody(boundary, request.getUtf8Body()) == 3);
    }

    private int numberOfBoundariesInBody(String boundary, String body) {
        Pattern p = Pattern.compile(boundary);
        Matcher m = p.matcher(body);

        int count = 0;

        while (m.find()) {
            count +=1;
        }

        return count;
    }

    private String getBoundaryFromContentType(String contentType) {
        String pattern = "boundary=(.{30})";

        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(contentType);

        assertTrue(m.groupCount() == 1);

        m.find();
        String boundary =  m.group().substring(9, 39);

        return boundary;
    }

    @Test
    public void postWithRawBody() throws Throwable {
        server.enqueue(new MockResponse().setBody("").setResponseCode(201));
        server.play(8080);

        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://localhost");

        operation.setHttpMethod(NetworkEngine.HttpMethod.POST);
        operation.setBody(new byte[] {'A', 'B', 'C', 'D'});

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_PENDING);

        NetworkEngine.getInstance().executeOperation(operation);

        assertTrue(operation.getStatus() == NetworkOperation.STATUS_COMPLETED);
        assertTrue(operation.getHttpStatusCode() == 201);
        assertTrue(operation.getResponseString().equals(""));

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getRequestLine().equals("POST / HTTP/1.1"));
        System.out.println(request.getUtf8Body());
        assertTrue(request.getBodySize() == 4);
        assertTrue(request.getUtf8Body().equals("ABCD"));
    }
}
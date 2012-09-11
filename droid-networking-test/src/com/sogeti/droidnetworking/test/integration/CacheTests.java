package com.sogeti.droidnetworking.test.integration;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;

import com.sogeti.droidnetworking.NetworkEngine;
import com.sogeti.droidnetworking.NetworkOperation;
import com.sogeti.droidnetworking.NetworkEngine.HttpMethod;
import com.sogeti.droidnetworking.test.entities.Message;

import android.test.InstrumentationTestCase;

public class CacheTests extends InstrumentationTestCase {
    public CacheTests() {
        super();
    }
    
    @Override
    public void setUp() {
        NetworkEngine.getInstance().init(getInstrumentation().getContext());
        
        // Make sure there are no messages, otherwise the ETag won't match the testcase
        for (Message message : getMessages()) {
            deleteMessage(message.getId());
        }
    }
    
    @Override
    public void tearDown() {
        
    }
    
    public void testCacheHeaders() {
    	NetworkOperation operation = NetworkEngine.getInstance()
                .createOperationWithURLString("http://freezing-winter-7173.heroku.com/messages.json");
            
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
        assertTrue(cacheHeaders.get("ETag").equalsIgnoreCase("\"d751713988987e9331980363e24189ce\""));
        
        assertTrue(cacheHeaders.get("Last-Modified") == null);
    }
    
    public void testNotModified() {
    	NetworkOperation operation = NetworkEngine.getInstance()
                .createOperationWithURLString("http://freezing-winter-7173.heroku.com/messages.json");
            
        NetworkEngine.getInstance().executeOperation(operation);
        
        assertTrue(operation.getHttpStatusCode() == 200);
        
        // Save the cache headers from the first request
        Map<String, String> cacheHeaders = operation.getCacheHeaders();
        
        // Create a new request
        operation = NetworkEngine.getInstance()
                .createOperationWithURLString("http://freezing-winter-7173.heroku.com/messages.json");
        
        // Update the headers with the saved cache headers (ETag)
        operation.updateOperation(cacheHeaders);
        
        // Execute the operation
        NetworkEngine.getInstance().executeOperation(operation);
        
        // The server should respond with 304 Not Modified since we supplied a ETag
        assertTrue(operation.getHttpStatusCode() == 304);
    }
    
    public void testResponseData() {
        NetworkOperation operation = NetworkEngine.getInstance()
                .createOperationWithURLString("http://freezing-winter-7173.heroku.com/messages.json");
            
        NetworkEngine.getInstance().executeOperation(operation);
        
        assertTrue(operation.getHttpStatusCode() == 200);
        assertTrue(operation.isCachedResponse() == false);
        assertTrue(operation.getResponseString().equalsIgnoreCase("[]"));
        
        byte[] responseData = operation.getResponseData();
        
        // There should be some response data   
        assertFalse(responseData == null);
        
        // Create an new operation
        operation = NetworkEngine.getInstance()
                .createOperationWithURLString("http://freezing-winter-7173.heroku.com/messages.json");
        
        // Set cached data
        operation.setCachedData(responseData);
        
        NetworkEngine.getInstance().executeOperation(operation);
        
        // Check that we get a 200 response, that is cached with the same data as before
        assertTrue(operation.getHttpStatusCode() == 200);
        assertTrue(operation.isCachedResponse() == true);
        assertTrue(operation.getResponseString().equalsIgnoreCase("[]"));  
    }
    
    public void testUniqueIdentifier() {
    	NetworkOperation operation = NetworkEngine.getInstance()
                .createOperationWithURLString("http://freezing-winter-7173.heroku.com/messages.json");
            
        assertFalse(operation == null);
        
        assertTrue(operation.getUniqueIdentifier().equalsIgnoreCase("674e29d6bdfd30ea62e56ae0d1bdea88"));
    }
    
    private ArrayList<Message> getMessages() {
        NetworkOperation operation = NetworkEngine.getInstance()
            .createOperationWithURLString("http://freezing-winter-7173.heroku.com/messages.json");
        
        NetworkEngine.getInstance().executeOperation(operation);
        
        if (operation.getHttpStatusCode() != 200) {
            return null;
        }
        
        ArrayList<Message> messages = new ArrayList<Message>();
        
        String responseString = operation.getResponseString();
        
        try {
            JSONArray jsonArray = new JSONArray(responseString);
            
            for (int i = 0; i < jsonArray.length(); i++) {
                Message message = new Message(jsonArray.getJSONObject(i));
                messages.add(message);
            }
            
            return messages;
        } catch (JSONException e) {
            return messages;
        }
    }
    
    private boolean deleteMessage(int id) {
        NetworkOperation operation = NetworkEngine.getInstance()
            .createOperationWithURLString("http://freezing-winter-7173.heroku.com/messages/"+id+".json", null, HttpMethod.DELETE);
        
        NetworkEngine.getInstance().executeOperation(operation);
        
        if (operation.getHttpStatusCode() == 200) {
            return true;
        } else {
            return false;
        }
    }
}

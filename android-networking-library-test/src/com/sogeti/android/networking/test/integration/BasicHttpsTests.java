package com.sogeti.android.networking.test.integration;

import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.sogeti.android.networking.NetworkEngine;
import com.sogeti.android.networking.NetworkEngine.HttpMethod;
import com.sogeti.android.networking.NetworkOperation;
import com.sogeti.android.networking.test.entities.Message;

import android.test.InstrumentationTestCase;

public class BasicHttpsTests extends InstrumentationTestCase {
    public BasicHttpsTests() {
        super();
    }
    
    @Override
    public void setUp() {
        NetworkEngine.getInstance().init(getInstrumentation().getContext(),
                "freezing-winter-7173.heroku.com");
        NetworkEngine.getInstance().setPortNumber(443);
    }
    
    @Override
    public void tearDown() {
        for (Message message : getMessages()) {
            deleteMessage(message.getId());
        }
    }
      
    public void testPostTC1() {
        Message message = postMessage("Post 1 title", "Post 1 body");
        
        assertFalse(message == null);
            
        assertTrue(message.getTitle().equalsIgnoreCase("Post 1 title"));
        assertTrue(message.getBody().equalsIgnoreCase("Post 1 body"));
    }
    
    public void testPutTC1() {
        Message message1 = postMessage("Post 2 title", "Post 2 body");
        
        assertFalse(message1 == null);
        
        boolean succeeded = putMessage(message1.getId(), "Put 1 title", "Put 1 body");
        
        assertTrue(succeeded);
        
        Message message2 = getMessage(message1.getId());
        assertFalse(message2 == null);
        
        assertTrue(message2.getTitle().equalsIgnoreCase("Put 1 title"));
        assertTrue(message2.getBody().equalsIgnoreCase("Put 1 body")); 
    }
    
    public void testDeleteTC1() {
        Message message = postMessage("Delete 1 title", "Delete 1 title");
        
        assertFalse(message == null);
        
        boolean succeeded = deleteMessage(message.getId());
        
        assertTrue(succeeded);
    }
    
    public void testGetTC1() {
        Message message1 = postMessage("Post 3 title", "Post 3 body");
        assertFalse(message1 == null);
        Message message2 = postMessage("Post 4 title", "Post 4 body");
        assertFalse(message2 == null);
        
        ArrayList<Message> messages = getMessages();
        
        assertTrue(messages.size() >= 2);  
    }
    
    public void testGetTC2() {
        Message message1 = postMessage("Post 5 title", "Post 5 body");
        assertFalse(message1 == null);
        
        Message message2 = getMessage(message1.getId());
        assertFalse(message2 == null);
        
        assertTrue(message1.getTitle().equalsIgnoreCase(message2.getTitle()));
        assertTrue(message1.getBody().equalsIgnoreCase(message2.getBody())); 
    }
    
    private ArrayList<Message> getMessages() {
        NetworkOperation operation = NetworkEngine.getInstance()
            .createOperationWithPath("/messages.json", null, HttpMethod.GET, true);
        
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
    
    private Message getMessage(int id) {
        NetworkOperation operation = NetworkEngine.getInstance()
            .createOperationWithPath("/messages/"+id+".json", null, HttpMethod.GET, true);
        
        NetworkEngine.getInstance().executeOperation(operation);
        
        if (operation.getHttpStatusCode() != 200) {
            return null;
        }
        
        String responseString = operation.getResponseString();
        
        try {
            JSONObject jsonObject = new JSONObject(responseString);
            
            return new Message(jsonObject);
        } catch (JSONException e) {
            return null;
        }
    }
    
    private Message postMessage(String title, String body) {
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("message[title]", title);
        params.put("message[body]", body);
        
        NetworkOperation operation = NetworkEngine.getInstance().createOperationWithPath("/messages.json",
                params, HttpMethod.POST, true);
        
        NetworkEngine.getInstance().executeOperation(operation);
        
        if (operation.getHttpStatusCode() != 201) {
            return null;
        }
        
        String responseString = operation.getResponseString();
        
        try {
            JSONObject jsonObject = new JSONObject(responseString);
            
            return new Message(jsonObject);
        } catch (JSONException e) {
            return null;
        }
    }
    
    private boolean putMessage(int id, String title, String body) {
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("message[title]", title);
        params.put("message[body]", body);
        
        NetworkOperation operation = NetworkEngine.getInstance()
            .createOperationWithPath("/messages/"+id+".json", params, HttpMethod.PUT, true);
        
        NetworkEngine.getInstance().executeOperation(operation);
        
        if (operation.getHttpStatusCode() == 200) {
            return true;
        } else {
            return false;
        }
    }
    
    private boolean deleteMessage(int id) {
        NetworkOperation operation = NetworkEngine.getInstance()
            .createOperationWithPath("/messages/"+id+".json", null, HttpMethod.DELETE, true);
        
        NetworkEngine.getInstance().executeOperation(operation);
        
        if (operation.getHttpStatusCode() == 200) {
            return true;
        } else {
            return false;
        }
    }
}
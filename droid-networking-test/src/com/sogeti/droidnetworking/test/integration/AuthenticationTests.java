/**
 * Copyright 2012 Sogeti Sverige AB

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.sogeti.droidnetworking.test.integration;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;

import com.sogeti.droidnetworking.NetworkEngine;
import com.sogeti.droidnetworking.NetworkOperation;
import com.sogeti.droidnetworking.NetworkEngine.HttpMethod;
import com.sogeti.droidnetworking.test.entities.Message;

import android.test.InstrumentationTestCase;

public class AuthenticationTests extends InstrumentationTestCase {
    public AuthenticationTests() {
        super();
    }
    
    @Override
    public void setUp() {
        NetworkEngine.getInstance().init(getInstrumentation().getContext());
    }
    
    @Override
    public void tearDown() {
        for (Message message : getMessages()) {
            deleteMessage(message.getId());
        }
    }
    
    public void testBasicAuthenticationWithNoUsernameOrPassword() {
        NetworkOperation operation = NetworkEngine.getInstance()
                .createOperationWithURLString("http://freezing-winter-7173.heroku.com/secrets.json");
        
        NetworkEngine.getInstance().executeOperation(operation);
        
        // We didn't supply a username and password. 401 Unauthorized
        assertTrue(operation.getHttpStatusCode() == 401);
    }
    
    public void testBasicAuthenticationWithUsernameAndPassword() {
        NetworkOperation operation = NetworkEngine.getInstance()
                .createOperationWithURLString("http://freezing-winter-7173.heroku.com/secrets.json");
        
        // Set a username and password
        operation.setBasicAuthenticationHeader("droid", "networking");
        
        NetworkEngine.getInstance().executeOperation(operation);
        
        // We did supply a username and password. 200 OK
        assertTrue(operation.getHttpStatusCode() == 200);
    }
    
    public void testBasicAuthenticationWithIncorrectUsernameAndPassword() {
        NetworkOperation operation = NetworkEngine.getInstance()
                .createOperationWithURLString("http://freezing-winter-7173.heroku.com/secrets.json");
        
        // Set a username and password
        operation.setBasicAuthenticationHeader("wrong", "password");
        
        NetworkEngine.getInstance().executeOperation(operation);
        
        // We did supply a username and password, but the wrong ones. 401 Unauthorized
        assertTrue(operation.getHttpStatusCode() == 401);
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

package com.sogeti.android.networking.test.entities;

import org.json.JSONException;
import org.json.JSONObject;

public class Message {
    private int id;
    private String title;
    private String body;
    
    public Message(JSONObject jsonObject) throws JSONException {
        this.id = jsonObject.getInt("id");
        this.title = jsonObject.getString("title");
        this.body = jsonObject.getString("body");
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}

package com.murrayc.galaxyzoo.app;

/**
 * Created by tennentb on 7/13/2015.
 */
import java.sql.Timestamp;

public class Message {

    private int messageID;
    private int userID;
    private String message;
    private Timestamp sentTS;
    private String name;

    public int getMessageID() {
        return messageID;
    }
    public void setMessageID(int messageID) {
        this.messageID = messageID;
    }
    public int getUserID() {
        return userID;
    }
    public void setUserID(int userID) {
        this.userID = userID;
    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public Timestamp getSentTS() {
        return sentTS;
    }
    public void setSentTS(Timestamp sentTS) {
        this.sentTS = sentTS;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

}

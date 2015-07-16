package com.murrayc.galaxyzoo.app;

/**
 * Created by tennentb on 7/13/2015.
 */
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.NetworkOnMainThreadException;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class MessageActivity extends Activity {

    private ListView lvMessages;
    private List<Message> messages;
    private EditText input;
    private Button sendMessage;
    private int job_id;


    @SuppressLint("ResourceAsColor")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        Intent i = getIntent();
        //job_id = i.getIntExtra("job_id", -1);

        messages = new ArrayList<Message>();

        lvMessages = (ListView) findViewById(R.id.lvMessages);

        input = (EditText)findViewById(R.id.etMessage);
        sendMessage = (Button)findViewById(R.id.btnSend);

        getWorker gw = new getWorker();
        Message message = new Message();
        message.getUserID();
        gw.setMsg(message);
        try {
            gw.execute().get();
            messages = gw.getMessages();
            addMessagesToList();
        } catch (InterruptedException e1) {
        } catch (ExecutionException e1) {
        }
        sendMessage.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {

                String inputText = input.getText().toString().trim();
                input.setText("");
                //lvMessages.setId(R.id.lv_next_message);
                //lvMessages.setBackgroundColor(R.color.Message_background);
	            /*
	            lvMessages.setTextFilterEnabled(true);
	            lvMessages.setFilterText(inputText);
	            */
                Worker w = new Worker();
                getWorker gw2 = new getWorker();
                Message message = new Message();
                message.setMessage(inputText);
                message.getUserID();
                gw2.setMsg(message);
                w.setMsg(message);
                try {
                    w.execute().get();
                    for(int i=0;i<messages.size();i++){
                        messages.get(i).getMessage();
                        messages.get(i).getMessageID();
                        messages.get(i).getName();
                    }
                } catch (InterruptedException e) {
                } catch (ExecutionException e) {
                }
                try {
                    gw2.execute().get();
                    messages = gw2.getMessages();
                    addMessagesToList();
                } catch (InterruptedException e1) {
                } catch (ExecutionException e1) {
                }
            }
        });

    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
		/*if (id == R.id.action_settings) {
			return true;
		}
		else*/ if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("SimpleDateFormat")
    public class getWorker extends AsyncTask<String, Integer, Boolean> {

        private Message msg;
        private List<Message> messages;
        String jsonStuff;
        String th_time;
        String th_date;
        String TIMEDATA;
        @Override
        protected Boolean doInBackground(String... params) {
            messages = new ArrayList<Message>();
            getMessage();
            return true;
        }
        private void getMessage() {
            SharedPreferences sp = getSharedPreferences(getResources().getText(R.string.app_pkg_name).toString(), Context.MODE_PRIVATE);
            HttpClient httpclient1 = new DefaultHttpClient();
            HttpGet httpget = new HttpGet("http://tmpr.jahkeup.com/api/jobs/" + msg.getUserID() + "/messages");
            httpget.setHeader("Auth-Token", sp.getString("auth_token", null));
            try {
                HttpResponse response = httpclient1.execute(httpget);
                jsonStuff = EntityUtils.toString(response.getEntity());
                Log.v("GETDATA", jsonStuff);
                parseMessageData();
            } catch (ClientProtocolException e) {
                Log.e("check", "ClientProtocolException");
            } catch (IOException e) {
                Log.e("check", "IOException");
            } catch (NetworkOnMainThreadException e) {
                Log.e("check", "NetworkOnMainThreadException");
            }


        }
        public List<Message> getMessages() {
            return messages;
        }
        public void setMessages(List<Message> messages) {
            this.messages = messages;
        }
        @SuppressLint({ "SimpleDateFormat", "DefaultLocale" })
        private void parseMessageData() {

            try {
                JSONObject jsonResponse = new JSONObject(jsonStuff);
                JSONArray jsonData = jsonResponse.getJSONArray("data");


                for (int i = 0; i < jsonData.length(); i++) {
                    Message m = new Message();

                    Pattern time_match = Pattern.compile("[0-9]{1,}:[0-9][0-9]:[0-9][0-9]");
                    Pattern date_match = Pattern.compile("[0-9]{4}-[0-9][0-9]-[0-9]{1,}");
                    Matcher matcher = time_match.matcher(jsonData.getJSONObject(i).getString("created_at"));
                    Matcher matcher2 = date_match.matcher(jsonData.getJSONObject(i).getString("created_at"));
                    //Log.v("CREATED_AT",jsonData.getJSONObject(i).getString("created_at"));
                    //2015-03-26
                    if (matcher.find()) {
                        th_time = matcher.group(0);
                    }
                    if (matcher2.find()) {
                        th_date = matcher2.group(0);
                    }


                    SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss");
                    java.util.Date d = sdf.parse(th_time);
                    SimpleDateFormat th_format = new SimpleDateFormat("h:mma");
                    TIMEDATA = th_format.format(d).toLowerCase();
                    //Log.v("TIMEDATA", TIMEDATA);

                    //m.setJobID(jsonData.getJSONObject(i).getInt("job_id"));
                    m.setMessageID(jsonData.getJSONObject(i).getInt("id"));
                    m.setMessage(jsonData.getJSONObject(i).getString("message"));
                    m.setName(jsonData.getJSONObject(i).getString("firstname") + " " + jsonData.getJSONObject(i).getString("lastname") + " " + TIMEDATA  + " " + th_date);
                    this.messages.add(m);
                }

            } catch (JSONException e) {
            } catch (java.text.ParseException e) {

            }
        }
        public void setMsg(Message m) {
            msg = m;
        }
    }





    public class Worker extends AsyncTask<String, Integer, Boolean> {

        private Message msg;
        private List<Message> messages;
        String jsonStuff;
        @Override
        protected Boolean doInBackground(String... params) {
            setMessages(new ArrayList<Message>());
            postMessage();
            return true;
        }

        private void postMessage() {
            SharedPreferences sp = getSharedPreferences(getResources().getText(R.string.app_name).toString(), Context.MODE_PRIVATE);
            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost("http://tmpr.jahkeup.com/api/jobs/" + msg.getUserID() + "/messages");
            httppost.addHeader("Content-Type", "application/json");
            httppost.addHeader("Auth-Token", sp.getString("auth_token", null));

            try {
                JSONObject jo = new JSONObject();
                jo.put("message", msg.getMessage());

                StringEntity input = new StringEntity(jo.toString());
                httppost.setEntity(input);
                HttpResponse response = httpclient.execute(httppost);
                HttpEntity entity = response.getEntity();
                String jsonString = EntityUtils.toString(entity, "UTF-8");
                Log.v("response", jsonString);

            } catch (UnsupportedEncodingException e) {
                Log.v("EXCEPTION", "UnsupportedEncodingException");
            } catch (ClientProtocolException e) {
                Log.v("EXCEPTION", "ClientProtocolException");
            } catch (IOException e) {
                Log.v("EXCEPTION", "IOException");
            } catch (JSONException e) {
                Log.v("EXCEPTION", "JSONEXCEPTION");
            }

        }

        public void setMsg(Message m) {
            msg = m;
        }

        public List<Message> getMessages() {
            return messages;
        }

        public void setMessages(List<Message> messages) {
            this.messages = messages;
        }
    }
    private void addMessagesToList() {
        List<Map<String, String>> data = new ArrayList<Map<String, String>>(messages.size());
        Message temp = new Message();
        for (int i = 0; i < messages.size(); i++) {
            Map<String, String> msgItem = new HashMap<String, String>();
            temp = messages.get(i);
            msgItem.put("message", temp.getMessage());
            msgItem.put("name_time", temp.getName());
            data.add(msgItem);
        }

        SimpleAdapter adapter = new SimpleAdapter(MessageActivity.this, data, android.R.layout.simple_list_item_2, new String[] {"message", "name_time"}, new int[] {android.R.id.text1, android.R.id.text2});
        lvMessages.setAdapter(adapter);
        lvMessages.setSelection(adapter.getCount() - 1);
        lvMessages.setBackgroundResource(R.drawable.message_shape);
    }
}


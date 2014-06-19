package com.lheidosms.app;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

public class LheidoSMSService extends Service {
    private static final String SERVICE_TAG = "LHEIDOSMS SERVICE LOG";
    SmsReceiver smsReceiver;
    private ArrayList<LheidoContact> conversations = new ArrayList<LheidoContact>();
    protected Context context;

    @Override
    public void onCreate(){
        Log.v(SERVICE_TAG, "=====> Service start! <=====");
        context = getApplicationContext();
        // load conversations
        Global.conversationsList.clear();
//        getConversationsList(); // with asyncTask
        getConversationsList2(); // without asyncTask
        // init receiver
        smsReceiver = new SmsReceiver(){
            @Override
            public void customReceivedSMS() {
                Toast.makeText(context, "Sms reçu de " + new_name, Toast.LENGTH_LONG).show();
                if(activ_notif){
                    Intent notificationIntent = new Intent(context, MainLheidoSMS.class);
                    PendingIntent pIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
                    showNotification(body, new_name, phone, pIntent);
                }
                playNotificationSound();
                if(vibrate) v.vibrate(1000);
                // get contact position in conversationList
                int i = 0;
                int size = Global.conversationsList.size();
                while(i < size && !PhoneNumberUtils.compare(Global.conversationsList.get(i).getPhone(), phone)) {i++;}
                if(i < size && PhoneNumberUtils.compare(Global.conversationsList.get(i).getPhone(), phone)) {
                    // retrieved position in conversationsList
                    Global.conversationsList.get(i).Nb_sms_Plus();
                    Global.conversationsList.get(i).markNewMessage(true);
                    int index = Global.conversationsList.indexOf(Global.conversationsList.get(i));
                    LheidoContact c = Global.conversationsList.remove(index);
                    Global.conversationsList.add(0, c);
                } else{
                    // not in conversationsList
                }
                sendReceiveNewMessage();
            }

            @Override
            public void customReceivedMMS() {
                if(activ_notif){
                    Intent notificationIntent = new Intent(context, MainLheidoSMS.class);
                    PendingIntent pIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
                    showNotification("MMS", new_name, phone, pIntent);
                }
            }

            @Override
            public void customNewMessageRead(int position, String phone) {
                Log.v("LHEIDO SMS LOG", "position = "+position+", phone = "+phone);
                cancelNotif(phone);
                Global.conversationsList.get(position).markNewMessage(false);
                Intent i = new Intent(LheidoUtils.ACTION_NOTIFY_DATA_CHANGED);
                context.sendBroadcast(i);
            }

            @Override
            public void customDelivered(long id) {
                Toast.makeText(context, "Message remis" , Toast.LENGTH_SHORT).show();
                if(userPref.getBoolean("delivered_vibration", true)){
                    long[] pattern = {
                            0, // Start immediately
                            100,100,100,100,100,100,100
                    };
                    v.vibrate(pattern, -1);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(LheidoUtils.ACTION_RECEIVE_SMS);
        filter.addAction(LheidoUtils.ACTION_RECEIVE_MMS);
        filter.addAction(LheidoUtils.ACTION_SENT_SMS);
        filter.addAction(LheidoUtils.ACTION_DELIVERED_SMS);
        filter.addAction(LheidoUtils.ACTION_NEW_MESSAGE_READ);
        filter.setPriority(2000);
        getApplication().registerReceiver(smsReceiver, filter);
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.v(SERVICE_TAG, "=====> onStartCommand <=====");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(SERVICE_TAG, "=====> onBind <=====");
        return null;
    }

    @Override
    public void onDestroy(){
        Log.v(SERVICE_TAG, "=====> Service done! <=====");
        getApplication().unregisterReceiver(smsReceiver);
        super.onDestroy();
    }

    public void getConversationsList(){
        ConversationsListTask c = new ConversationsListTask();
        c.execTask();
    }

    public void getConversationsList2(){
        final String[] projection = new String[] {"_id", "date", "message_count", "recipient_ids", "read", "type"};
        Uri uri = Uri.parse("content://mms-sms/conversations?simple=true");
        Cursor query = context.getContentResolver().query(uri, projection, null, null, "date DESC");
        if(query != null) {
            if (query.moveToFirst()) {
                do {
                    Global.conversationsList.add(LheidoUtils.getLConversationInfo(context, query));
                    if(Global.conversationsList.size() == 1)
                        sendFrist();
                } while (query.moveToNext());
            }
            query.close();
        }
    }

    public void sendFrist(){
        Intent i = new Intent(LheidoUtils.ACTION_FIRST);
        sendBroadcast(i);
    }

    public void sendReceiveNewMessage(){
        Intent i = new Intent(LheidoUtils.ACTION_NEW_MESSAGE);
        sendBroadcast(i);
    }

    private final class ConversationsListTask extends AsyncTask<Void, LheidoContact, Boolean>{

        @Override
        protected Boolean doInBackground(Void... voids) {
            final String[] projection = new String[] {"_id", "date", "message_count", "recipient_ids", "read", "type"};
            Uri uri = Uri.parse("content://mms-sms/conversations?simple=true");
            Cursor query = context.getContentResolver().query(uri, projection, null, null, "date DESC");
            if(query != null) {
                if (query.moveToFirst()) {
                    do {
                        publishProgress(LheidoUtils.getLConversationInfo(context, query));
                    } while (query.moveToNext());
                }
                query.close();
            }
            return true;
        }

        @Override
        protected void onProgressUpdate (LheidoContact... prog){
            Global.conversationsList.add(prog[0]);
            if(Global.conversationsList.size() == 1)
                sendFrist();
        }

        public void execTask(){
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ) {
                executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                execute();
            }
        }
    }

}
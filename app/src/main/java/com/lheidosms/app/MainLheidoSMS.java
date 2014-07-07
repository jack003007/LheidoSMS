package com.lheidosms.app;

import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.text.InputType;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.ArrayList;

public class MainLheidoSMS extends ActionBarActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private String mTitle;

    private ArrayList<Fragment> pages;
    private ViewPagerAdapter mViewPagerAdapter;
    private ViewPager mViewPager;
    private EditText sms_body;
    private String mem_body;
    private LheidoUtils.UserPref userPref;
    private int currentConversation;
    private int currentPage;
    private static final int PAGE_SMS = 0;
    private static final int PAGE_MMS = 1;
    private ImageButton send_button;
    private int PICK_IMAGE = 1;
    protected String mmsImgPath = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_lheido_sms);

        startService(new Intent(getApplicationContext(), LheidoSMSService.class));
        userPref = new LheidoUtils.UserPref();
        userPref.setUserPref(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));

        mmsImgPath = null;

        pages = new ArrayList<Fragment>();
//        Log.v("LheidoSMS LOG", "onCreate() pages = "+pages);
        mViewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager(), pages);
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mViewPagerAdapter);
        mViewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            @Override
            public void onPageSelected(int position) {
//                Log.v("LheidoSMS LOG", "======> onPageSelected           <====== "+position);
                currentPage = position;
                if(send_button != null){
                    if(currentPage == PAGE_SMS)
                        send_button.setImageResource(R.drawable.send_sms);
                    else {
                        if(mmsImgPath == null)
                            send_button.setImageResource(R.drawable.send_mms);
                        else
                            send_button.setImageResource(R.drawable.send_sms);
                    }
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {}
        });

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle().toString();
        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));
        init_sms_body();
        init_send_button();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
    }

    public void setCurrentConversation(){
        String phone = ((SMSFragment)pages.get(PAGE_SMS)).getPhoneContact();
        int i = 0;
        int size = Global.conversationsList.size();
        while(i < size && !PhoneNumberUtils.compare(Global.conversationsList.get(i).getPhone(), phone)) {i++;}
        if(i < size && PhoneNumberUtils.compare(Global.conversationsList.get(i).getPhone(), phone)) {
            currentConversation = i;
        }
    }

    @Override
    public void onNavigationDrawerItemSelected(int position, LheidoContact contact) {
        currentConversation = position;
        currentPage = 0;
        MMSFragment mmsFrag = MMSFragment.newInstance(position, contact);
        SMSFragment smsFrag = SMSFragment.newInstance(position, contact);
        pages.clear();
        pages.add(smsFrag);
        pages.add(mmsFrag);
        mViewPagerAdapter.notifyDataSetChanged();
    }

    public void init_sms_body(){
        sms_body = (EditText) findViewById(R.id.send_body);
//        sms_body.setOnFocusChangeListener(new View.OnFocusChangeListener() {
//            @Override
//            public void onFocusChange(View v, boolean hasFocus) {
//                if(hasFocus){
//                    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
//                        liste.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
//                }else {
//                    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
//                        liste.setTranscriptMode(ListView.TRANSCRIPT_MODE_DISABLED);
//                    InputMethodManager inputManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
//                    inputManager.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
//                }
//            }
//        });
//        liste.setOnItemClickListener(new ListView.OnItemClickListener(){
//            @Override
//            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
////                sms_body.clearFocus();
//                liste.requestFocus();
//            }
//        });
        if(mem_body != null) sms_body.setText(mem_body);
        sms_body.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        if(!userPref.first_upper)
            sms_body.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        sms_body.setSingleLine(false);
    }

    public void init_send_button(){
        send_button = (ImageButton) findViewById(R.id.send_button);
        final ProgressBar noSendBar = (ProgressBar) findViewById(R.id.no_send_bar);
        noSendBar.setVisibility(ProgressBar.GONE);
        noSendBar.setMax(100);
        send_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(currentPage == PAGE_SMS) {
                    if (userPref.hide_keyboard) {
                        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        inputManager.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                    }
                    String body;
                    if (sms_body.getText() != null) {
                        body = sms_body.getText().toString();
                    } else
                        body = "";
                    if (body.length() > 0) {
                        Message new_sms = new Message();
                        new_sms.setBody(body);
                        new_sms.setSender(LheidoUtils.getUserPhone(getApplicationContext()));
                        new_sms.setRead(false);
                        Time now = new Time();
                        now.setToNow();
                        new_sms.setDate(now);
                        long thread_id = Long.parseLong(Global.conversationsList.get(currentConversation).getConversationId());
                        String phoneContact = Global.conversationsList.get(currentConversation).getPhone();
                        long new_id = LheidoUtils.store_sms(getApplicationContext(), new_sms, thread_id);
                        ((SMSFragment) pages.get(PAGE_SMS)).userAddSms(new_id, body, new_sms.getSender(), 32, now, 0);
                        sms_body.setText(R.string.empty_sms);
                        SmsManager manager = SmsManager.getDefault();
                        ArrayList<String> bodyPart = manager.divideMessage(body);
                        if (bodyPart.size() > 1) {
                            ArrayList<PendingIntent> piSent = new ArrayList<PendingIntent>();
                            ArrayList<PendingIntent> piDelivered = new ArrayList<PendingIntent>();
                            for (String aBodyPart : bodyPart) {
                                Intent ideli = new Intent(LheidoUtils.ACTION_DELIVERED_SMS);
                                ideli.putExtra(LheidoUtils.ARG_SMS_DELIVERED, new_id);
                                piSent.add(PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(LheidoUtils.ACTION_SENT_SMS), 0));
                                piDelivered.add(PendingIntent.getBroadcast(getApplicationContext(), 0, ideli, PendingIntent.FLAG_UPDATE_CURRENT));
                            }
                            manager.sendMultipartTextMessage(phoneContact, null, bodyPart, piSent, piDelivered);
                        } else {
                            Intent ideli = new Intent(LheidoUtils.ACTION_DELIVERED_SMS);
                            ideli.putExtra(LheidoUtils.ARG_SMS_DELIVERED, new_id);
                            PendingIntent piSent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(LheidoUtils.ACTION_SENT_SMS), 0);
                            PendingIntent piDelivered = PendingIntent.getBroadcast(getApplicationContext(), 0, ideli, PendingIntent.FLAG_UPDATE_CURRENT);
                            manager.sendTextMessage(phoneContact, null, body, piSent, piDelivered);
                        }
                        sms_body.clearFocus();
                        LheidoUtils.Send.userNewMessage(getApplicationContext(),phoneContact);
                    } else {
                        Toast.makeText(getApplicationContext(), R.string.empty_message, Toast.LENGTH_LONG).show();
                    }
                } else{// before send, retrieve mms bitmap
                    if(mmsImgPath == null) {
                        Intent intent = new Intent();
                        intent.setType("image/*");
                        intent.setAction(Intent.ACTION_GET_CONTENT);
                        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE);
                    }else{
                        //send mms
                    }
                }
            }
        });

        send_button.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(getApplicationContext(),
                        ""+currentConversation+"\n"+
                                Global.conversationsList.get(currentConversation).getPhone()+"\n"+
                                Global.conversationsList.get(currentConversation).getName()+"\n"+
                                "page "+((currentPage==PAGE_SMS)?"sms":"mms"),
                        Toast.LENGTH_LONG).show();
                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = { MediaStore.Images.Media.DATA };

            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            mmsImgPath = cursor.getString(columnIndex);
            cursor.close();
        }


    }

    public void onSectionAttached(String name) {
        mTitle = name;
        setActionBarTitle(mTitle);
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }

    public void setActionBarTitle(String name){
        getSupportActionBar().setTitle(name);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if(id == R.id.action_call){
            Intent call = new Intent(Intent.ACTION_CALL);
            SMSFragment frag = (SMSFragment)getSupportFragmentManager().findFragmentById(R.id.container);
            call.setData(Uri.parse("tel:" + frag.phoneContact));
            startActivity(call);
            return true;
        } else if(id == R.id.action_voir_contact){
            Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Global.conversationsList.get(currentConversation).getId());
            Intent look = new Intent(Intent.ACTION_VIEW, contactUri);
            look.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(look);
            return true;
        } else if(id == R.id.action_new_sms){
            Toast.makeText(this, "TODO :P", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_settings) {
            Intent intent;
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ) {
                intent = new Intent(this, LheidoSMSPreference.class);
            } else {
                intent = new Intent(this, LheidoSMSPreferenceOldApi.class);
            }
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
            return true;
        } else if(id == R.id.action_delete_old){
            Intent i = new Intent(getApplicationContext(), DeleteOldSMSService.class);
            startService(i);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume(){
        super.onResume();
        userPref.setUserPref(PreferenceManager.getDefaultSharedPreferences(this));
        if(sms_body != null) {
            sms_body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            if (!userPref.first_upper)
                sms_body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            sms_body.setSingleLine(false);
            sms_body.setText(mem_body);
        }
    }

    @Override
    public void onPause(){
        if(sms_body != null){
            if(sms_body.getText() != null)
                mem_body = sms_body.getText().toString();
            else mem_body = "";
        }
        super.onPause();
    }

}

package com.example.automatedcalls;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import java.lang.Override;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import java.util.logging.LogManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Handler;
import android.os.IBinder;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSION = 1;
    private static final int REQUEST_CALL_STATUS = 1;
    private EditText mEditTextNumber;
    private ImageButton call;
    private final int DEFAULT_AUTOMATION_ITERATIONS = 100;
    private int current_iteration = DEFAULT_AUTOMATION_ITERATIONS;
    protected static final int GET_STATUS_FAIL = 0;
    protected static final int STATUS_IDLE = 1;
    protected static final int STATUS_RINGING = 2;
    protected static final int STATUS_CALLING = 3;
    protected static volatile int PHONE_STATUS = GET_STATUS_FAIL;


    class CountTimeThread implements Callable<Boolean> {
        int seconds;
        CountTimeThread(int seconds){
            this.seconds = seconds;
        }
        @Override
        public Boolean call(){
            for(int i = 0; i< seconds;i++){
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return true;
        }
    }
    class CallAutomationThread implements Callable<Boolean>{
        int seconds;
        CallAutomationThread(int seconds){
            this.seconds = seconds;
        }
        @Override
        public Boolean call(){
            startAutomation(current_iteration);
            return true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        mEditTextNumber = findViewById(R.id.edit_text_number);
        ImageView imageCall=findViewById(R.id.image_call);
        imageCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                makePhoneCall();
            }
        });
        TextView startAutomation = findViewById(R.id.automation_start);
        startAutomation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startAutomation(current_iteration);

            }
        });
    }
    public void getPhoneStatus(){
        //listen to Call status change
        TelephonyManager telephonyManager =
                (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        PhoneStateListener callStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber)
            {
                PHONE_STATUS = GET_STATUS_FAIL;
                if(state==TelephonyManager.CALL_STATE_RINGING){
                    Toast.makeText(getApplicationContext(),"Phone Is Ringing",
                            Toast.LENGTH_LONG).show();

                    PHONE_STATUS = STATUS_RINGING;

                }
                if(state==TelephonyManager.CALL_STATE_OFFHOOK){
                    Toast.makeText(getApplicationContext(),"Phone is Currently in A call",
                            Toast.LENGTH_LONG).show();

                    PHONE_STATUS = STATUS_CALLING;
                }

                if(state==TelephonyManager.CALL_STATE_IDLE){
                    Toast.makeText(getApplicationContext(),"phone is Idle",
                            Toast.LENGTH_LONG).show();
                    PHONE_STATUS = STATUS_IDLE;
//                    Toast.makeText(getApplicationContext(),""+PHONE_STATUS,
//                            Toast.LENGTH_LONG).show();
                }

            }
        };

        telephonyManager.listen(callStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        telephonyManager.listen(callStateListener, PhoneStateListener.LISTEN_NONE);
    }

    @SuppressLint("PrivateApi")
    public static boolean endCall(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            final TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager != null && ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                telecomManager.endCall();
                return true;
            }
            return false;
        }
        //use unofficial API for older Android versions, as written here: https://stackoverflow.com/a/8380418/878126

        return false;
    }

    public void startAutomation(int iterations){
        if(iterations <=0){
            return;
        }

        if(ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.READ_PHONE_STATE)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.READ_PHONE_STATE},REQUEST_PERMISSION);
        }else if(ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ANSWER_PHONE_CALLS)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ANSWER_PHONE_CALLS},REQUEST_PERMISSION);
        }
        else {
            for(int i = 0;i<iterations;i++){
                Handler phoneStatusHandler = new Handler();
                phoneStatusHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        getPhoneStatus();
                    }
                }, 1000);

                if(PHONE_STATUS == STATUS_CALLING){
                    Log.d(TAG, "phone status is calling, waiting it ends and make call");
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            endCall(getApplicationContext());
                        }
                    }, 20000);
                }
                else if(PHONE_STATUS == STATUS_RINGING){
                    Log.d(TAG, "phone status is ringing, waiting it ends and make call");
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            endCall(getApplicationContext());
                        }
                    }, 20000);
                }
                else if (PHONE_STATUS == STATUS_IDLE){

                    makePhoneCall();
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Log.i(TAG, "Current Iteration is： " + iterations);
                        }
                    }, 20000);
                }else{
                    Log.e(TAG, "Unable to get the status of current phone status");
                    return;
                }

            }

        }


    }

    private void makePhoneCall(){

        String number = mEditTextNumber.getText().toString();
        if (number.trim().length() > 0) {

            if(ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.CALL_PHONE)!= PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.CALL_PHONE},REQUEST_PERMISSION);
                makePhoneCall();
            }else{
                String dial = "tel:" + number;
                startActivity(new Intent(Intent.ACTION_CALL, Uri.parse(dial)));
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        endCall(getApplicationContext());
                    }
                }, 20000);
            }
        }
        else{
            Toast.makeText(MainActivity.this,"Enter Phone Number",Toast.LENGTH_SHORT).show();
        }
    }
//    public static boolean hasPermissions(Context context, String... permissions) {
//        if (context != null && permissions != null) {
//            for (String permission : permissions) {
//                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
//                    return false;
//                }
//            }
//        }
//        return true;
//    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
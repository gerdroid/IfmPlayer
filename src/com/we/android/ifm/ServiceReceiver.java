package com.we.android.ifm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

class MyPhoneStateListener extends PhoneStateListener {
  public void onCallStateChanged(int state,String incomingNumber){
    switch(state){
    case TelephonyManager.CALL_STATE_IDLE:
      Log.d("IFM", "IDLE");
      break;
    case TelephonyManager.CALL_STATE_OFFHOOK:
      Log.d("IFM", "OFFHOOK");
      break;
    case TelephonyManager.CALL_STATE_RINGING:
      Log.d("IFM", "RINGING");
      break;
    }
  } 
}

public class ServiceReceiver extends BroadcastReceiver {

  public void onReceive(Context context, Intent intent) {
    MyPhoneStateListener phoneListener=new MyPhoneStateListener();
    TelephonyManager telephony = (TelephonyManager) 
    context.getSystemService(Context.TELEPHONY_SERVICE);
    telephony.listen(phoneListener,PhoneStateListener.LISTEN_CALL_STATE);
  }
}
package com.blogspot.developersu.ns_usbloader.Model;

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

public class NsResultReciever extends ResultReceiver {

    public interface Receiver{
        void onRecieveResults(int code, Bundle bundle);
    }

    private Receiver mReceiver;

    public NsResultReciever(Handler handler) {
        super(handler);
    }

    public void setReceiver(Receiver receiver){
        this.mReceiver = receiver;
    }

    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
        if (mReceiver != null)
            mReceiver.onRecieveResults(resultCode, resultData);
    }
}

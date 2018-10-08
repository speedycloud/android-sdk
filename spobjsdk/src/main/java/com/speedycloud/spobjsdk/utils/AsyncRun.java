package com.speedycloud.spobjsdk.utils;

import android.os.Handler;
import android.os.Looper;

/**
 * Created by gyb on 2018/4/3.
 */

public final class AsyncRun {
    public static void runInMain(Runnable r) {
        Handler h = new Handler(Looper.getMainLooper());
        h.post(r);
    }

    public static void runInBack(Runnable r) {

    }
}

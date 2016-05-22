package com.zmy.javacvdemo.utils;


import android.os.Handler;
import android.os.Looper;

public class ThreadUtil
{
    public static void runOnUiThread(Runnable runnable)
    {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(runnable);
    }
}

package com.zmy.javacvdemo;

import android.app.Application;

/**
 * Created by zmy on 2016/5/22 0022.
 */
public class TheApplication extends Application
{
    public static Application currentInstance;

    @Override
    public void onCreate()
    {
        super.onCreate();
        currentInstance = this;
    }
}

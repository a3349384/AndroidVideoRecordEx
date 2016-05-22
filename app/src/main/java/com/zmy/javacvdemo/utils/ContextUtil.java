package com.zmy.javacvdemo.utils;


import android.content.Context;

import com.zmy.javacvdemo.TheApplication;

public class ContextUtil
{
    public static Context getAppContext()
    {
        return TheApplication.currentInstance;
    }
}

package com.zmy.javacvdemo.utils;

import android.widget.Toast;

public class ToastUtil
{
    public static void showShort(String s)
    {
        Toast.makeText(ContextUtil.getAppContext(),s,Toast.LENGTH_SHORT).show();
    }
}

package com.zmy.javacvdemo.utils;

import android.os.Environment;

public class FileUtil
{
    public static boolean isSDCardMounted()
    {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }
}

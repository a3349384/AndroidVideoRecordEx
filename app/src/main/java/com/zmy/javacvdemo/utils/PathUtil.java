package com.zmy.javacvdemo.utils;

import java.io.File;

public class PathUtil
{
    public static String getRecordPath()
    {
        String path = ContextUtil.getAppContext().getExternalFilesDir(null).getAbsolutePath() + "/media/video";
        File folder = new File(path);
        if (!folder.exists())
        {
            folder.mkdirs();
        }
        return path;
    }

    public static String getPhotoPath()
    {
        String path = ContextUtil.getAppContext().getExternalFilesDir(null).getAbsolutePath()  + "/media/photo";
        File folder = new File(path);
        if (!folder.exists())
        {
            folder.mkdirs();
        }
        return path;
    }
}

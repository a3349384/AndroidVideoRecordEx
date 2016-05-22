package com.zmy.javacvdemo.utils;

import android.util.DisplayMetrics;
import android.util.TypedValue;


public class ScreenUtil
{
    public static int getScreenWidth()
    {
        DisplayMetrics dm = ContextUtil.getAppContext().getResources().getDisplayMetrics();
        return dm.widthPixels;
    }

    public static int getScreenHeight()
    {
        DisplayMetrics dm = ContextUtil.getAppContext().getResources().getDisplayMetrics();
        return dm.heightPixels;
    }

    public static int dip2Px(float dp)
    {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, ContextUtil.getAppContext().getResources().getDisplayMetrics());
    }
}

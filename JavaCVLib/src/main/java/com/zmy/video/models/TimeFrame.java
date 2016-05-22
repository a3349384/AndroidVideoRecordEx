package com.zmy.video.models;

import org.bytedeco.javacv.Frame;

/**
 * Created by zmy on 2016/5/13 0013.
 */
public class TimeFrame extends Frame
{
    public long keyTime;

    public TimeFrame()
    {
    }

    public TimeFrame(int width, int height, int depth, int channels, long keyTime)
    {
        super(width, height, depth, channels);
        this.keyTime = keyTime;
    }
}

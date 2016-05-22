package com.zmy.javacvdemo.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ProgressBar;
import com.zmy.javacvdemo.utils.ThreadUtil;

import java.util.Timer;
import java.util.TimerTask;

public class VideoRecordProgressBar extends ProgressBar
{
    private final int TIMER_PERIOD = 50;//计时器周期（毫秒）

    private Timer timer;
    private int maxSeconds;
    private int eachIncreaseProgressValue;//每次需要增加的进度值

    public VideoRecordProgressBar(Context context)
    {
        this(context, null);
    }

    public VideoRecordProgressBar(Context context, AttributeSet attrs)
    {
        this(context, attrs, 0);
    }

    public VideoRecordProgressBar(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }

    public void setMaxSeconds(int seconds)
    {
        this.maxSeconds = seconds;
        eachIncreaseProgressValue = this.getMax() / (this.maxSeconds * 1000 / TIMER_PERIOD);
    }

    public void start()
    {
        this.setProgress(0);
        this.setVisibility(VISIBLE);

        timer = new Timer(false);
        timer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                setProgress(getProgress() + eachIncreaseProgressValue);
                if (getProgress() >= getMax())
                {
                    stop();
                }
            }
        }, 0, TIMER_PERIOD);
    }

    public void stop()
    {
        if (timer != null)
        {
            timer.cancel();
            timer.purge();
            timer = null;

        }
        ThreadUtil.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                setVisibility(GONE);
                setProgress(0);
            }
        });
    }
}
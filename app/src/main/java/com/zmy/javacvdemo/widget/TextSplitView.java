package com.zmy.javacvdemo.widget;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.zmy.javacvdemo.utils.ScreenUtil;

public class TextSplitView extends RelativeLayout
{
    public static final String TAG = "TextSplitView";

    private Adapter adapter;
    private OnItemSelectedChangeListener itemSelectedChangeListener;

    private int currentPosition;

    public TextSplitView(Context context)
    {
        this(context, null);
    }

    public TextSplitView(Context context, AttributeSet attrs)
    {
        this(context, attrs, 0);
    }

    public TextSplitView(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }

    public void setAdapter(Adapter adapter)
    {
        this.adapter = adapter;
        for (int i = 0; i < this.adapter.getItemCount(); i++)
        {
            TextView textView = new TextView(getContext());
            textView.setId(i + 1);
            textView.setText(this.adapter.getItem(i));
            textView.setGravity(Gravity.CENTER);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, this.adapter.getTextSize());

            //每个item的宽度保持一致
            int width = ScreenUtil.dip2Px(50);
            LayoutParams params = new LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);

            //默认第一个Item居中
            if (i == 0)
            {
                params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                textView.setTextColor(this.adapter.getSelectedColor());
            }
            else
            {
                params.addRule(RelativeLayout.RIGHT_OF, i);
                params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
                textView.setTextColor(this.adapter.getUnSelectColor());
            }

            int margin = ScreenUtil.dip2Px(10);
            params.setMargins(0, 0, margin, 0);
            textView.setLayoutParams(params);

            textView.setOnClickListener(new ItemClickListener(i));
            addView(textView);
        }
    }

    public void setCurrentItem(final int index)
    {
        if (index == currentPosition)
        {
            return;
        }
        int from = ((MarginLayoutParams) getLayoutParams()).rightMargin;
        int to = index * ScreenUtil.dip2Px(120);

        ValueAnimator animator = ValueAnimator.ofInt(from, to).setDuration(200);
        animator.setInterpolator(new AccelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener()
        {
            @Override
            public void onAnimationUpdate(ValueAnimator animation)
            {
                int marginRight = ((Integer)animation.getAnimatedValue()).intValue();
                Log.i(TAG,"marginRight:" + marginRight);
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) TextSplitView.this.getLayoutParams();
                params.setMargins(0,0,marginRight,0);
                TextSplitView.this.setLayoutParams(params);
            }
        });
        animator.addListener(new Animator.AnimatorListener()
        {
            @Override
            public void onAnimationStart(Animator animation)
            {

            }

            @Override
            public void onAnimationEnd(Animator animation)
            {
                currentPosition = index;
                for (int i = 0; i < getChildCount(); i++)
                {
                    TextView child = (TextView) getChildAt(i);
                    if (i == currentPosition)
                    {
                        child.setTextColor(adapter.getSelectedColor());
                    }
                    else
                    {
                        child.setTextColor(adapter.getUnSelectColor());
                    }
                }

                if (itemSelectedChangeListener != null)
                {
                    itemSelectedChangeListener.change(currentPosition);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation)
            {

            }

            @Override
            public void onAnimationRepeat(Animator animation)
            {

            }
        });
        animator.start();
    }

    public void setItemSelectedChangeListener(OnItemSelectedChangeListener listener)
    {
        this.itemSelectedChangeListener = listener;
    }

    public static abstract class Adapter
    {
        public abstract int getItemCount();

        public abstract String getItem(int position);

        public abstract int getSelectedColor();

        public abstract int getUnSelectColor();

        public abstract int getTextSize();
    }

    class ItemClickListener implements View.OnClickListener
    {
        private int index;

        public ItemClickListener(int index)
        {
            this.index = index;
        }

        @Override
        public void onClick(View v)
        {
            setCurrentItem(index);
        }
    }

    public interface OnItemSelectedChangeListener
    {
        void change(int currentPosition);
    }
}

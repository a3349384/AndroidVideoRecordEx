package com.zmy.video.views;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.zmy.video.camera.CameraHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import com.lerni.video.R;

/**
 * 相机预览视图
 */
public class CameraPreviewView extends FrameLayout
{
    public static final String TAG = "CameraPreviewView";

    /**
     * 开启相机延迟
     */
    private static final long OPEN_CAMERA_DELAY = 350;

    private List<PreviewEventListener> mPreviewEventListenerList = new ArrayList<>();

    private Camera mCamera;
    private int mCameraId;
    private float viewWHRatio;
    private int yOffset;

    // 对焦动画视图
    private ImageView mFocusAnimationView;
    private Animation mFocusAnimation;

    // 真实相机预览视图
    private RealCameraPreviewView mRealCameraPreviewView;

    public CameraPreviewView(Context context)
    {
        this(context, null);
    }

    public CameraPreviewView(Context context, AttributeSet attrs)
    {
        this(context, attrs, 0);
    }

    public CameraPreviewView(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        setBackgroundColor(Color.TRANSPARENT);
        setClipChildren(false);
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.CameraPreviewView);
        Drawable focusDrawable = typedArray.getDrawable(R.styleable.CameraPreviewView_cpv_focusDrawable);
        typedArray.recycle();

//        // 添加一个占位视图，解决下面添加的对焦动画视图，若layout调整到他的上面，视图会被切掉的bug
//        addView(new View(getContext()));

        // 添加对焦动画视图
        mFocusAnimationView = new ImageView(context);
        mFocusAnimationView.setVisibility(INVISIBLE);
        if (focusDrawable == null)
        {
            mFocusAnimationView.setImageResource(R.drawable.ms_video_focus_icon);
        }
        else
        {
            mFocusAnimationView.setImageDrawable(focusDrawable);
        }
        addView(mFocusAnimationView, new MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 定义对焦动画
        mFocusAnimation = AnimationUtils.loadAnimation(context, R.anim.focus_animation);
        mFocusAnimation.setAnimationListener(new Animation.AnimationListener()
        {
            @Override
            public void onAnimationStart(Animation animation)
            {

            }

            @Override
            public void onAnimationEnd(Animation animation)
            {
                mFocusAnimationView.setVisibility(INVISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation)
            {

            }
        });
    }

    /**
     * 获取相机
     *
     * @return
     */
    public Camera getCamera()
    {
        return mCamera;
    }

    /**
     * 设置相机（初始化后必须调用，否则没有预览效果）
     *
     * @param camera   相机
     * @param cameraId 相机id
     */
    public void setCamera(Camera camera, int cameraId)
    {
        mCamera = camera;
        mCameraId = cameraId;
        CameraHelper.setCameraDisplayOrientation((Activity) getContext(), mCameraId, mCamera);

        if (mRealCameraPreviewView != null)
        {
            removeView(mRealCameraPreviewView);
        }
        postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                addView((mRealCameraPreviewView = new RealCameraPreviewView(getContext(), mCamera)), 0);
                //添加半透明蒙层
                View view = new View(getContext());
                view.setClickable(true);
                view.setBackgroundColor(0xAF000000);
                int height =  getHeight() - (int)(getWidth()/viewWHRatio) - yOffset;
                FrameLayout.LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,height);
                layoutParams.gravity = Gravity.BOTTOM;
                view.setLayoutParams(layoutParams);
                addView(view);
            }
        }, OPEN_CAMERA_DELAY);
    }

    /**
     * 添加预览事件监听器
     *
     * @param previewEventListener
     */
    public void addPreviewEventListener(PreviewEventListener previewEventListener)
    {
        mPreviewEventListenerList.add(previewEventListener);
    }

    public void setViewWHRatio(float viewWHRatio)
    {
        this.viewWHRatio = viewWHRatio;
        requestLayout();
    }

    public void setYOffset(int offset)
    {
        this.yOffset = offset;
    }

    @Override
    protected void onDetachedFromWindow()
    {
        super.onDetachedFromWindow();
        mPreviewEventListenerList.clear();
        mPreviewEventListenerList = null;
    }

    /**
     * 实际相机预览视图
     *
     * @author Martin
     */
    private class RealCameraPreviewView extends SurfaceView implements SurfaceHolder.Callback, Camera.AutoFocusCallback
    {

        private static final String TAG = "RealCameraPreviewView";

        // 用于判断双击事件的两次按下事件的间隔
        private static final long DOUBLE_CLICK_INTERVAL = 200;

        private Camera mCamera;

        private long mLastTouchDownTime;

        private ZoomRunnable mZoomRunnable;

        private int focusAreaSize;
        private Matrix matrix;

        public RealCameraPreviewView(Context context, Camera camera)
        {
            super(context);
            mCamera = camera;

            focusAreaSize = getResources().getDimensionPixelSize(R.dimen.camera_focus_area_size);
            matrix = new Matrix();
            getHolder().addCallback(this);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
        {
            Camera.Size size = mCamera.getParameters().getPreviewSize();
            float ratio = 1f * size.height / size.width;
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = (int) (width / ratio);
            int wms = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int hms = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            super.onMeasure(wms, hms);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event)
        {
            switch (event.getAction())
            {
                case MotionEvent.ACTION_DOWN:
                    long downTime = System.currentTimeMillis();
                    if (mCamera.getParameters().isZoomSupported() && downTime - mLastTouchDownTime <= DOUBLE_CLICK_INTERVAL)
                    {
                        zoomPreview();
                    }
                    mLastTouchDownTime = downTime;
                    focusOnTouch(event.getX(), event.getY());
                    break;
            }
            return super.onTouchEvent(event);
        }

        @Override
        public void onAutoFocus(boolean success, Camera camera)
        {
            // 设置对焦方式为视频连续对焦
            CameraHelper.setCameraFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO, mCamera);
        }

        /**
         * 放大预览视图
         */
        private void zoomPreview()
        {
            Camera.Parameters parameters = mCamera.getParameters();
            int currentZoom = parameters.getZoom();
            int maxZoom = (int) (parameters.getMaxZoom() / 2f + 0.5);
            int destZoom = 0 == currentZoom ? maxZoom : 0;
            if (parameters.isSmoothZoomSupported())
            {
                mCamera.stopSmoothZoom();
                mCamera.startSmoothZoom(destZoom);
            }
            else
            {
                Handler handler = getHandler();
                if (null == handler)
                {
                    return;
                }
                handler.removeCallbacks(mZoomRunnable);
                handler.post(mZoomRunnable = new ZoomRunnable(destZoom, currentZoom, mCamera));
            }
        }

        /**
         * On each tap event we will calculate focus area and metering area.
         * <p>
         * Metering area is slightly larger as it should contain more info for exposure calculation.
         * As it is very easy to over/under expose
         */
        private void focusOnTouch(final float x, final float y)
        {
            mCamera.cancelAutoFocus();
            // 设置对焦方式为自动对焦
            CameraHelper.setCameraFocusMode(Camera.Parameters.FOCUS_MODE_AUTO, mCamera);
            mCamera.autoFocus(this);

            mFocusAnimation.cancel();
            mFocusAnimationView.clearAnimation();

            int left = (int) (x - mFocusAnimationView.getWidth() / 2f);
            int top = (int) (y - mFocusAnimationView.getHeight() / 2f);
            MarginLayoutParams params = (MarginLayoutParams) mFocusAnimationView.getLayoutParams();
            params.setMargins(left,top,0,0);
            mFocusAnimationView.setLayoutParams(params);
            mFocusAnimationView.setVisibility(VISIBLE);
            mFocusAnimationView.startAnimation(mFocusAnimation);
        }

        /**
         * Convert touch position x:y to {@link Camera.Area} position -1000:-1000 to 1000:1000.
         * <p>
         * Rotate, scale and translate touch rectangle using matrix configured in
         * {@link SurfaceHolder.Callback#surfaceChanged(SurfaceHolder, int, int, int)}
         */
        private Rect calculateTapArea(float x, float y, float coefficient)
        {
            int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();

            int left = clamp((int) x - areaSize / 2, 0, getWidth() - areaSize);
            int top = clamp((int) y - areaSize / 2, 0, getHeight() - areaSize);

            RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);
            matrix.mapRect(rectF);

            return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
        }

        private int clamp(int x, int min, int max)
        {
            if (x > max)
            {
                return max;
            }
            if (x < min)
            {
                return min;
            }
            return x;
        }

        public void surfaceCreated(SurfaceHolder holder)
        {
            Log.d(TAG, "surfaceCreated");
        }

        public void surfaceDestroyed(SurfaceHolder holder)
        {
            // empty. Take care of releasing the Camera preview in your activity.
            Log.d(TAG, "surfaceDestroyed");
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h)
        {
            Log.d(TAG, "surfaceChanged w: " + w + "---h: " + h);

            // If your preview can change or rotate, take care of those events here.
            // Make sure to stop the preview before resizing or reformatting it.

            if (holder.getSurface() == null)
            {
                // preview surface does not exist
                return;
            }

            // stop preview before making changes
            try
            {
                mCamera.stopPreview();
            }
            catch (Exception e)
            {
                // ignore: tried to stop a non-existent preview
            }

            // set preview size and make any resize, rotate or
            // reformatting changes here
            //            Camera.Parameters parameters = mCamera.getParameters();
            //            Camera.Size size = CameraHelper.getOptimalPreviewSize(parameters.getSupportedPreviewSizes(), Math.min(w, h));
            //            Log.d(TAG, "OptimalPreviewSize w: " + size.width + "---h: " + size.height);
            //            parameters.setPreviewSize(size.width, size.height);
            //            mCamera.setParameters(parameters);
            // 预览尺寸改变，请求重新布局、计算宽高
            requestLayout();

            for (PreviewEventListener previewEventListener : mPreviewEventListenerList)
                previewEventListener.onPrePreviewStart();

            // start preview with new settings
            try
            {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();

                int height =  (int)(CameraPreviewView.this.getWidth() * 9f/16f);
                focusOnTouch(CameraPreviewView.this.getWidth() / 2f, height / 2f);
            }
            catch (Exception e)
            {
                Log.d(TAG, "Error starting camera preview: " + e.getMessage());
            }
        }

    }

    /**
     * 放大预览视图任务
     *
     * @author Martin
     */
    private static class ZoomRunnable implements Runnable
    {

        int destZoom, currentZoom;
        WeakReference<Camera> cameraWeakRef;

        public ZoomRunnable(int destZoom, int currentZoom, Camera camera)
        {
            this.destZoom = destZoom;
            this.currentZoom = currentZoom;
            cameraWeakRef = new WeakReference<>(camera);
        }

        @Override
        public void run()
        {
            Camera camera = cameraWeakRef.get();
            if (null == camera)
            {
                return;
            }

            boolean zoomUp = destZoom > currentZoom;
            for (int i = currentZoom; zoomUp ? i <= destZoom : i >= destZoom; i = (zoomUp ? ++i : --i))
            {
                Camera.Parameters parameters = camera.getParameters();
                parameters.setZoom(i);
                camera.setParameters(parameters);
            }
        }
    }

    /**
     * 预览画面改变监听器
     *
     * @author Martin
     */
    public interface PreviewEventListener
    {
        /**
         * 预览开始前回调
         */
        void onPrePreviewStart();
    }

}

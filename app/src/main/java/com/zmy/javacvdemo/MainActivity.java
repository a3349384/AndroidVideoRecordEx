package com.zmy.javacvdemo;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.zmy.javacvdemo.utils.BitmapUtil;
import com.zmy.javacvdemo.utils.ContextUtil;
import com.zmy.javacvdemo.utils.FileUtil;
import com.zmy.javacvdemo.utils.PathUtil;
import com.zmy.javacvdemo.utils.ScreenUtil;
import com.zmy.javacvdemo.utils.ToastUtil;
import com.zmy.javacvdemo.widget.TextSplitView;
import com.zmy.javacvdemo.widget.VideoRecordProgressBar;
import com.zmy.video.camera.CameraHelper;
import com.zmy.video.recorder.VideoRecorderEx;
import com.zmy.video.views.CameraPreviewView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity
    implements View.OnTouchListener
{
    public static final String TAG = "MainActivity";
    // 输出宽度
    private int OUTPUT_WIDTH = 640;
    // 输出高度
    private int OUTPUT_HEIGHT = 360;
    //拍摄类型为短视频
    private int MEDIA_TYPE_SHORT_VIDEO = 0;
    //拍摄类型为照片
    private int MEDIA_TYPE_PHOTO = 1;
    //短视频最大时长(秒)
    private int SHORT_VIDEO_MAX_DURATION = 5;

    private Camera camera;
    private VideoRecorderEx videoRecorder;
    private CameraPreviewView cameraPreviewView;
    private TextSplitView textSplitView;
    private VideoRecordProgressBar videoRecordProgressBar;
    private ProgressDialog progressDialog;

    private int currentMediaType;//标志当前拍摄的媒体类型（短视频、照片）

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraPreviewView = (CameraPreviewView) findViewById(R.id.cameraPreviewView);
        textSplitView = (TextSplitView) findViewById(R.id.textSplitView);
        videoRecordProgressBar = (VideoRecordProgressBar) findViewById(R.id.progressBar_video);


        //监听录制按钮的触摸事件
        findViewById(R.id.view_record).setOnTouchListener(this);

        textSplitView.setAdapter(new TextSplitViewAdapter());
        textSplitView.setItemSelectedChangeListener(new MediaTypeSelectedChangeListener());
        textSplitView.setCurrentItem(currentMediaType);

        videoRecordProgressBar.setMaxSeconds(SHORT_VIDEO_MAX_DURATION);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        initCamera();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (videoRecorder != null)
        {
            //停止录制
            videoRecorder.requestStopRecord(true);
            videoRecorder.setOnRecordCompleteListener(null);
        }
        releaseCamera();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event)
    {
        switch (event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
            {
                if (currentMediaType == MEDIA_TYPE_SHORT_VIDEO)
                {
                    //按下后就开始拍摄短视频
                    startRecord();
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            {
                if (currentMediaType == MEDIA_TYPE_SHORT_VIDEO)
                {
                    //抬起后停止拍摄视频
                    requestStopRecord();
                }
                else
                {
                    //抬起后开始拍照
                    takePhoto();
                }
                break;
            }
        }
        return true;
    }

    private void initCamera()
    {
        int cameraId = CameraHelper.getDefaultCameraID();
        camera = CameraHelper.getCameraInstance(cameraId);
        if (camera == null)
        {
            doOnNoneCamera();
            return;
        }

        Camera.Parameters parameters = camera.getParameters();
        //region 设置预览大小
        List<Camera.Size> preSizeList = parameters.getSupportedPreviewSizes();
        Camera.Size preferSize = null;
        int preferIndex = -1;
        //找到高度>720的项，以宽高为目标优先级最高
        for (int i = preSizeList.size() - 1; i >= 0; i--)
        {
            Camera.Size picSize = preSizeList.get(i);
            if (picSize.height >= 720)
            {
                if (preferIndex < 0)
                {
                    preferIndex = i;
                }

                if (Math.abs((float)picSize.width/(float) picSize.height - (float)OUTPUT_WIDTH/(float)OUTPUT_HEIGHT) <= 0.001)
                {
                    preferSize = picSize;
                    break;
                }
            }
        }

        if (preferSize == null)
        {
            preferSize = preSizeList.get(preferIndex);
        }
        parameters.setPreviewSize(preferSize.width, preferSize.height);
        //endregion

        //region 设置camera预览帧率
        List<int[]> fpsList = parameters.getSupportedPreviewFpsRange();
        int[] fps = fpsList.get(fpsList.size() - 1);
        parameters.setPreviewFpsRange(fps[0], fps[1]);
        //endregion

        if (parameters.isVideoStabilizationSupported())
        {
            parameters.setVideoStabilization(true);
        }
        camera.setParameters(parameters);

        cameraPreviewView.setCamera(camera, cameraId);
        // 初始化录像机
        videoRecorder = new VideoRecorderEx(ContextUtil.getAppContext());
        videoRecorder.setCameraPreviewView(cameraPreviewView);
        videoRecorder.setVideoOutputSize(OUTPUT_WIDTH, OUTPUT_HEIGHT);
        videoRecorder.setMaxRecordTime(SHORT_VIDEO_MAX_DURATION * 1000);
        videoRecorder.setFrameRate(30);
        videoRecorder.setVideoBitRate(1000 * 1000);
        videoRecorder.setOnRecordCompleteListener(new VideoRecordAutoCompleteListener());
        videoRecorder.initRecorder();
    }

    private void releaseCamera()
    {
        if (camera != null)
        {
            // 释放前先停止预览
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }

        if (videoRecorder != null)
        {
            videoRecorder.release();
        }
        videoRecorder = null;
    }

    private void startRecord()
    {
        if (!FileUtil.isSDCardMounted())
        {
            doOnCannotRecord();
            return;
        }

        //开始录制
        String videoPath = generateNewVideoPath();
        Log.i(TAG, "the video path is: " + videoPath);
        if (!videoRecorder.startRecord(videoPath))
        {
            doOnCannotRecord();
        }

        //显示录制进度条
        videoRecordProgressBar.start();
    }

    private void requestStopRecord()
    {
        if (!videoRecorder.isRecording())
        {
            return;
        }
        //隐藏录制进度条
        videoRecordProgressBar.stop();
        videoRecorder.requestStopRecord(false);
        //显示等待对话框，等待视频真实录入完毕
        showProgressWindow();
    }

    private void takePhoto()
    {
        showProgressWindow();
        byte[] data = videoRecorder.getLastPreviewData();
        saveYuvToFileAsync(data)
                .subscribe(new Observer<String>()
                {
                    @Override
                    public void onCompleted()
                    {
                        hideProgressWindow();
                    }

                    @Override
                    public void onError(Throwable e)
                    {
                        hideProgressWindow();
                        doOnCannotTakePhoto();
                    }

                    @Override
                    public void onNext(String photoPath)
                    {
                        String tip = "take photo completed,path:" + photoPath;
                        Log.i(TAG, tip);
                        ToastUtil.showShort(tip);
                    }
                });
    }

    private Observable<String> saveYuvToFileAsync(final byte[] data) {
        return Observable.create(new Observable.OnSubscribe<String>()
        {
            @Override
            public void call(Subscriber<? super String> subscriber)
            {
                try
                {
                    int YUVWidth = camera.getParameters().getPreviewSize().width;
                    int YUVHeight = camera.getParameters().getPreviewSize().height;
                    //将YUV转为bitmap
                    YuvImage img = new YuvImage(data, ImageFormat.NV21, YUVWidth, YUVHeight, null);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    //裁剪为目标分辨率的图片
                    int y = (int) (videoRecorder.getYOffset() / ((float)ScreenUtil.getScreenHeight() / YUVWidth));
                    img.compressToJpeg(new Rect(y, 0, (int) (YUVHeight * (9f / 16f)) + y, YUVHeight), 100, out);
                    byte[] imageBytes = out.toByteArray();
                    out.close();
                    //将图片旋转90度
                    Bitmap bitmapOri = BitmapUtil.getBitmapFromBytes(imageBytes);
                    Bitmap bitmapRotate = BitmapUtil.rotate(bitmapOri, 90);
                    bitmapOri.recycle();
                    //保存至文件
                    String photoDir = PathUtil.getPhotoPath();
                    String photoName = "IMG_" + System.currentTimeMillis() + ".jpg";
                    boolean saveSuccess = BitmapUtil.saveBitmap(BitmapUtil.getBytesFromBitmapJpeg(bitmapRotate), photoDir, photoName);
                    bitmapRotate.recycle();
                    if (saveSuccess)
                    {
                        subscriber.onNext(photoDir + File.separator + photoName);
                        subscriber.onCompleted();
                    }
                    else
                    {
                        subscriber.onError(null);
                    }
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                    subscriber.onError(ex);
                }
            }
        }).subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread());

    }


    /*未能打开照相机*/
    private void doOnNoneCamera()
    {
        Log.i(TAG, "cannot open camera");
    }

    /*无法录制视频*/
    private void doOnCannotRecord()
    {
        Log.i(TAG, "cannot record video");
    }

    /*无法拍照*/
    private void doOnCannotTakePhoto()
    {
        Log.i(TAG, "cannot take photo");
    }

    private String generateNewVideoPath()
    {
        return PathUtil.getRecordPath() + File.separator + "video_" + System.currentTimeMillis() + ".mp4";
    }

    private void showProgressWindow()
    {
        if (progressDialog == null)
        {
            progressDialog = new ProgressDialog(this);
        }
        if (progressDialog.isShowing())
        {
            return;
        }
        progressDialog.setCancelable(false);
        progressDialog.setMessage("请稍候...");
        progressDialog.show();
    }

    private void hideProgressWindow()
    {
        if (progressDialog == null)
        {
            return;
        }

        progressDialog.dismiss();
    }

    class TextSplitViewAdapter extends TextSplitView.Adapter
    {
        private ArrayList<String> items;

        public TextSplitViewAdapter()
        {
            this.items = new ArrayList<>(2);
            this.items.add("短视频");
            this.items.add("拍照");
        }

        @Override
        public int getItemCount()
        {
            return items.size();
        }

        @Override
        public String getItem(int position)
        {
            return items.get(position);
        }

        @Override
        public int getSelectedColor()
        {
            return Color.WHITE;
        }

        @Override
        public int getUnSelectColor()
        {
            return 0x7FFFFFFF;
        }

        @Override
        public int getTextSize()
        {
            return 16;
        }
    }

    /*拍摄类型（短视频、照片）改变监听器*/
    class MediaTypeSelectedChangeListener implements TextSplitView.OnItemSelectedChangeListener
    {
        @Override
        public void change(int currentPosition)
        {
            if (currentPosition == 0)
            {
                //拍摄短视频
                currentMediaType = MEDIA_TYPE_SHORT_VIDEO;
            }
            else
            {
                //拍摄照片
                currentMediaType = MEDIA_TYPE_PHOTO;
                //拍摄类型为照片时，应隐藏录制进度条
                videoRecordProgressBar.setVisibility(View.GONE);
            }
        }
    }

    /*录像完毕监听器*/
    class VideoRecordAutoCompleteListener implements VideoRecorderEx.OnRecordCompleteListener
    {
        @Override
        public void onRecordComplete()
        {
            hideProgressWindow();
            String videoPath = videoRecorder.getVideoPath();

            String tip = "video record completed,path:" + videoPath;
            Log.i(TAG, tip);
            ToastUtil.showShort(tip);
        }
    }
}

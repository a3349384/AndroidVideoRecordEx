package com.zmy.video.recorder;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.zmy.video.models.TimeFrame;
import com.zmy.video.views.CameraPreviewView;

import org.bytedeco.javacv.FFmpegFrameFilter;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameFilter;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class VideoRecorderEx implements Camera.PreviewCallback, CameraPreviewView.PreviewEventListener
{
    private static final String TAG = "VideoRecorderEx";
    // 帧率
    private int FRAME_RATE = 30;
    //视频比特率
    private int VIDEO_BIT_RATE = 300 * 1000;
    //最长录制时间，默认10S
    private long MAX_RECORD_TIME = 10000;
    // 输出文件路径
    private String videoPath;
    // 图片帧宽、高
    private int imageWidth = 640;
    private int imageHeight = 480;
    // 输出视频宽、高
    private int outputWidth = 320;
    private int outputHeight = 180;
    //Y方向偏移量
    private int yOffset;

    private Context context;
    private volatile FFmpegFrameRecorder recorder;
    private OnRecordCompleteListener recordCompleteListener;
    private long startTime;
    private boolean acceptFrame;
    private boolean stopRecordImmediately;
    private boolean isRecording;

    /**
     * 帧数据处理配置
     */
    private String frameFilterString;
    //保存本次拍摄的视频的所有帧
    private BlockingQueue<TimeFrame> frames;
    private byte[] lastPreviewData;
    // 图片帧过滤器
    private FFmpegFrameFilter frameFilter;
    // 相机预览视图
    private CameraPreviewView cameraPreviewView;

    public VideoRecorderEx(Context context)
    {
        this.context = context;
    }

    //region 开始和停止录制视频API

    /**
     * 开始录制视频
     * 调用此方法前必须调用initRecorder方法进行初始化
     */
    public boolean startRecord(String videoPath)
    {
        this.videoPath = videoPath;
        boolean success = true;
        try
        {
            preStartRecording();
            initFrameFilter();
            recorder.start();
            frameFilter.start();

            startTime = System.currentTimeMillis();
            isRecording = true;
            acceptFrame = true;

            new RecordFrameThread().start();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            success = false;
        }

        return success;
    }

    /**
     * 请求停止录制视频。整个录制过程会在所有帧都处理完成之后才会停止
     * 可以设置stopImmediately参数为true立即停止视频录制而不管未处理完成的视频帧
     *
     * @param stopImmediately 是否立即停止
     */
    public void requestStopRecord(boolean stopImmediately)
    {
        acceptFrame = false;
        stopRecordImmediately = stopImmediately;
    }
    //endregion

    //region 初始化API

    /**
     * 设置相机预览视图
     */
    public void setCameraPreviewView(CameraPreviewView cameraPreviewView)
    {
        this.cameraPreviewView = cameraPreviewView;
        this.cameraPreviewView.addPreviewEventListener(this);
        this.cameraPreviewView.setViewWHRatio((float) outputWidth / (float) outputHeight);
    }

    /**
     * 初始化recorder，必须在startRecord方法调用之前调用此方法
     */
    public void initRecorder()
    {
        if (recorder == null)
        {
            recorder = new FFmpegFrameRecorder("", outputWidth, outputHeight, 1);
        }
        recorder.setFormat("mp4");
        recorder.setFrameRate(FRAME_RATE);
        recorder.setVideoBitrate(VIDEO_BIT_RATE);

        initFrameFilter();
    }

    /**
     * 设置输出视频大小
     *
     * @param width  输出视频的宽度
     * @param height 输出视频的高度
     */
    public void setVideoOutputSize(int width, int height)
    {
        outputWidth = width;
        outputHeight = height;
    }

    /**
     * 设置视频的最大时常（毫秒）
     */
    public void setMaxRecordTime(long time)
    {
        MAX_RECORD_TIME = time;
    }

    /**
     * 设置视频比特率
     */
    public void setVideoBitRate(int rate)
    {
        VIDEO_BIT_RATE = rate;
    }

    /**
     * 设置帧率，推荐使用30
     * 注意：这个值实际上充当了视频的最大帧率，具体的帧率可能是动态变化的
     * 可以调用camera.getParameters().getSupportedPreviewFpsRange()查看支持的帧率范围
     */
    public void setFrameRate(int rate)
    {
        this.FRAME_RATE = rate;
    }

    /**
     * 设置Y方向偏移量
     * @param offset 偏移量，>=0.
     *               注意：offset的值是相对于screen size的像素大小，但是实际上camera的preview size和screen size很可能不一致
     *               所以代码中要对其做转换.
     *               假设offset的值为100，手机屏幕像素为2560 * 1440，照相机的预览像素为1280 * 720，那么实际上视频仅需要偏移100 / (2560 / 1280) = 50像素即可。
     *               具体实现代码参见方法：initFrameFilter
     */
    public void setYOffset(int offset)
    {
        if (offset < 0)
        {
            return;
        }

        this.yOffset = offset;
        if (cameraPreviewView != null)
        {
            cameraPreviewView.setYOffset(offset);
        }
        else
        {
            Log.w(TAG, "set YOffset maybe not success");
        }
    }

    /**
     * 设置视频录制真正完毕的监听器
     */
    public void setOnRecordCompleteListener(OnRecordCompleteListener listener)
    {
        this.recordCompleteListener = listener;
    }
    //endregion

    //region 其他API

    public boolean isRecording()
    {
        return isRecording;
    }

    /**
     * 释放资源
     */
    public void release()
    {
        try
        {
            lastPreviewData = null;
            if (recorder != null)
            {
                recorder.release();
                recorder = null;
            }

            if (frames != null)
            {
                frames.clear();
                frames = null;
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }

    }

    /**
     * 获取视频文件输出路径
     *
     * @return
     */
    public String getVideoPath()
    {
        return videoPath;
    }

    public byte[] getLastPreviewData()
    {
        return lastPreviewData;
    }

    public int getYOffset()
    {
        return yOffset;
    }
    //endregion

    //region 私有方法
    private void preStartRecording()
    {
        recorder.setFilePath(videoPath);
        frames = new ArrayBlockingQueue<>(FRAME_RATE * (int)(MAX_RECORD_TIME/1000) + 100);
    }

    /**
     * 初始化帧过滤器
     */
    private void initFrameFilter()
    {
        try
        {
            if (frameFilter != null)
            {
                frameFilter.release();
                frameFilter = null;
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }

        if (TextUtils.isEmpty(frameFilterString))
        {
            int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
            int y = (int) (yOffset / ((float)screenHeight / (float) imageWidth));

            //PS：在录制16：9比例的时候，我发现视频的上面一小块错位了，可能是FFmpeg在裁剪这样的分辨率出现了错误
            //如果是4：3的比例则没有这个问题
            //我没找到解决的办法，我对FFmpeg一点都不熟~~~
            //不过可以为Y增加几个偏移像素间接的解决这个问题，如y本来为0，然后这里传值的时候设置为Y + 4即可。
            frameFilterString = generateFilters((int) (1f * outputHeight / outputWidth * imageHeight), imageHeight, y, 0, "clock");
        }
        frameFilter = new FFmpegFrameFilter(frameFilterString, imageWidth, imageHeight);
        frameFilter.setPixelFormat(org.bytedeco.javacpp.avutil.AV_PIX_FMT_NV21); // default camera format on Android
    }

    /**
     * 生成处理配置
     *
     * @param w         裁切宽度
     * @param h         裁切高度
     * @param x         裁切起始x坐标
     * @param y         裁切起始y坐标
     * @param transpose 图像旋转参数
     * @return 帧图像数据处理参数
     */
    private String generateFilters(int w, int h, int x, int y, String transpose)
    {
        return String.format("crop=w=%d:h=%d:x=%d:y=%d,transpose=%s", w, h, x, y, transpose);
    }
    //endregion

    @Override
    public void onPrePreviewStart()
    {
        Camera camera = cameraPreviewView.getCamera();
        Camera.Parameters parameters = camera.getParameters();
        Camera.Size size = parameters.getPreviewSize();
        camera.setParameters(parameters);

        // 设置Recorder处理的的图像帧大小
        imageWidth = size.width;
        imageHeight = size.height;

        camera.setPreviewCallbackWithBuffer(this);
        camera.addCallbackBuffer(new byte[size.width * size.height * ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8]);
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera)
    {
        try
        {
            lastPreviewData = data;
            if (!acceptFrame)
            {
                return;
            }

            //判断是否到达最大录像时间
            long recordingTime = System.currentTimeMillis() - startTime;
            if (recordingTime > MAX_RECORD_TIME)
            {
                requestStopRecord(false);
                return;
            }

            TimeFrame frame = new TimeFrame(imageWidth, imageHeight, Frame.DEPTH_UBYTE, 2,System.currentTimeMillis());
            ((ByteBuffer) frame.image[0].position(0)).put(data);
            try
            {
                frames.put(frame);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
        finally
        {
            camera.addCallbackBuffer(data);
        }
    }

    /**
     * 视频帧处理线程
     * */
    class RecordFrameThread extends Thread
    {
        private int recordFrameCount = 0;

        @Override
        public void run()
        {
            super.run();
            while (true)
            {
                try
                {
                    if (stopRecordImmediately)
                    {
                        performStopRecord();
                        break;
                    }

                    //默认1s之内没有获取到新的帧，就认为本次录制已经完毕
                    TimeFrame frame = frames.poll(1, TimeUnit.SECONDS);
                    if (frame == null && !acceptFrame)
                    {
                        performStopRecord();
                        break;
                    }

                    long recordingTime = frame.keyTime - startTime;
                    recorder.setTimestamp(recordingTime * 1000);
                    recordFrame(frame);

                    recordFrameCount++;
                    Log.i(TAG,"record frame count:" + recordFrameCount);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }

        private boolean recordFrame(Frame frame)
        {
            try
            {
                frameFilter.push(frame);
                Frame filteredFrame;
                while ((filteredFrame = frameFilter.pull()) != null)
                {
                    recorder.record(filteredFrame);
                }

                return true;
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                return false;
            }
        }

        private void performStopRecord()
        {
            isRecording = false;

            try
            {
                recorder.stop();
                recorder.release();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            // 释放帧过滤器
            releaseFrameFilter();

            if (recordCompleteListener == null)
            {
                return;
            }

            new Handler(Looper.getMainLooper()).post(new Runnable()
            {
                @Override
                public void run()
                {
                    recordCompleteListener.onRecordComplete();
                }
            });
        }

        private void releaseFrameFilter()
        {
            if (frameFilter != null)
            {
                try
                {
                    frameFilter.release();
                }
                catch (FrameFilter.Exception e)
                {
                    e.printStackTrace();
                }
            }
            frameFilter = null;
        }
    }

    /**
     * 录制完成监听器
     *
     * @author Martin
     */
    public interface OnRecordCompleteListener
    {
        /**
         * 录制完成回调
         */
        void onRecordComplete();
    }
}

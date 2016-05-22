# AndroidVideoRecordEx
基于项目https://github.com/szitguy/RecordVideoDemo 优化而来。感谢@szitguy

<br/>
支持自定义分辨率录像、自定义分辨率拍照、异步录制等功能。<br/>
demo程序下载：https://github.com/a3349384/AndroidVideoRecordEx/blob/master/demo.apk?raw=true<br/>

个人能力有限，存在如下未解决的问题：<br/>
1、目前视频数据通过Camara的PreviewCallback取得，这样的方式限制了视频最大帧率。
我测试过数款手机，其中：<br/>
Moto X Pro(原声安卓系统)，帧率处于24-30FPS之间<br/>
小米手机（MIUI 7），帧率处于24-30FPS之间<br/>
魅族MX4（Flyme 5），帧率几乎可稳定在30FPS<br/>
另外一款CM系统手机，帧率仅10FPS，真是日了狗了。<br/>
因为是异步录制，上述FPS和视频分辨率、视频比特率等没有太大关系。性能瓶颈主要在于系统Camara提供的原始数据帧率可能达不到30FPS。<br/>

2、so包目前仅有arm-v7架构<br/>

最后，由于便于为了分析代码，我把声音的录制代码删掉了，如果需要声音录制功能的朋友，可前往原始项目查看相关代码。链接上面有。<br/>


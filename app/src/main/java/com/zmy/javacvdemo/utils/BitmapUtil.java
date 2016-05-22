package com.zmy.javacvdemo.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

public class BitmapUtil
{
    public static Bitmap getBitmapFromBytes(byte[] b) {
        if (b.length != 0) {
            return BitmapFactory.decodeByteArray(b, 0, b.length);
        } else {
            return null;
        }
    }

    public static Bitmap rotate(Bitmap bitmap, int angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                bitmap.getHeight(), matrix, true);
    }

    public static boolean saveBitmap(byte[] data,String imageDir,String imageName)
    {
        try
        {
            File fileDir = new File(imageDir);
            if (!fileDir.exists() && !fileDir.mkdirs())
            {
                return false;
            }

            File fileImage = new File(fileDir.getAbsolutePath() + File.separator + imageName);
            ByteArrayInputStream e = new ByteArrayInputStream(data);
            FileOutputStream fileOutputStream = new FileOutputStream(fileImage);
            byte[] buffer = new byte[2048];

            int length;
            while((length = e.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, length);
            }

            fileOutputStream.close();
            e.close();

            return true;
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    public static byte[] getBytesFromBitmapJpeg(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        return baos.toByteArray();
    }
}

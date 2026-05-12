package com.miladghouila.proscanai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;

public class ScreenCaptureManager {

    private final WindowManager windowManager;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int width, height, density;

    public interface CaptureCallback {
        void onBitmapCaptured(Bitmap bitmap);
        void onError(String message);
    }

    public ScreenCaptureManager(Context context) {
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        updateDisplayMetrics();
    }

    private void updateDisplayMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        density = metrics.densityDpi;
    }

    /**
     * Initializes a persistent VirtualDisplay. 
     * This MUST be called immediately after getting MediaProjection on Android 14+
     */
    public void initPersistentCapture(MediaProjection mediaProjection, Handler handler) {
        stop(); // Cleanup any old session
        updateDisplayMetrics();

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "LinguScan_Persistent",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                handler
        );
    }

    /**
     * Grabs the current frame from the persistent display.
     */
    public void captureCurrentFrame(CaptureCallback callback) {
        if (imageReader == null) {
            callback.onError("Capture session not initialized");
            return;
        }

        Image image = imageReader.acquireLatestImage();
        if (image == null) {
            callback.onError("Screen capture is busy. Try again in a second.");
            return;
        }

        try {
            Bitmap bitmap = convertImage(image);
            if (bitmap != null) {
                callback.onBitmapCaptured(bitmap);
            } else {
                callback.onError("Failed to process screen buffer");
            }
        } catch (Exception e) {
            callback.onError("Capture error: " + e.getMessage());
        } finally {
            image.close();
        }
    }

    private Bitmap convertImage(Image image) {
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();

            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();
            int rowPadding = rowStride - pixelStride * imgWidth;

            Bitmap bitmap = Bitmap.createBitmap(
                    imgWidth + rowPadding / pixelStride,
                    imgHeight,
                    Bitmap.Config.ARGB_8888
            );

            bitmap.copyPixelsFromBuffer(buffer);
            return Bitmap.createBitmap(bitmap, 0, 0, imgWidth, imgHeight);
        } catch (Exception e) {
            return null;
        }
    }

    public void stop() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }
}
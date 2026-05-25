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
import android.os.Looper;
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

        // Use 5 as max images for extra stability on high-resolution screens
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 5);
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
     * Grabs the current frame from the persistent display with an aggressive retry mechanism.
     */
    public void captureCurrentFrame(final CaptureCallback callback) {
        if (imageReader == null || virtualDisplay == null) {
            callback.onError("Capture session not initialized. Please restart Screen Magic.");
            return;
        }

        attemptCapture(0, callback);
    }

    private void attemptCapture(final int count, final CaptureCallback callback) {
        Image image = null;
        try {
            // First try to get the very latest frame
            image = imageReader.acquireLatestImage();
        } catch (Exception e) {
            // Fallback for some device-specific states
        }

        if (image != null) {
            processCapturedImage(image, callback);
        } else if (count < 8) {
            // If latest is null, it means no new frames were pushed. 
            // Retry with increasing delay (up to 1.2s total wait)
            new Handler(Looper.getMainLooper()).postDelayed(() -> attemptCapture(count + 1, callback), 150);
        } else {
            // Last resort: try acquireNextImage which might have an older frame but is better than nothing
            try {
                image = imageReader.acquireNextImage();
            } catch (Exception e) {
                // Ignore
            }

            if (image != null) {
                processCapturedImage(image, callback);
            } else {
                callback.onError("Screen buffer is empty. Ensure your screen is active and try again.");
            }
        }
    }

    private void processCapturedImage(Image image, CaptureCallback callback) {
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
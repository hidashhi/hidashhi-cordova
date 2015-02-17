package com.hidashhi.cordovaplugin;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.opengl.GLSurfaceView;
import android.os.Build;

import java.util.Random;

public class VideoGLView extends GLSurfaceView {
    private Point screenDimensions;

    private final static boolean isAndroidEmulator() {
        String product = Build.PRODUCT;
        boolean isEmulator = false;
        if (product != null) {
            isEmulator = product.equals("sdk") || product.contains("_sdk") || product.contains("sdk_");
        }
        return isEmulator;
    }

    public VideoGLView(Context c, Point screenDimensions) {
        super(c);
        if (isAndroidEmulator()) {
            // Switch to supported OpenGL (ARGB888) mode on emulator
            this.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        }
        this.screenDimensions = screenDimensions;
    }

    public void updateDisplaySize(Point screenDimensions) {
        this.screenDimensions = screenDimensions;
    }

    @Override
    protected void onMeasure(int unusedX, int unusedY) {
        // Go big or go home!
        setMeasuredDimension(screenDimensions.x, screenDimensions.y);
    }
}
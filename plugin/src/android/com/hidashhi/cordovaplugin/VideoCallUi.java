package com.hidashhi.cordovaplugin;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.v4.app.NotificationCompat;
import android.view.Display;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.hidashhi.demo.R;

import org.apache.cordova.CordovaInterface;
import org.webrtc.VideoRendererGui;

/**
 *  Video call UI related staff
 */
public class VideoCallUi {
    private static final int PRIORITY_HIGH = 5;
    private static final int CALL_IN_PROGRESS_NOTIFICATION = 1;

    private final WebView _webView;
    private final CordovaInterface _cordova;
    private final HidashHiPlugin _plugin;

    RelativeLayout _relativeLayout = null;
    private VideoGLView _videoView; // Single video view, fullscreen. Streams are rendered on this view.

    public VideoCallUi(WebView webView, CordovaInterface cordova, HidashHiPlugin plugin) {
        _webView = webView;
        _cordova = cordova;
        _plugin = plugin;
    }

    public void onOrientationChanged() {
        if (_videoView != null) {
            refreshVideoView();
        }
    }

    private void createLayout() {
        _relativeLayout = new RelativeLayout(_webView.getContext());
        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);

        ViewGroup rootViewGroup = (ViewGroup) _webView.getParent();
        rootViewGroup.removeView(_webView);
        rootViewGroup.addView(_relativeLayout, relativeParams);

        RelativeLayout.LayoutParams relativeParams2 = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        _relativeLayout.addView(_webView, relativeParams2);

        showCallInProgressNotification();
    }

    private void showCallInProgressNotification() {
        Context context = _cordova.getActivity().getApplicationContext();
        Class<?> activityClass = _cordova.getActivity().getClass();
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);

        Intent resultIntent = new Intent(context, activityClass);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent intent = PendingIntent.getActivity(context, 0, resultIntent, 0);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle("HidashHi")
                .setContentText("Call in progress")
                .setOngoing(true)
                .setContentIntent(intent)
                .setPriority(PRIORITY_HIGH);

        mNotificationManager.notify(CALL_IN_PROGRESS_NOTIFICATION, mBuilder.build());
    }

    private void hideCallInProgressNotification() {
        Context context = _cordova.getActivity().getApplicationContext();
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);

        mNotificationManager.cancel(CALL_IN_PROGRESS_NOTIFICATION);
    }

    private void createVideoView() {
        Display display = _cordova.getActivity().getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        if (_relativeLayout == null) {
            createLayout();
        }

        _videoView = new VideoGLView(_cordova.getActivity(), size);
        VideoRendererGui.setView(_videoView, new Runnable() {
            @Override
            public void run() {
            }
        });

        WebView.LayoutParams webViewLayoutParams = new WebView.LayoutParams(size.x, size.y, 0, 0);
        _relativeLayout.addView(_videoView, webViewLayoutParams);
        _relativeLayout.bringChildToFront(_webView);
    }

    public void refreshVideoView() {
        final int remoteVideoCount = _plugin.getTrackManager().getRemoteVideos().size();

        _plugin.getTrackManager().removeRenderers();

        if (_videoView != null) {
            _relativeLayout.removeView(_videoView);
        }
        createVideoView();

        int remoteVideoWidthPercentage = remoteVideoCount > 0 ? 100/remoteVideoCount : 0;
        for (int i = 0; i < remoteVideoCount; i++) {
            VideoTrackRendererPair pair = _plugin.getTrackManager().getRemoteVideos().get(i);
            int x = remoteVideoWidthPercentage*i;
            int y = 0;

            pair.createVideoRenderer(x, y, remoteVideoWidthPercentage, 100);
        }

        if (_plugin.getTrackManager().getLocalVideo() != null) {
            Rect selfCoords = remoteVideoCount == 0 ?
                    new Rect(0, 0, 100, 100) : // full screen for no participants
                    new Rect(60, 60, 90, 90);  // right bottom corner in case of any participants
            _plugin.getTrackManager().getLocalVideo().createVideoRenderer(selfCoords.left, selfCoords.top,
                    selfCoords.width(), selfCoords.height());
        }
    }

    public void cleanup() {
        // UI cleanup
        hideCallInProgressNotification();
    }
}

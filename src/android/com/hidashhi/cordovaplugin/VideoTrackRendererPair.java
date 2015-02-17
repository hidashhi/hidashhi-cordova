package com.hidashhi.cordovaplugin;

import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoTrack;

public class VideoTrackRendererPair {
    private VideoTrack _videoTrack;
    private VideoRenderer _videoRenderer;

    public VideoTrackRendererPair(VideoTrack videoTrack, VideoRenderer videoRenderer) {
        _videoTrack = videoTrack;
        _videoRenderer = videoRenderer;
    }

    public VideoTrack getVideoTrack() {
        return _videoTrack;
    }

    public void removeVideoRenderer() {
        if (_videoRenderer != null) {
            // VideoTrack.removeRenderer also disposes the renderer
            _videoTrack.removeRenderer(_videoRenderer);
            _videoRenderer = null;
        }
    }

    public void createVideoRenderer(int x, int y, int width, int height) {
        _videoRenderer = new VideoRenderer(
                VideoRendererGui.create(x, y, width, height,
                        VideoRendererGui.ScalingType.SCALE_ASPECT_FIT, true));
        _videoTrack.addRenderer(_videoRenderer);
    }

    public void dispose() {
        _videoTrack.dispose(); // Will also dispose its renderers
        _videoTrack = null;
    }
}

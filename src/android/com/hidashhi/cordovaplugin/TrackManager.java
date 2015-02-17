package com.hidashhi.cordovaplugin;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *  Video/audio track manager. Keeps local and remote tracks and associated renderers.
 *  Responsible for tracks initialization and cleanup.
 */
public class TrackManager {
    private AudioTrack _audioTrack;   // Local audio track

    private VideoTrackRendererPair _localVideo; // Local video track and renderer
    private VideoSource _videoSource = null;
    private VideoCapturer _videoCapturer = null;

    private Map<String, VideoTrack> _videoTracksByStreamId; // List of mediatracks indexed by stream id

    private List<VideoTrackRendererPair> _remoteVideos;

    public TrackManager() {
        _remoteVideos = new ArrayList<>();
        _videoTracksByStreamId = new HashMap<>();
    }

    public AudioTrack getAudioTrack() {
        return _audioTrack;
    }

    public VideoTrackRendererPair getLocalVideo() {
        return _localVideo;
    }

    /**
     * 1. Find the proper camera
     * 2. Create a Peer connection factory video source using this camera
     */
    public void initializeLocalVideoTrack(PeerConnectionFactory peerConnectionFactory) {
        _videoCapturer = getVideoCapturer();
        _videoSource = peerConnectionFactory.createVideoSource(_videoCapturer,
                new MediaConstraints());
        _localVideo = new VideoTrackRendererPair(peerConnectionFactory.createVideoTrack("ARDAMSv0",
                _videoSource), null);
    }

    public void removeRemoteVideoTrackForStream(String streamId) {
        VideoTrack videoTrack = _videoTracksByStreamId.get(streamId);
        for (VideoTrackRendererPair pair : _remoteVideos) {
            if (pair.getVideoTrack() == videoTrack) {
                // TODO remove the track from the MediaStream and probably call pair.dispose();
                pair.removeVideoRenderer();
                _remoteVideos.remove(pair);
                return;
            }
        }
    }

    public void initializeLocalAudioTrack(PeerConnectionFactory peerConnectionFactory) {
        AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        _audioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource);
    }

    public void addRemoteVideoTrack(String streamId, VideoTrack videoTrack) {
        _videoTracksByStreamId.put(streamId, videoTrack);
        _remoteVideos.add(new VideoTrackRendererPair(videoTrack, null));
    }

    // Cycle through likely device names for the camera and return the first
    // capturer that works, or crash if none do.
    private VideoCapturer getVideoCapturer() {
        String[] cameraFacing = { "front", "back" };
        int[] cameraIndex = { 0, 1 };
        int[] cameraOrientation = { 0, 90, 180, 270 };
        for (String facing : cameraFacing) {
            for (int index : cameraIndex) {
                for (int orientation : cameraOrientation) {
                    String name = "Camera " + index + ", Facing " + facing +
                            ", Orientation " + orientation;
                    VideoCapturer capturer = VideoCapturer.create(name);
                    if (capturer != null) {
                        return capturer;
                    }
                }
            }
        }
        throw new RuntimeException("Failed to open capturer");
    }

    public void removeRenderers() {
        for (VideoTrackRendererPair pair : _remoteVideos) {
            pair.removeVideoRenderer();
        }

        if (_localVideo != null) {
            _localVideo.removeVideoRenderer();
        }
    }

    public void disconnect() {
        _remoteVideos.clear();

        if (_localVideo != null) {
            _localVideo.dispose();
            _localVideo = null;
        }

        if (_videoSource != null) {
            _videoSource.dispose();
            _videoSource = null;
        }

        if (_videoCapturer != null) {
            _videoCapturer.dispose();
            _videoCapturer = null;
        }

        _videoTracksByStreamId.clear();
        _audioTrack = null;
    }

    public List<VideoTrackRendererPair> getRemoteVideos() {
        return _remoteVideos;
    }
}

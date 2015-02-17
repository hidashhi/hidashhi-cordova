package com.hidashhi.cordovaplugin;

import java.util.HashMap;
import java.util.Map;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoTrack;

import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

public class HidashHiPlugin extends CordovaPlugin {
    private TrackManager _trackManager = null;
    private VideoCallUi _videoCallUi = null;
    private PeerConnectionFactory _peerConnectionFactory; // Native WebRTC library

    private Map<String, RTCPeerConnection> _rtcPeerConnections;

    private boolean _initializedAndroidGlobals = false;

    public HidashHiPlugin() {
        _rtcPeerConnections = new HashMap<>();
    }

    private AppRTCAudioManager _audioManager = null;

    @Override
    public boolean execute(String action, JSONArray args,
            final CallbackContext callbackContext) throws JSONException {

        final HidashHiPlugin self = this;

        switch (action) {
            case "attachMediaStream":
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (!_initializedAndroidGlobals) {
                            abortUnless(PeerConnectionFactory.initializeAndroidGlobals(cordova.getActivity(), true, true, true,
                                            VideoRendererGui.getEGLContext()),
                                    "Failed to initializeAndroidGlobals");
                            _initializedAndroidGlobals = true;
                        }

                        if (_peerConnectionFactory == null) {
                            _peerConnectionFactory = new PeerConnectionFactory();
                        }

                        initializeLocalVideoTrack();
                        initializeLocalAudioTrack();
                    }
                });

                return true;
            case "RTCPeerConnection_create": {
                final String key = args.getString(0);
                final JSONObject options = args.getJSONObject(1);
                final JSONObject pcConstraints = args.getJSONObject(2);
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        RTCPeerConnection connection = new RTCPeerConnection(
                                _peerConnectionFactory, options, pcConstraints, cordova.getActivity(),
                                callbackContext, self);
                        _rtcPeerConnections.put(key, connection);
                    }
                });
                return true;
            }
            case "RTCPeerConnection_addStream": {
                final String key = args.getString(0);
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (_rtcPeerConnections.containsKey(key)) {
                            RTCPeerConnection connection = _rtcPeerConnections.get(key);
                            connection.addStream(_peerConnectionFactory,
                                    _trackManager.getAudioTrack(),
                                    _trackManager.getLocalVideo().getVideoTrack());
                        }
                    }
                });
                return true;
            }
            case "RTCPeerConnection_setRemoteDescription": {
                final String key = args.getString(0);
                final JSONObject sessionDescription = args.getJSONObject(1);
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (_rtcPeerConnections.containsKey(key)) {
                            RTCPeerConnection connection = _rtcPeerConnections.get(key);
                            connection.setRemoteDescription(sessionDescription);
                        }
                    }
                });
                return true;
            }
            case "RTCPeerConnection_addIceCandidate": {
                final String key = args.getString(0);
                final JSONObject iceCandidate = args.getJSONObject(1);
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (_rtcPeerConnections.containsKey(key)) {
                            RTCPeerConnection connection = _rtcPeerConnections.get(key);
                            connection.addIceCandidate(iceCandidate);
                        }
                    }
                });
                return true;
            }
            case "RTCPeerConnection_createOffer": {
                final String key = args.getString(0);
                final JSONObject sdpConstraints = args.getJSONObject(1);
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (_rtcPeerConnections.containsKey(key)) {
                            RTCPeerConnection connection = _rtcPeerConnections.get(key);
                            connection.createOffer(sdpConstraints, callbackContext);
                        }
                    }
                });
                return true;
            }
            case "RTCPeerConnection_createAnswer": {
                final String key = args.getString(0);
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (_rtcPeerConnections.containsKey(key)) {
                            RTCPeerConnection connection = _rtcPeerConnections.get(key);
                            connection.createAnswer(callbackContext);
                        }
                    }
                });
                return true;
            }
            case "RTCPeerConnection_setLocalDescription": {
                final String key = args.getString(0);
                final JSONObject offer = args.getJSONObject(1);
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (_rtcPeerConnections.containsKey(key)) {
                            RTCPeerConnection connection = _rtcPeerConnections.get(key);
                            connection.setLocalDescription(offer);
                        }
                    }
                });
                return true;
            }
            case "VideoTrackEnabled": {
                final Boolean enabled = args.getBoolean(0);
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        _trackManager.getLocalVideo().getVideoTrack().setEnabled(enabled);
                    }
                });
                return true;
            }
            case "AudioTrackEnabled": {
                final Boolean enabled = args.getBoolean(0);
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        _trackManager.getAudioTrack().setEnabled(enabled);
                    }
                });
                return true;
            }
            case "onVideoDomElementDeleted": {
                final String streamId = args.getString(0);
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        _trackManager.removeRemoteVideoTrackForStream(streamId);
                        getVideoCallUi().refreshVideoView();
                    }
                });
                return true;
            }
            case "orientationChanged": {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        getVideoCallUi().onOrientationChanged();
                    }
                });
                return true;
            }
            case "endCall": {
                endCall();
                return true;
            }
        }

        callbackContext.error("Invalid action: " + action);
        return false;
    }

    /**
     * Initialize local video:
     * - Find camera and create a video track
     * - Create the video view
     */
    private void initializeLocalVideoTrack() {
        if (_trackManager == null) {
            _trackManager = new TrackManager();
        }
        _trackManager.initializeLocalVideoTrack(_peerConnectionFactory);
        getVideoCallUi().refreshVideoView();
    }

    /**
     * Return video UI object
     */
    private VideoCallUi getVideoCallUi() {
        if (_videoCallUi == null) {
            _videoCallUi = new VideoCallUi(webView, cordova, this);
        }
        return _videoCallUi;
    }

    /**
     * Initialize local audio track and AppRTCAudioManager (which handles proximity sensor events)
     */
    private void initializeLocalAudioTrack() {
        _audioManager = AppRTCAudioManager.create(cordova.getActivity().getApplicationContext(),
                new Runnable() {
                    @Override
                    public void run() {
                        onAudioManagerChangedState();
                    }
                }
        );
        _audioManager.init();
        Window window = cordova.getActivity().getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        _trackManager.initializeLocalAudioTrack(_peerConnectionFactory);
    }

    /**
     * Handle audio being switched from earpiece to speakerphone and back.
     * Turns off screen when it's earpiece
     */
    private void onAudioManagerChangedState() {
        // turn off the screen if AppRTCAudioManager.AudioDevice.EARPIECE is active.
        Window window = cordova.getActivity().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        if (_audioManager.getSelectedAudioDevice() == AppRTCAudioManager.AudioDevice.EARPIECE) {
            params.screenBrightness = 0;
        } else {
            params.screenBrightness = -1;
        }
        window.setAttributes(params);
    }

    private static void abortUnless(boolean condition, String msg) {
        if (!condition) {
            throw new RuntimeException(msg);
        }
    }

    /**
     * Handles a new remote video track: sends event to the track manager and updates the UI
     */
    public void addRemoteVideoTrack(String streamId, VideoTrack videoTrack) {
        _trackManager.addRemoteVideoTrack(streamId, videoTrack);
        getVideoCallUi().refreshVideoView();
    }

    @Override
    public void onDestroy() {
        endCall();
    }

    /**
     * Ends the video call: cleans up resources
     */
    public void endCall() {
        Log.d("HidashHiPlugin", "endCall called");

        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                if (_peerConnectionFactory == null) {
                    // We are not in the call now, so return
                    return;
                }

                // Remove UI renderers
                _trackManager.removeRenderers();

                // Dispose Peer Connections
                for (RTCPeerConnection connection: _rtcPeerConnections.values()) {
                    connection.disconnect();
                }
                _rtcPeerConnections.clear();

                // Dispose audio manager
                if (_audioManager != null) {
                    _audioManager.close();
                    _audioManager = null;
                    Window window = cordova.getActivity().getWindow();
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }

                // Dispose video source
                _trackManager.disconnect();

                // Dispose peer connection factory
                _peerConnectionFactory.dispose();
                _peerConnectionFactory = null;

                getVideoCallUi().cleanup();
            }
        });
    }

    public TrackManager getTrackManager() {
        return _trackManager;
    }
}

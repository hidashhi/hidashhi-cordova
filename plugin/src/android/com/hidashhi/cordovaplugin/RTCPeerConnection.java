package com.hidashhi.cordovaplugin;

import android.app.Activity;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoTrack;

import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RTCPeerConnection
 * Wrapper over org.webrtc.PeerConnection object
 */
public class RTCPeerConnection {
    private final HidashHiPlugin _plugin;
    private PeerConnection _connection;
    MediaConstraints _pcMediaConstraints;
    private final SDPObserver _sdpObserver;
    private CallbackContext _callbackContext;
    private MediaStream _mediaStream;

    public RTCPeerConnection(PeerConnectionFactory connectionFactory,
                             JSONObject options, JSONObject pcConstraints, Activity activity,
                             CallbackContext callbackContext, HidashHiPlugin plugin) {
        _sdpObserver = new SDPObserver();
        _sdpObserver.setActivity(activity);
        _plugin = plugin;

        _callbackContext = callbackContext;

        // Initialize ICE server list
        final LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
        try {
            JSONArray servers = options.getJSONArray("iceServers");
            for (int i = 0; i < servers.length(); i++) {
                JSONObject server = servers.getJSONObject(i);
                String host = server.getString("host");
                String password = server.getString("password");
                String username = server.getString("username");
                iceServers.add(new PeerConnection.IceServer(host, username, password));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Initialize PeerConnection
        _pcMediaConstraints = new MediaConstraints();
        decodeMediaConstraints(pcConstraints, _pcMediaConstraints);

        PCObserver pcObserver = new PCObserver();
        pcObserver.setActivity(activity);
        _connection = connectionFactory.createPeerConnection(iceServers, _pcMediaConstraints,
                pcObserver);
    }

    /**
     * Converts media constraints JSON to MediaConstraints object
     */
    private void decodeMediaConstraints(JSONObject pcConstraints, MediaConstraints result) {
        try {
            if (pcConstraints.has("optional")) {
                JSONArray optional = pcConstraints.getJSONArray("optional");
                for (int i = 0; i < optional.length(); i++) {
                    JSONObject constraint = optional.getJSONObject(i);
                    decodeMediaConstraintsPart(constraint, result.optional);
                }
            }

            if (pcConstraints.has("mandatory")) {
                JSONObject mandatory = pcConstraints.getJSONObject("mandatory");
                decodeMediaConstraintsPart(mandatory, result.mandatory);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Decodes the part of media constraints (optional or mandatory) to the list of
     * MediaConstraints.KeyValuePair
     */
    private void decodeMediaConstraintsPart(JSONObject constraint,
                                        List<MediaConstraints.KeyValuePair> result)
            throws JSONException {

        Iterator<?> keys = constraint.keys();
        while( keys.hasNext() ){
            String key = (String)keys.next();
            String value = constraint.getString(key);
            result.add(new MediaConstraints.KeyValuePair(
                    key, value));
        }
    }

    /**
     * Adds local audio/video tracks as a local source for video and audio.
     */
    public void addStream(PeerConnectionFactory _connectionFactory,
                          AudioTrack localAudioTrack,
                          VideoTrack localVideoTrack) {
        if (_mediaStream != null) {
            removeStream();
        }

        // TODO should it be a singletone? Common for all connections?
        _mediaStream = _connectionFactory.createLocalMediaStream("ARDAMS");

        if (localAudioTrack != null) {
            _mediaStream.addTrack(localAudioTrack);
        }
        if (localVideoTrack != null) {
            _mediaStream.addTrack(localVideoTrack);
        }

        _connection.addStream(_mediaStream);
    }

    /**
     * Dispose local audio/video media streams.
     */
    private void removeStream() {
        _connection.removeStream(_mediaStream);

        for (VideoTrack videoTrack: _mediaStream.videoTracks) {
            _mediaStream.removeTrack(videoTrack);
        }

        for (AudioTrack audioTrack: _mediaStream.audioTracks) {
            _mediaStream.removeTrack(audioTrack);
        }

        _mediaStream.dispose();
        _mediaStream = null;
    }

    /**
     * Changes remote description associated with connection.
     * Just a wrapper to pass the data to underlying RTCPeerConnection object
     */
    public void setRemoteDescription(JSONObject sessionDescription) {
        try {
            String type = sessionDescription.getString("type");
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(type),
                    preferISAC(sessionDescription.getString("sdp")));

            _connection.setRemoteDescription(_sdpObserver, sdp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * IceCandidate event handler.
     * Just a wrapper to pass the data to underlying RTCPeerConnection object
     */
    public void addIceCandidate(JSONObject iceCandidate) {
        try {
            IceCandidate candidate = new IceCandidate(
                    iceCandidate.getString("id"), iceCandidate.getInt("label"),
                    iceCandidate.getString("candidate"));
            _connection.addIceCandidate(candidate);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Create an offer based on SDP constraints
     * Decodes SDP constraints from JSON and passes them to underlying RTCPeerConnection object
     */
    public void createOffer(JSONObject sdpConstraints, final CallbackContext context) {
        MediaConstraints pcMediaConstraints = new MediaConstraints();
        decodeMediaConstraints(sdpConstraints, pcMediaConstraints);
        _sdpObserver.setCallbackContext(context);
        _connection.createOffer(_sdpObserver, pcMediaConstraints);
    }

    public void createAnswer(CallbackContext callbackContext) {
        _sdpObserver.setCallbackContext(callbackContext);
        _connection.createAnswer(_sdpObserver, _pcMediaConstraints);
    }

    /**
     * Changes local description associated with the connection.
     */
    public void setLocalDescription(JSONObject offer) {
        try {
            String type = offer.getString("type");
            SessionDescription sdp = new SessionDescription(
                SessionDescription.Type.fromCanonicalForm(type),
                preferISAC(offer.getString("sdp")));
            _connection.setLocalDescription(_sdpObserver, sdp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    String preferISAC(String sdpDescription) {
        String[] lines = sdpDescription.split("\r?\n");
        int mLineIndex = -1;
        String isac16kRtpMap = null;
        Pattern isac16kPattern = Pattern
                .compile("^a=rtpmap:(\\d+) ISAC/16000[\r]?$");
        for (int i = 0; (i < lines.length)
                && (mLineIndex == -1 || isac16kRtpMap == null); ++i) {
            if (lines[i].startsWith("m=audio ")) {
                mLineIndex = i;
                continue;
            }
            Matcher isac16kMatcher = isac16kPattern.matcher(lines[i]);
            if (isac16kMatcher.matches()) {
                isac16kRtpMap = isac16kMatcher.group(1);
            }
        }
        if (mLineIndex == -1) {
            Log.d("com.hidashhi.cordovaplugin",
                    "No m=audio line, so can't prefer iSAC");
            return sdpDescription;
        }
        if (isac16kRtpMap == null) {
            Log.d("com.hidashhi.cordovaplugin",
                    "No ISAC/16000 line, so can't prefer iSAC");
            return sdpDescription;
        }
        String[] origMLineParts = lines[mLineIndex].split(" ");
        StringBuilder newMLine = new StringBuilder();
        int origPartIndex = 0;
        // Format is: m=<media> <port> <proto> <fmt> ...
        newMLine.append(origMLineParts[origPartIndex++]).append(" ");
        newMLine.append(origMLineParts[origPartIndex++]).append(" ");
        newMLine.append(origMLineParts[origPartIndex++]).append(" ");
        newMLine.append(isac16kRtpMap).append(" ");
        for (; origPartIndex < origMLineParts.length; ++origPartIndex) {
            if (!origMLineParts[origPartIndex].equals(isac16kRtpMap)) {
                newMLine.append(origMLineParts[origPartIndex]).append(" ");
            }
        }
        lines[mLineIndex] = newMLine.toString();
        StringBuilder newSdpDescription = new StringBuilder();
        for (String line : lines) {
            newSdpDescription.append(line).append("\r\n");
        }
        return newSdpDescription.toString();
    }

    /**
     * Send message to the plugin
     */
    void sendMessage(JSONObject data) {
        PluginResult result = new PluginResult(PluginResult.Status.OK, data);
        result.setKeepCallback(true);
        _callbackContext.sendPluginResult(result);
    }

    /**
     * Disconnects and cleans up the resources
     */
    public void disconnect() {
        removeStream();

        _connection.dispose();
        _connection = null;
    }

    private class PCObserver implements PeerConnection.Observer {

        private Activity _activity;

        @Override
        public void onIceCandidate(final IceCandidate iceCandidate) {
            _activity.runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        JSONObject json = new JSONObject();
                        json.put("type", "onicecandidate");
                        json.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                        json.put("sdpMid", iceCandidate.sdpMid);
                        json.put("candidate", iceCandidate.sdp);
                        // Call rtcPC.onicecandidate
                        sendMessage(json);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void onAddStream(final MediaStream stream) {
            _activity.runOnUiThread(new Runnable() {
                public void run() {
                    if (stream.videoTracks.size() > 0) {
                        VideoTrack videoTrack = stream.videoTracks.get(0);
                        String streamid = stream.toString();
                        // Rememeber stream id - videotrack pair
                        if (videoTrack != null) {
                            _plugin.addRemoteVideoTrack(streamid, videoTrack);

                            // Notify javscript connection callbacks
                            try {
                                JSONObject json = new JSONObject();
                                json.put("type", "onaddstream");
                                json.put("stream", streamid);
                                sendMessage(json);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
        }

        @Override
        public void onDataChannel(DataChannel stream) {
        }
        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState arg0) {
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState arg0) {
        }

        @Override
        public void onRemoveStream(MediaStream stream) {
        }

        @Override
        public void onRenegotiationNeeded() {
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        public void setActivity(Activity activity) {
            this._activity = activity;
        }
    }

    private class SDPObserver implements SdpObserver {
        private Activity _activity;
        private CallbackContext _callbackContext;

        @Override
        public void onCreateSuccess(final SessionDescription origSdp) {
            _activity.runOnUiThread(new Runnable() {
                public void run() {
                    SessionDescription sdp = new SessionDescription(
                            origSdp.type, preferISAC(origSdp.description));
                    try {
                        JSONObject json = new JSONObject();
                        json.put("type", sdp.type.canonicalForm());
                        json.put("sdp", sdp.description);
                        _callbackContext.success(json);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(final String error) {
            _activity.runOnUiThread(new Runnable() {
                public void run() {
                    throw new RuntimeException("createSDP error: " + error);
                }
            });
        }

        @Override
        public void onSetFailure(final String error) {
            _activity.runOnUiThread(new Runnable() {
                public void run() {
                    throw new RuntimeException("setSDP error: " + error);
                }
            });
        }

        public void setActivity(Activity activity) {
            _activity = activity;
        }

        public void setCallbackContext(CallbackContext callbackContext) {
            _callbackContext = callbackContext;
        }
    }
}


window.getUserMedia = function(mediaConstraints, cb, errCb) {

    function AudioTrack(){
        this.enabled = true;
    }

    function Stream() {
        var _videoEnabled = true;
        var _audioEnabled = true;

        this.videoTrack = {
            id: null, // Prevents video resolution to be detected by hidashhi library.
            label: null,
            set enabled(val) {
                _videoEnabled = val;
                cordova.exec(function(){},
                            function(err) {},
                            "HidashHiPlugin",
                            "VideoTrackEnabled",
                            [val]);
            },

            get enabled() {
                return _videoEnabled;
            }
        };

        this.audioTrack = {
            set enabled(val) {
                _audioEnabled = val;
                cordova.exec(function(){},
                            function(err) {},
                            "HidashHiPlugin",
                            "AudioTrackEnabled",
                            [val]);
            },

            get enabled() {
                return _audioEnabled;
            }
        };

        // This is a local video stream
        this.isLocal = true;
    }

    Stream.prototype.getVideoTracks = function() {
        return [this.videoTrack];
    };

    Stream.prototype.getAudioTracks = function() {
        return [this.audioTrack];
    },

    Stream.prototype.removeTrack = function(track) {
        console.log("===== stream.removeTrack");
    }

    cb(new Stream());
};

// Attaching video stream to the dom element
window.attachMediaStream = function(domElement, mediaStream) {
    console.log("===== attachMediaStream");
    if (mediaStream.isLocal) {
        // Show local video on the screen
        var params = {
            containerParams: domElement.getBoundingClientRect(),
            devicePixelRatio: window.devicePixelRatio || 2
        };
        cordova.exec(function(){},
                    function(err) {},
                    "HidashHiPlugin",
                    "attachMediaStream",
                    [params]);
    } else {
        // TODO handle remote video show here
    }

    domElement.addEventListener('DOMNodeRemovedFromDocument', function(e) {
        // The element has been removed.
        cordova.exec(function(){},
                        function(err) {},
                        "HidashHiPlugin",
                        "onVideoDomElementDeleted",
                        [mediaStream]);
    });

    return domElement;
};

window.createIceServer = function(host, username, password) {
    var iceServer = { host: host, username: "", password: ""};
    if (username) {
        iceServer.username = username;
    }
    if (password) {
        iceServer.password = password;
    }
    return iceServer;
};

window.RTCSessionDescription = function(data) {
    this.sdp = data.sdp;
    this.type = data.type;
}

window.RTCIceCandidate = function(data) {
    this.id = data.sdpMid;
    this.label = data.sdpMLineIndex;
    this.candidate = data.candidate;
};

window.RTCPeerConnection = function(options, pcConstraints) {
    function createUUID() {
        // http://www.ietf.org/rfc/rfc4122.txt
        var s = [];
        var hexDigits = "0123456789abcdef";
        for (var i = 0; i < 36; i++) {
          s[i] = hexDigits.substr(Math.floor(Math.random() * 0x10), 1);
        }
        s[14] = "4";  // bits 12-15 of the time_hi_and_version field to 0010
        // bits 6-7 of the clock_seq_hi_and_reserved to 01
        s[19] = hexDigits.substr((s[19] & 0x3) | 0x8, 1);
        s[8] = s[13] = s[18] = s[23] = "-";

        var uuid = s.join("");
        return uuid;
    }

    var callEvent = function(eventName) {
        if (!self.events[eventName]) {
          return;
        }

        var args = Array.prototype.slice.call(arguments, 1);
        self.events[eventName].forEach(function (callback) {
          callback.apply(self, args);
        });
    }

    var self = this;

    var onSendMessage = function(data) {
        if (data.type === 'onicecandidate') {
            delete data.type;
            self.onicecandidate({candidate: data});
        } else if (data.type === 'onaddstream') {
            delete data.type;
            self.onaddstream(data);
        } else if (data.type === 'endcall') {
            $hi.emit('call:endcall');
        } else {
            callEvent('sendMessage', data);
        }
    }

    this.onicecandidate = function(){};
    this.onaddstream = function(){};    // Never called, handled by plugin itself
    this.onremovestream = function(){}; // Never called, handled by plugin itself
    this.onconnection = function(){};   // TODO should be called from plugin

    this.peerConnectionKey = createUUID();
    cordova.exec(onSendMessage,
                function(err) {},
                "HidashHiPlugin",
                "RTCPeerConnection_create",
                [this.peerConnectionKey, options, pcConstraints]);
};

window.RTCPeerConnection.prototype.close = function() {};

window.RTCPeerConnection.prototype.createDataChannel = function(label, options) {
    return {
        send: function() { console.log("RTCPeerConnection datachannel send: not implemented."); }
    };
};

window.RTCPeerConnection.prototype.addStream = function(stream) {
    cordova.exec(function(){},
                function(err) {},
                "HidashHiPlugin",
                "RTCPeerConnection_addStream",
                [this.peerConnectionKey, stream]);
};

window.RTCPeerConnection.prototype.setRemoteDescription = function(sessionDescription) {
    cordova.exec(function(){},
                function(err) {},
                "HidashHiPlugin",
                "RTCPeerConnection_setRemoteDescription",
                [this.peerConnectionKey, sessionDescription]);
};

window.RTCPeerConnection.prototype.addIceCandidate = function(iceCandidate) {
    iceCandidate.type = "candidate"; // TODO is it needed?
    cordova.exec(function(){},
        function(err) {},
        "HidashHiPlugin",
        "RTCPeerConnection_addIceCandidate",
        [this.peerConnectionKey, iceCandidate]);
};

window.RTCPeerConnection.prototype.createOffer = function(cb, errCb, sdpConstraints) {
    cordova.exec(cb, errCb,
        "HidashHiPlugin",
        "RTCPeerConnection_createOffer",
        [this.peerConnectionKey, sdpConstraints]);

};

window.RTCPeerConnection.prototype.createAnswer = function(cb, errCb) {
    cordova.exec(cb, errCb,
            "HidashHiPlugin",
            "RTCPeerConnection_createAnswer",
            [this.peerConnectionKey]);
};

window.RTCPeerConnection.prototype.setLocalDescription = function(offer) {
    cordova.exec(function(){},
            function(err) {},
            "HidashHiPlugin",
            "RTCPeerConnection_setLocalDescription",
            [this.peerConnectionKey, offer]);
};

window.RTCPeerConnection.prototype.receiveMessage = function (data) {
  exec(null, null, 'PhoneRTCPlugin', 'receiveMessage', [{
    sessionKey: this.sessionKey,
    message: JSON.stringify(data)
  }]);
};

window.RTCPeerConnection.prototype.getLocalStreams = function () {
    return [];
};

window.addEventListener("orientationchange", function (e) {
    cordova.exec(function(){},
                function(err) {},
                "HidashHiPlugin",
                "orientationChanged",
                []);
}, true);


var Temasys = window.Temasys || {};
Temasys.WebRTCPlugin = Temasys.WebRTCPlugin || {};
Temasys.WebRTCPlugin.TemRTCPlugin = null;


/**
 * Position the window with video
 * @param x, y - x/y coordinates of window in percents (0..100)
 * @param width, height - width and height of the window in percents (0..100)
 */
var endCall = function(x, y, width, height) {
    cordova.exec(function(){},
        function(err) {},
        "HidashHiPlugin",
        "endCall",
        []);
};

// Exports
exports.endCall = endCall;

var mediaConstraints = {
    audio: true,
    video: true
};
var displayConstraints = {
    audio: true,
    video: {
        cursor: "always"
    }
};
var configuration = {
    'iceServers': [
        {
            'urls': 'stun:stun.l.google.com:19302'
        }
    ]
};
const micDisabledString = "&nbsp&nbsp<del style='color: black;'>🎤</del>";
const camDisabledString = "&nbsp&nbsp<del style='color: black;'>🎥</del>";

var id = window.localStorage.getItem("MyID");
if (id == null) {
    id = "a" + crypto.randomUUID().replaceAll("-", "");
    window.localStorage.setItem("MyID", id);
}
console.log("MyID: " + id);

var myName = window.localStorage.getItem("MyName");
if (myName == null) {
    myName = "anonyme";
    window.localStorage.setItem("MyName", "" + myName);
}
$("#NameText").html("" + myName);
console.log("MyName: " + myName);

function changeMyName(newName) {
    myName = newName;
    $("#MyVideo-text").html(newName);
    updateVideoPhoto(newName, "MyVideo");
    if(started) {
        wsCon.send(JSON.stringify({op: "name", from: id, to: null, room: myRoom, payload: newName}));
    }
}

var myRoom;
const urlParams = new URLSearchParams(window.location.search);
myRoom = urlParams.get('room');
if(myRoom == null) {
    myRoom = "public";
    window.history.replaceState(null, null, "?room=" + myRoom);
}
console.log("Room: " + myRoom);
$("#RoomText").html(myRoom);

var started = false;
var cameraList = [];
var microphoneList = [];
var speakerList = [];
var cameraIndex = -1, microphoneIndex = -1;
var sendingStream = null;
var myVideo;
var connected = false;
var connections = {};
var names = {};

var wsCon = new WebSocket(((window.location.protocol === "http:") ? "ws" : "wss") + "://" + window.location.host + "/webrtc");

function send(message) {
    wsCon.send(JSON.stringify(message));
}

function updateVideoPhoto(name, id) {
    let video = document.getElementById(id + "-video");
    let photo = document.getElementById(id + "-photo");
    if((video == null) || (photo == null)) {
        return;
    }
    let width = video.offsetWidth, height = video.offsetHeight;
    if(width === 0) {
        width = 100;
    }
    if(height === 0) {
        height = 100;
    }
    if(name.includes(camDisabledString)) {
        photo.style.width = width;
        photo.style.height = height;
        video.style.display = "none";
        photo.style.display = "block";
    } else {
        video.style.display = "block";
        photo.style.display = "none";
    }
}

function createVideo(stream, id, label) {
    let background = "#BBBBBB";
    let div = document.createElement('div');
    div.setAttribute("id", id);
    div.style.display = "inline-block";
    div.style.padding = "0";
    div.style.margin = "5px";
    div.style.background = background;
    //div.style.width = "50%";

    let video = document.createElement('video');
    video.setAttribute("id", id + "-video");
    video.autoplay = true;
    //video.controls = true;
    video.srcObject = stream;
    video.style.padding = "0";
    //video.style.width = "50px";
    video.style.height = "180px";
    video.onclick = function (event) {
        event.target.requestFullscreen();
    };
    div.appendChild(video);

    let userPhoto = document.createElement('img');
    userPhoto.setAttribute("id", id + "-photo");
    userPhoto.style.display = "none";
    userPhoto.style.margin = "auto";
    //userPhoto.style.maxHeight = video.style.maxHeight;
    userPhoto.style.background = background;
    userPhoto.src = "avatar.svg";
    div.appendChild(userPhoto);

    let textNode = document.createElement('div');
    textNode.setAttribute("id", id + "-text");
    textNode.innerHTML = label;
    textNode.style.background = background;
    textNode.style.color = "#FFFFFF";
    textNode.style.fontSize = "0.8em";
    textNode.style.padding = "2px";
    div.appendChild(textNode);

    document.getElementById("Videos").append(div);

    $("#" + id).hide();
    video.onloadeddata = function(e) {
        let divId = e.target.id.replaceAll("-video", "");
        $("#" + divId).fadeIn(fadeDelay);
        $("#" + divId + "-video").show();
        $("#" + divId + "-photo").hide();
    };

    return video;
}

wsCon.onmessage = function (msg) {
    let content = JSON.parse(msg.data);
    console.log("receiving " + content.op);
    let otherId = content.from;
    switch (content.op) {
        case "hi":
            if(otherId.localeCompare(id) > 0) {
                let con = new RTCPeerConnection(configuration);
                con.addEventListener("icecandidate", function (event) {
                    if (event.candidate) {
                        console.log("icecandidate event received!", event);
                        send({op: "candidate", room: myRoom, from: id, to: otherId, payload: JSON.stringify(event.candidate)});
                    }
                });
                con.addEventListener("addstream", function (event) {
                    console.log("addstream event received!", event);
                    createVideo(event.stream, "Video-" + otherId, names[otherId]);
                    updateVideoPhoto(names[otherId], "Video-" + otherId);
                });
                if(sendingStream != null) {
                    con.addStream(sendingStream);
                }
                con.createOffer(function (offer) {
                    con.setLocalDescription(offer);
                    console.log("sending offer");
                    offer["name"] = myName;
                    send({op: "offer", room: myRoom, from: id, to: otherId, payload: JSON.stringify(offer)});
                }, function (err) {
                    console.log("Error creating an offer");
                });
                connections[otherId] = con;
                names[otherId] = JSON.parse(content.payload).name;
                //console.log(peerConnections);
            } else {
                wsCon.send(JSON.stringify({op: "hi", from: id, to: otherId, room: myRoom, payload: JSON.stringify({name: myName})}));
            }
            break;
        case "offer":
            let con = new RTCPeerConnection(configuration);
            con.addEventListener("icecandidate", function (event) {
                if (event.candidate) {
                    console.log("icecandidate event received!", event);
                    send({op: "candidate", room: myRoom, from: id, to: otherId, payload: JSON.stringify(event.candidate)});
                }
            });
            con.addEventListener("addstream", function (event) {
                console.log("addstream event received!", event);
                createVideo(event.stream, "Video-" + otherId, names[otherId]);
                updateVideoPhoto(names[otherId], "Video-" + otherId);
            });
            con.addStream(sendingStream);
            con.setRemoteDescription(new RTCSessionDescription(JSON.parse(content.payload)));
            con.createAnswer(function (answer) {
                con.setLocalDescription(answer);
                send({op: "answer", room: myRoom, from: id, to: otherId, payload: JSON.stringify(answer)});
            }, function (err) {
                console.log("Error creating an answer");
            });
            connections[otherId] = con;
            names[otherId] = JSON.parse(content.payload).name;
            updateVideoPhoto(names[otherId], "Video-" + otherId);
            break;
        case "answer":
            connections[otherId].setRemoteDescription(new RTCSessionDescription(JSON.parse(content.payload)));
            break;
        case "candidate":
            connections[otherId].addIceCandidate(new RTCIceCandidate(JSON.parse(content.payload)));
            break;
        case "bye":
            if(content.to == null) {
                wsCon.send(JSON.stringify({op: "bye", from: id, to: otherId, room: myRoom}));
            }
            if(connections[otherId]) {
                connections[otherId].close();
            }
            delete(connections[otherId]);
            delete(names[otherId]);

            $("#Video-" + otherId).remove();
            break;
        case "name":
            names[otherId] = content.payload;
            $("#Video-" + otherId + "-text").html(content.payload);
            updateVideoPhoto(content.payload, "Video-" + otherId);
            break;
        case "message":
            $("#ChatArea").val($("#ChatArea").val() + content.payload + "\n");
            $("#ChatArea").scrollTop($("#ChatArea")[0].scrollHeight + 100);
            notify("primary", "New chat message received!", 5000);
            break;
        default:
            break;
    }
};

function sendHi() {
    if(!connected) {
        return;
    }
    started = true;
    wsCon.send(JSON.stringify({op: "hi", from: id, to: null, room: myRoom, payload: JSON.stringify({name: myName})}));
}

function sendMessage(msg) {
    if(!connected || !started) {
        return;
    }
    $("#ChatArea").val($("#ChatArea").val() + msg + "\n");
    $("#ChatArea").scrollTop($("#ChatArea")[0].scrollHeight + 100);
    wsCon.send(JSON.stringify({op: "message", from: id, to: null, room: myRoom, payload: msg}));
}

wsCon.onopen = function () {
    connected = true;
    console.log("connected!");
};

function enumerateDevices() {
    if(cameraList.length !== 0) {
        return;
    }
    navigator.mediaDevices.enumerateDevices().then(function(mediaDevices) {
        cameraList = mediaDevices.filter(function(x){
            return x.kind === "videoinput";
        });
        microphoneList = mediaDevices.filter(function(x){
            return x.kind === "audioinput";
        });
        speakerList = mediaDevices.filter(function(x){
            return x.kind === "audiooutput";
        });
    });
}

function initiateUserMedia() {
    stopRecording();
    $("#RecordButText").text("Record");
    if(sendingStream != null) {
        if(started) {
            wsCon.send(JSON.stringify({op: "bye", from: id, to: null, room: myRoom}));
        }

        sendingStream.getTracks().forEach(function(track) {
            if (track.readyState === 'live') {
                track.stop();
            }
        });

        let video = document.getElementById("MyVideo-video");
        let photo = document.getElementById("MyVideo-photo");
        photo.style.width = (video.offsetWidth);
        photo.style.height = (video.offsetHeight);
        video.style.display = "none";
        photo.style.display = "block";
        sendingStream = null;
    }

    mediaConstraints.video = (cameraIndex === -2) ? false : ((cameraIndex === -1) ? true : {deviceId: cameraList[cameraIndex].deviceId});
    mediaConstraints.audio = (microphoneIndex === -2) ? false : ((microphoneIndex === -1) ? true : {deviceId: microphoneList[microphoneIndex].deviceId});
    navigator.mediaDevices.getUserMedia(mediaConstraints).then(function (stream) {
        enumerateDevices();
        sendingStream = stream;

        $("#MyVideo").hide();
        let video = document.getElementById("MyVideo-video");
        if(video) {
            video.srcObject = stream;
            $("#MyVideo-photo").hide();
            $("#MyVideo-video").show();
        } else {
            let video = createVideo(stream, "MyVideo", "Me");
            video.muted = true;
        }

        if(started) {
            sendHi();
        }
    }).catch(function (err) {
        notify("primary", "Could not get camera stream!", 5000);
    });
}

function initiateDisplayMedia() {
    stopRecording();
    $("#RecordButText").text("Record");
    navigator.mediaDevices.getDisplayMedia(displayConstraints).then(function (stream) {
        wsCon.send(JSON.stringify({op: "bye", from: id, to: null, room: myRoom}));

        sendingStream.getVideoTracks().forEach(function(track) {
            if (track.readyState === 'live') {
                track.stop();
            }
        });

        stream.addTrack(sendingStream.getAudioTracks()[0]);
        sendingStream = stream;

        $("#MyVideo").hide();
        let video = document.getElementById("MyVideo-video");
        if(video) {
            video.srcObject = stream;
        } else {
            let video = createVideo(stream, "MyVideo", "Me");
            video.muted = true;
        }

        stream.getVideoTracks()[0].addEventListener('ended', () => {
            initiateUserMedia();
        });

        if(started) {
            sendHi();
        }
    }).catch(function (err) {
        notify("primary", "Could not get display stream!", 5000);
    });
}

initiateUserMedia();


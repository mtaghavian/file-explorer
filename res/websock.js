var reconnectModal = document.createElement('div');
reconnectModal.setAttribute("id", "reconnect-modal");
reconnectModal.setAttribute("uk-modal", "true");
reconnectModal.setAttribute("bg-close", "false");
reconnectModal.setAttribute("esc-close", "false");
var container = document.createElement('div');
container.classList.add("uk-modal-dialog");
container.classList.add("uk-margin-auto-vertical");
container.classList.add("Centered");
container.style.padding = "20px";
reconnectModal.append(container);
var reconnectTimer = document.createElement('div');
reconnectTimer.setAttribute("id", "reconnect-modal-timer");
reconnectTimer.style.paddingBottom = "20px";
container.append(reconnectTimer);
var tryBut = document.createElement('div');
tryBut.setAttribute("id", "TryNow");
tryBut.setAttribute("type", "button");
tryBut.classList.add("uk-button");
tryBut.classList.add("uk-button-primary");
tryBut.style.float = "right";
tryBut.append(document.createTextNode("Try now"));
container.append(tryBut);
document.body.append(reconnectModal);
var reconnectionTimerMax = 20;
var reconnectionTimer = 0;
var wsUrl = ((window.location.protocol === "http:") ? "ws" : "wss") + "://" + window.location.host + "/ws";

var startWebSocket = function () {
    var onOpen = function () {
        console.log("Socket opened!");
        UIkit.modal($("#reconnect-modal")).hide();
    };
    var onClose = function () {
        console.log("Socket closed!");
        reconnectionTimer = reconnectionTimerMax;
        UIkit.modal($("#reconnect-modal")).show();
    };
    var wsocketInterval = null;
    $("#TryNow").click(function () {
        connect();
    });
    var connect = function () {
        var brokenWS = (wsCon == null || wsCon.readyState !== 1);
        if (brokenWS) {
            console.log("Websocket connecting...")

            wsCon = new WebSocket(wsUrl);
            wsCon.onopen = onOpen;
            wsCon.onclose = onClose;
            wsCon.onmessage = function (msg) {
                let data = JSON.parse(msg.data);
                let type = data.type;
                let op = data.op;
                let list = data.list;
                if(type === "Unauthorised") {
                    window.location.href = "/";
                } else if(type === "Ping") {
                } else {
                    console.log("Websocket msg received!");
                    console.log(data);
                    if (type === "Contact") {
                        if(onContact) {
                            onContact(data);
                        }
                    } else if (type === "Message") {
                        if(onChat) {
                            onChat(data);
                        } else if(onMain) {
                            onMain(data);
                        }
                    } else if (type === "Call") {
                        if(onCall) {
                            onCall(data);
                        } else if (op !== "Delete") {
                            parameters.isCallee = true;
                            parameters.otherId = list[0].otherId;
                            load("/call.html");
                        }
                    } else if (type === "Main") {
                        if(onMain) {
                            onMain(data);
                        }
                    }
                }
            };
            if(wsocketInterval == null) {
                wsocketInterval = setInterval(function () {
                    var brokenWS = (wsCon == null || wsCon.readyState !== 1);
                    if (brokenWS) {
                        $("#reconnect-modal-timer").text("Connection Lost! Reconnecting in " + reconnectionTimer + " seconds...");
                    }
                    if(reconnectionTimer > 0) {
                        reconnectionTimer--;
                        return;
                    }
                    reconnectionTimer = reconnectionTimerMax;
                    if (brokenWS) {
                        connect();
                    } else {
                        wsCon.send(JSON.stringify({type: "Ping"}));
                    }
                }, 1000);
            }
        }
    };
    connect();
}
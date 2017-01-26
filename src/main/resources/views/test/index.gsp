<!DOCTYPE html>
<html>
<head>
    <title>Francis test page</title>
</head>
<body>
<b>Francis test page</b>
<script>
    var handlers = {};
    var debug = true;

    var apps = [];
    var classes = {};

    var wsEvents = {
        onOpen: () => {
        },
        onClose: () => {
        }
    };

    handlers.listApps = (data) => {
        log(data.list);
        apps = data.list;
    };
    handlers.listClasses = (data) => {
        log(data.list);
        classes[data.appName] = data.list;
    };

    function log(message) {
        if (debug)
            console.log(message)
    }

    function connect() {
        var https = location.protocol === 'https:';
        var port = location.port || (https ? 443 : 80);
        ws = new WebSocket((https ? 'wss' : 'ws') + "://" + location.hostname + ":" + port + '/ws/server');
        ws.onopen = function () {
            log("open");
            if (wsEvents.onOpen)
                wsEvents.onOpen();
        };
        ws.onmessage = function (e) {
//            log(e.data);
            var data = JSON.parse(e.data);
            var handler = handlers[data.command];
            if (handler)
                handler(data);
            else
                log("unknown command: " + data.command);
        };
        ws.onclose = function () {
            log("closed");
            if (wsEvents.onClose)
                wsEvents.onClose();
            connect();
        };
    }

    connect();
</script>
</body>
</html>
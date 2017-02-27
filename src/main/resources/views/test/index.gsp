<!DOCTYPE html>
<head>
    <title>Francis test page</title>
    <g:resource dir="js" file="material.js"/>
    <g:resource dir="css" file="material.css"/>
</head>
<body>
<b>Francis test page</b>

<br>

<div class="mdl-textfield mdl-js-textfield mdl-textfield--floating-label">
    <input class="mdl-textfield__input" type="text" id="search_class" onkeyup="searchListener(this, event)"
           onfocus="showSuggestions()" onblur="hideSuggestions()"
    >
    <label class="mdl-textfield__label" for="search_class">Class search</label>

    <ul class="mdl-menu mdl-menu--bottom-right mdl-js-menu mdl-js-ripple-effect" id="search_suggestions">
        <li class="mdl-menu__item" data-n="1">
            option 1
        </li>
        <li class="mdl-menu__item" data-n="2">
            option 2
        </li>
        <li class="mdl-menu__item" data-n="3">
            option 3
        </li>
    </ul>
</div>

<script>
    var selection = 0;
    var searchSuggestionsElement = document.getElementById('search_suggestions');

    function searchListener(input, e) {
        console.log(e)
        var options = ''
        var value = input.value;
        let length = value.length;
        for (var i = 0; i < length; i++) {
            options += `
    <li class="mdl-menu__item" data-n="${'${i}'}">
        option ${'${value[i]}'}
    </li>`
        }
        searchSuggestionsElement.innerHTML = options;
        searchSuggestionsElement.style.clip = `rect(0px 300px ${'${length * 48 + 16}'}px 0px)`;
        searchSuggestionsElement.previousElementSibling.style.height = (length * 48 + 16) + 'px';
        if (searchSuggestionsElement.childElementCount == 0){
            return;
        }

        searchSuggestionsElement.children[selection].style.backgroundColor = 'transparent';
        if (e.code == 'ArrowDown' && selection < searchSuggestionsElement.childElementCount - 1) {
            selection++;
        } else if (e.code == 'ArrowUp' && selection > 0) {
            selection--;
        } else if (e.code == 'Enter') {
            //
        }
        searchSuggestionsElement.children[selection].style.backgroundColor = '#eeeeee';
    }

    function showSuggestions() {
        var width = '300px';
        searchSuggestionsElement.parentNode.classList.add('is-visible');
        searchSuggestionsElement.parentNode.style.left = width;
        searchSuggestionsElement.previousElementSibling.style.width = width;
        searchSuggestionsElement.previousElementSibling.style.left = '-'+width;
        searchSuggestionsElement.style.width = width;
    }
    function hideSuggestions() {
        searchSuggestionsElement.parentNode.classList.remove('is-visible');
    }
</script>

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
    handlers.listMethods = (data) => {
        log(data.list);
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
            if (e.error) {
                log(e.error.exceptionClass + ": " + e.error.message);
                log(e.error.stacktrace);
                log(e.error.case);
            }

            var data = JSON.parse(e.data);
            var handler = handlers[data.command];
            if (handler)
                handler(data);
            else {
                log("unknown command: " + data.command);
                log(data)
            }
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
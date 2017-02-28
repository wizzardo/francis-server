<!DOCTYPE html>
<head>
    <title>Francis test page</title>
    <g:resource dir="js" file="material.js"/>
    <g:resource dir="css" file="material.css"/>
    <style>
        #search_suggestions li.mdl-menu__item {
            height: 24px;
            line-height: 24px;
        }
    </style>
</head>
<body>
<b>Francis test page</b>

<br>

<div class="mdl-textfield mdl-js-textfield mdl-textfield--floating-label">
    <input class="mdl-textfield__input" type="text" id="search_class"
           onkeydown="searchControls(this, event)"
           onfocus="showSuggestions()"
           onblur="hideSuggestions()"
    >
    <label class="mdl-textfield__label" for="search_class">Class search</label>

    <ul class="mdl-menu mdl-menu--bottom-right mdl-js-menu mdl-js-ripple-effect" id="search_suggestions">
    </ul>
</div>

<script>
    var selection = -1;
    var searchSuggestionsElement = document.getElementById('search_suggestions');

    function searchControls(input, e) {
//        console.log(e)
//        console.log(input.value)
        var newSelection = selection;
        if (e.code == 'ArrowDown') {
            if (selection < searchSuggestionsElement.childElementCount - 1)
                newSelection++;
        } else if (e.code == 'ArrowUp') {
            if (selection > 0)
                newSelection--;
        } else if (e.code == 'Enter') {
            //
        } else if (e.key.length == 1) {
            searchClasses(input.value + e.key);
        } else if (e.key == 'Backspace' && input.value.length > 1) {
            searchClasses(input.value.substr(0, input.value.length - 1));
        }

        if (newSelection != selection) {
            if (selection >= 0)
                searchSuggestionsElement.children[selection].style.backgroundColor = 'transparent';
            selection = newSelection;
            searchSuggestionsElement.children[selection].style.backgroundColor = '#eeeeee';
            e.preventDefault()
        }
    }

    function handleSearchResults(list) {
        var options = '';
        var value = list;
        var length = value.length;
        for (var i = 0; i < length; i++) {
            options += `
    <li class="mdl-menu__item" data-n="${'${i}'}">
        ${'${value[i]}'}
    </li>`
        }
        searchSuggestionsElement.innerHTML = options;
        searchSuggestionsElement.style.clip = `rect(0px 300px ${'${length * 24 + 16}'}px 0px)`;
        searchSuggestionsElement.previousElementSibling.style.height = (length * 24 + 16) + 'px';
        setTimeout(() => {
            if (selection == -1)
                selection = 0;
            if (selection >= searchSuggestionsElement.childElementCount) {
                selection = searchSuggestionsElement.childElementCount - 1;
            }
            if (selection >= 0)
                searchSuggestionsElement.children[selection].style.backgroundColor = '#eeeeee';
        }, 1);
    }

    function showSuggestions() {
        var width = '300px';
        searchSuggestionsElement.parentNode.classList.add('is-visible');
        searchSuggestionsElement.parentNode.style.left = width;
        searchSuggestionsElement.previousElementSibling.style.width = width;
        searchSuggestionsElement.previousElementSibling.style.left = '-' + width;
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
    handlers.hello = (data) => {
        ws.send('{command: loadClasses, appName: "francis-server"}')
    };
    handlers.searchClasses = (data) => {
        log(data);
        handleSearchResults(data.list);
    };

    function searchClasses(target) {
        ws.send(JSON.stringify({command: 'searchClasses', appName: "francis-server", target: target}))
    }

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
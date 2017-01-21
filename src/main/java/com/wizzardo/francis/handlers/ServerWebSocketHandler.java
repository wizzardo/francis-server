package com.wizzardo.francis.handlers;

import com.wizzardo.http.framework.di.PostConstruct;
import com.wizzardo.http.websocket.DefaultWebSocketHandler;
import com.wizzardo.http.websocket.Message;
import com.wizzardo.tools.json.JsonObject;
import com.wizzardo.tools.json.JsonTools;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by wizzardo on 07/01/17.
 */
public class ServerWebSocketHandler extends DefaultWebSocketHandler implements PostConstruct {
    ClientWebSocketHandler<ClientWebSocketHandler.ClientWebSocketListener> clientsHandler;
    protected Map<String, CommandHandler> handlers = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "serverWebSocketHandler";
    }

    @Override
    public void onConnect(WebSocketListener listener) {
        super.onConnect(listener);
        listener.sendMessage(new Message("{\"command\":\"hello\"}"));
    }

    @Override
    public void onMessage(WebSocketListener listener, Message message) {
        String s = message.asString();
        System.out.println("on message: " + s);
        JsonObject json = JsonTools.parse(s).asJsonObject();
        CommandHandler handler = handlers.get(json.getAsString("command"));
        if (handler != null)
            handler.handle(listener, json);
        else
            System.out.println("unknown command: " + json.getAsString("command"));
    }

    @Override
    public void init() {
        handlers.put("listApps", (listener, json) -> {
            Set<String> apps = clientsHandler.connections()
                    .map(it -> it.params.get("appName"))
                    .collect(Collectors.toSet());

            JsonObject response = new JsonObject()
                    .append("command", "listApps")
                    .append("list", apps)
                    ;

            listener.sendMessage(new Message(response.toString()));
        });
    }

    protected interface CommandHandler {
        void handle(WebSocketListener listener, JsonObject json);
    }
}

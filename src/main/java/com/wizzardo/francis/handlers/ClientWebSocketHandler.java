package com.wizzardo.francis.handlers;

import com.wizzardo.http.HttpConnection;
import com.wizzardo.http.framework.di.PostConstruct;
import com.wizzardo.http.websocket.DefaultWebSocketHandler;
import com.wizzardo.http.websocket.Message;
import com.wizzardo.http.websocket.WebSocketHandler;
import com.wizzardo.tools.json.JsonObject;
import com.wizzardo.tools.json.JsonTools;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Created by wizzardo on 07/01/17.
 */
public class ClientWebSocketHandler<T extends ClientWebSocketHandler.ClientWebSocketListener> extends DefaultWebSocketHandler<T> implements PostConstruct {
    protected Map<String, CommandHandler> handlers = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "clientWebSocketHandler";
    }

    @Override
    public void onConnect(T listener) {
        super.onConnect(listener);
        listener.sendMessage(new Message("{\"command\":\"hello\"}"));
    }

    @Override
    public void onMessage(T listener, Message message) {
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
        handlers.put("setParameters", (listener, json) -> {
            JsonObject params = json.getAsJsonObject("params");
            params.forEach((k, v) -> listener.params.put(k, v.asString()));
        });
    }

    public Stream<T> connections() {
        return listeners.stream();
    }

    public static class ClientWebSocketListener extends WebSocketListener {
        final Map<String, String> params = new ConcurrentHashMap<>();

        public ClientWebSocketListener(HttpConnection connection, WebSocketHandler webSocketHandler) {
            super(connection, webSocketHandler);
        }
    }

    @Override
    protected T createListener(HttpConnection connection, WebSocketHandler handler) {
        return (T) new ClientWebSocketListener(connection, handler);
    }

    protected interface CommandHandler {
        void handle(ClientWebSocketListener listener, JsonObject json);
    }
}

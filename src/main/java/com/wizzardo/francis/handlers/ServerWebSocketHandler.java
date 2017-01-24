package com.wizzardo.francis.handlers;

import com.wizzardo.http.framework.di.PostConstruct;
import com.wizzardo.http.websocket.DefaultWebSocketHandler;
import com.wizzardo.http.websocket.Message;
import com.wizzardo.tools.cache.Cache;
import com.wizzardo.tools.json.JsonArray;
import com.wizzardo.tools.json.JsonObject;
import com.wizzardo.tools.json.JsonTools;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by wizzardo on 07/01/17.
 */
public class ServerWebSocketHandler extends DefaultWebSocketHandler implements PostConstruct {
    ClientWebSocketHandler<ClientWebSocketHandler.ClientWebSocketListener> clientsHandler;
    Map<String, CommandHandler> handlers = new ConcurrentHashMap<>();
    Cache<Integer, Callback> callbacks = new Cache<>("callbacks", 60);
    AtomicInteger callbackCounter = new AtomicInteger();

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
                    .append("list", apps);

            send(listener, response);
        });

        handlers.put("listClasses", (listener, json) -> {
            String appName = json.getAsString("appName");
            Optional<ClientWebSocketHandler.ClientWebSocketListener> first = clientsHandler.connections()
                    .filter(it -> {
                        return it.params.get("appName").equals(appName);
                    })
                    .findFirst();

            JsonObject response = new JsonObject()
                    .append("command", "listClasses")
                    .append("appName", appName);

            if (!first.isPresent()) {
                send(listener, response.append("list", new JsonArray()));
            } else {
                Integer id = putCallback(data -> {
                    send(listener, response.append("list", data.getAsJsonArray("list")));
                });
                ClientWebSocketHandler.ClientWebSocketListener client = first.get();
                clientsHandler.getClasses(client, id);
            }
        });
    }

    protected Integer putCallback(Callback callback) {
        int i = callbackCounter.incrementAndGet();
        callbacks.put(i, callback);
        return i;
    }

    protected void executeCallback(Integer id, JsonObject json) {
        Callback callback = callbacks.remove(id);
        if (callback == null) {
            System.out.println("unknown callback id: " + id);
        } else {
            try {
                callback.execute(json);
            } catch (Exception e) {
                System.out.println("callback (" + id + ") execution failed");
                e.printStackTrace();
            }
        }
    }

    public void send(WebSocketListener listener, JsonObject json) {
        listener.sendMessage(new Message(json.toString()));
    }

    protected interface CommandHandler {
        void handle(WebSocketListener listener, JsonObject json);
    }

    interface Callback {
        void execute(JsonObject json);
    }
}

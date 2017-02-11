package com.wizzardo.francis.handlers;

import com.wizzardo.francis.domain.Transformation;
import com.wizzardo.francis.services.DataService;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by wizzardo on 07/01/17.
 */
public class ControlWebSocketHandler extends DefaultWebSocketHandler implements PostConstruct {
    ClientWebSocketHandler<ClientWebSocketHandler.ClientWebSocketListener> clientsHandler;
    Map<String, CommandHandler> handlers = new ConcurrentHashMap<>();
    Cache<Integer, Callback> callbacks = new Cache<>("callbacks", 60);
    AtomicInteger callbackCounter = new AtomicInteger();
    DataService dataService;

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
            Optional<ClientWebSocketHandler.ClientWebSocketListener> first = findClient(appName);

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

        handlers.put("listMethods", (listener, json) -> {
            String appName = json.getAsString("appName");
            String clazz = json.getAsString("class");
            Optional<ClientWebSocketHandler.ClientWebSocketListener> first = findClient(appName);

            JsonObject response = new JsonObject()
                    .append("command", "listMethods")
                    .append("class", clazz)
                    .append("appName", appName);

            if (!first.isPresent()) {
                send(listener, response.append("list", new JsonArray()));
            } else {
                Integer id = putCallback(data -> {
                    if (data.get("error") == null)
                        send(listener, response.append("list", data.getAsJsonArray("list")));
                    else
                        send(listener, response
                                .append("list", data.getAsJsonArray("list"))
                                .append("error", data.get("error"))
                                .append("message", data.get("message"))
                                .append("stacktrace", data.get("stacktrace"))
                        );
                });
                ClientWebSocketHandler.ClientWebSocketListener client = first.get();
                clientsHandler.getMethods(client, clazz, id);
            }
        });

        handlers.put("addTransformation", (listener, json) -> {
            String appName = json.getAsString("appName");

            Long applicationId = dataService.findApplicationId(appName);
            Transformation t = new Transformation();
            t.applicationId = applicationId;
            t.className = json.getAsString("className");
            t.method = json.getAsString("method");
            t.methodDescriptor = json.getAsString("methodDescriptor");
            t.before = json.getAsString("before");
            t.after = json.getAsString("after");
            t.variables = json.getAsJsonArray("variables").toString();
            dataService.saveTransformation(t);

            findAllClients(it -> it.applicationId.equals(applicationId))
                    .forEach(it -> clientsHandler.addTransformation(it, t));
        });
    }

    protected Optional<ClientWebSocketHandler.ClientWebSocketListener> findClient(String appName) {
        return clientsHandler.connections()
                .filter(it -> appName.equals(it.params.get("appName")))
                .findFirst();
    }

    protected Stream<ClientWebSocketHandler.ClientWebSocketListener> findAllClients(Predicate<ClientWebSocketHandler.ClientWebSocketListener> filter) {
        return clientsHandler.connections().filter(filter);
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

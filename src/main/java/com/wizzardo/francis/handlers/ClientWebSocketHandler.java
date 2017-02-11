package com.wizzardo.francis.handlers;

import com.wizzardo.francis.domain.Transformation;
import com.wizzardo.francis.services.DataService;
import com.wizzardo.http.HttpConnection;
import com.wizzardo.http.framework.di.PostConstruct;
import com.wizzardo.http.websocket.DefaultWebSocketHandler;
import com.wizzardo.http.websocket.Message;
import com.wizzardo.http.websocket.WebSocketHandler;
import com.wizzardo.tools.json.JsonObject;
import com.wizzardo.tools.json.JsonTools;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static com.wizzardo.tools.misc.With.with;

/**
 * Created by wizzardo on 07/01/17.
 */
public class ClientWebSocketHandler<T extends ClientWebSocketHandler.ClientWebSocketListener> extends DefaultWebSocketHandler<T> implements PostConstruct {
    ControlWebSocketHandler controlWebSocketHandler;
    Map<String, CommandHandler> handlers = new ConcurrentHashMap<>();
    DataService dataService;

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

            Long appId = dataService.saveApplicationIfNot(listener.params.get("appName"));
            listener.applicationId = appId;
            listener.instanceId = dataService.saveInstanceIfNot(appId, listener.params);
        });
        handlers.put("listClasses", (listener, json) -> {
            controlWebSocketHandler.executeCallback(json.getAsInteger("callbackId"), json);
        });
        handlers.put("listMethods", (listener, json) -> {
            controlWebSocketHandler.executeCallback(json.getAsInteger("callbackId"), json);
        });
        handlers.put("getTransformations", (listener, json) -> {
            List<Transformation> transformations = dataService.findAllTransformationsByApplicationId(listener.applicationId);
            send(listener, with(new ListTransformationsCommandResponse(), it -> {
                it.command = "getTransformationsResponse";
                it.list = transformations;
            }));
        });
    }

    public Stream<T> connections() {
        return listeners.stream();
    }

    public void getClasses(T client, Integer callbackId) {
        send(client, new JsonObject()
                .append("command", "listClasses")
                .append("callbackId", callbackId)
        );
    }


    public void getMethods(T client, String clazz, Integer callbackId) {
        send(client, new JsonObject()
                .append("command", "listMethods")
                .append("callbackId", callbackId)
                .append("class", clazz)
        );
    }

    public void send(WebSocketListener listener, JsonObject json) {
        listener.sendMessage(new Message(json.toString()));
    }

    public void send(WebSocketListener listener, CommandResponse response) {
        listener.sendMessage(new Message(JsonTools.serialize(response)));
    }

    public void addTransformation(T client, Transformation transformation) {
        send(client, new JsonObject()
                .append("command", "addTransformation")
                .append("id", transformation.id)
                .append("className", transformation.className)
                .append("method", transformation.method)
                .append("methodDescriptor", transformation.methodDescriptor)
                .append("before", transformation.before)
                .append("after", transformation.after)
                .append("variables", transformation.variables)
        );
    }

    public void deleteTransformation(T client, Transformation transformation) {
        send(client, new JsonObject()
                .append("command", "deleteTransformation")
                .append("id", transformation.id)
                .append("className", transformation.className)
        );
    }

    public static class ClientWebSocketListener extends WebSocketListener {
        final Map<String, String> params = new ConcurrentHashMap<>();
        public Long applicationId;
        public Long instanceId;

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

    static class CommandResponse {
        String command;
    }

    static class ListTransformationsCommandResponse extends CommandResponse {
        List<Transformation> list;
    }
}

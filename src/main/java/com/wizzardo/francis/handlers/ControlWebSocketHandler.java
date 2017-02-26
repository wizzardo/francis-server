package com.wizzardo.francis.handlers;

import com.wizzardo.francis.domain.Transformation;
import com.wizzardo.francis.services.ClassesService;
import com.wizzardo.francis.services.DataService;
import com.wizzardo.http.framework.di.PostConstruct;
import com.wizzardo.http.websocket.DefaultWebSocketHandler;
import com.wizzardo.http.websocket.Message;
import com.wizzardo.tools.cache.Cache;
import com.wizzardo.tools.json.JsonArray;
import com.wizzardo.tools.json.JsonItem;
import com.wizzardo.tools.json.JsonObject;
import com.wizzardo.tools.json.JsonTools;
import com.wizzardo.tools.misc.Unchecked;

import java.io.FileWriter;
import java.util.*;
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
    ClassesService classesService;

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
        if (handler != null) {
            try {
                handler.handle(listener, json);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            send(listener, new JsonObject()
                    .append("error", "unknown command: " + json.getAsString("command"))
                    .append("command", "help")
                    .append("commands", new JsonArray()
                            .appendAll(handlers.keySet())
                    )
            );
            System.out.println("unknown command: " + json.getAsString("command"));
        }
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
            if (appName == null) {
                sendCommandHelp(listener, "listClasses", "appName");
                return;
            }

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

        handlers.put("loadClasses", (listener, json) -> {
            String appName = json.getAsString("appName");
            if (appName == null) {
                sendCommandHelp(listener, "loadClasses", "appName");
                return;
            }

            Optional<ClientWebSocketHandler.ClientWebSocketListener> first = findClient(appName);

            JsonObject response = new JsonObject()
                    .append("command", "loadClasses")
                    .append("appName", appName);

            if (!first.isPresent()) {
                send(listener, response);
            } else {
                Integer id = putCallback(data -> {
                    classesService.load(appName, data.getAsJsonArray("list").stream().map(JsonItem::asString));
                    send(listener, response.append("count", classesService.countClasses(appName)));
                });
                ClientWebSocketHandler.ClientWebSocketListener client = first.get();
                clientsHandler.getClasses(client, id);
            }
        });

        handlers.put("areClassesLoaded", (listener, json) -> {
            String appName = json.getAsString("appName");
            if (appName == null) {
                sendCommandHelp(listener, "areClassesLoaded", "appName");
                return;
            }

            JsonObject response = new JsonObject()
                    .append("command", "areClassesLoaded")
                    .append("loaded", classesService.isReady(appName))
                    .append("count", classesService.countClasses(appName))
                    .append("appName", appName);

            send(listener, response);
        });

        handlers.put("searchClasses", (listener, json) -> {
            String appName = json.getAsString("appName");
            String target = json.getAsString("target");
            int limit = json.getAsInteger("limit", 25);
            if (appName == null || target == null) {
                sendCommandHelp(listener, "searchClasses", "appName", "target", "limit");
                return;
            }

            JsonObject response = new JsonObject()
                    .append("command", "searchClasses")
                    .append("appName", appName);

            List<ClassesService.ClassInfo> list = classesService.search(target, limit, appName);
            send(listener, response.append("list", new JsonArray().appendAll(
                    list.stream()
                            .map(classInfo -> classInfo.name)
                            .collect(Collectors.toList())
            )));
        });

        handlers.put("saveClasses", (listener, json) -> {
            String appName = json.getAsString("appName");
            String file = json.getAsString("file");
            if (appName == null || file == null) {
                sendCommandHelp(listener, "saveClasses", "appName", "file");
                return;
            }

            Optional<ClientWebSocketHandler.ClientWebSocketListener> first = findClient(appName);

            JsonObject response = new JsonObject()
                    .append("command", "listClasses")
                    .append("appName", appName);

            if (!first.isPresent()) {
                send(listener, response.append("list", new JsonArray()));
            } else {
                Integer id = putCallback(data -> Unchecked.run(() -> {
                    FileWriter fileWriter = new FileWriter(file);
                    for (JsonItem item : data.getAsJsonArray("list")) {
                        fileWriter.write(item.asString());
                        fileWriter.write("\n");
                    }
                    fileWriter.close();
                }));
                ClientWebSocketHandler.ClientWebSocketListener client = first.get();
                clientsHandler.getClasses(client, id);
            }
        });

        handlers.put("listMethods", (listener, json) -> {
            String appName = json.getAsString("appName");
            String clazz = json.getAsString("class");
            if (appName == null || clazz == null) {
                sendCommandHelp(listener, "listMethods", "appName", "class");
                return;
            }

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

            if (appName == null || t.className == null || t.method == null || t.methodDescriptor == null || t.before == null || t.after == null || t.variables == null) {
                sendCommandHelp(listener, "addTransformation", "appName", "className", "method", "methodDescriptor", "before", "after", "variables");
                return;
            }

            dataService.saveTransformation(t);

            findAllClients(it -> it.applicationId.equals(applicationId))
                    .forEach(it -> clientsHandler.addTransformation(it, t));
        });

        handlers.put("updateTransformation", (listener, json) -> {
            String appName = json.getAsString("appName");

            Long applicationId = dataService.findApplicationId(appName);
            Transformation t = new Transformation();
            t.id = json.getAsLong("id", -1l);
            t.applicationId = applicationId;
            t.className = json.getAsString("className");
            t.method = json.getAsString("method");
            t.methodDescriptor = json.getAsString("methodDescriptor");
            t.before = json.getAsString("before");
            t.after = json.getAsString("after");
            t.variables = json.getAsJsonArray("variables").toString();

            if (appName == null || t.id == -1 || t.className == null || t.method == null || t.methodDescriptor == null || t.before == null || t.after == null || t.variables == null) {
                sendCommandHelp(listener, "updateTransformation", "id", "appName", "className", "method", "methodDescriptor", "before", "after", "variables");
                return;
            }

            if (!dataService.updateTransformation(t))
                throw new IllegalArgumentException("Transformation " + t.id + " wasn't updated");

            findAllClients(it -> it.applicationId.equals(applicationId))
                    .forEach(it -> clientsHandler.addTransformation(it, t));
        });

        handlers.put("listTransformations", (listener, json) -> {
            String appName = json.getAsString("appName");
            if (appName == null) {
                sendCommandHelp(listener, "listTransformations", "appName");
                return;
            }


            Long applicationId = dataService.findApplicationId(appName);
            List<Transformation> transformations = dataService.findAllTransformationsByApplicationId(applicationId);

            send(listener, new ListTransformationsResponse(transformations));
        });

        handlers.put("deleteTransformation", (listener, json) -> {
            Long id = json.getAsLong("id");
            if (id == null) {
                sendCommandHelp(listener, "deleteTransformation", "id");
                return;
            }

            Transformation t = dataService.getTransformation(id);
            if (!dataService.deleteTransformation(id))
                throw new IllegalArgumentException("Transformation " + id + " wasn't deleted");

            findAllClients(it -> it.applicationId.equals(t.applicationId))
                    .forEach(it -> clientsHandler.deleteTransformation(it, t));
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

    public void sendCommandHelp(WebSocketListener listener, String command, String... args) {
        send(listener, new JsonObject()
                .append("command", "help")
                .append("forCommand", command)
                .append("args", new JsonArray()
                        .appendAll(Arrays.asList(args))
                )
        );
    }

    public void send(WebSocketListener listener, JsonObject json) {
        listener.sendMessage(new Message(json.toString()));
    }

    public void send(WebSocketListener listener, CommandResponse response) {
        listener.sendMessage(new Message(JsonTools.serialize(response)));
    }

    protected interface CommandHandler {
        void handle(WebSocketListener listener, JsonObject json);
    }

    interface Callback {
        void execute(JsonObject json);
    }

    static class CommandResponse {
        String command;
    }

    static class ListTransformationsResponse extends CommandResponse {
        List<Transformation> list;

        public ListTransformationsResponse(List<Transformation> transformations) {
            command = "listTransformations";
            list = transformations;
        }
    }
}

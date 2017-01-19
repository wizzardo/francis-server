package com.wizzardo.francis.handlers;

import com.wizzardo.http.websocket.DefaultWebSocketHandler;
import com.wizzardo.http.websocket.Message;

/**
 * Created by wizzardo on 07/01/17.
 */
public class ServerWebSocketHandler extends DefaultWebSocketHandler {
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
        System.out.println(message.asString());
    }
}

package com.wizzardo.francis;

import com.wizzardo.francis.handlers.*;
import com.wizzardo.francis.controllers.*;
import com.wizzardo.http.framework.WebApplication;

/**
 * Created by wizzardo on 24/01/17.
 */
public class App {

    public static void main(String[] args) {
        WebApplication webApplication = new WebApplication(args);
        webApplication.onSetup(app -> {
            app.getUrlMapping()
                    .append("/ui/test", TestController.class, "index")
                    .append("/ws/client", ClientWebSocketHandler.class)
                    .append("/ws/server", ControlWebSocketHandler.class);
        });

        webApplication.start();
    }
}

package com.wizzardo.francis;

import com.wizzardo.francis.controllers.TestController;
import com.wizzardo.francis.handlers.ClientWebSocketHandler;
import com.wizzardo.francis.handlers.ControlWebSocketHandler;
import com.wizzardo.http.framework.WebApplication;

/**
 * Created by wizzardo on 24/01/17.
 */
public class App extends WebApplication {
    WebApplication webApplication;

    public App(WebApplication webApplication) {
        this.webApplication = webApplication;
        webApplication.onSetup(app -> {
            app.getUrlMapping()
                    .append("/ui/test", TestController.class, "index")
                    .append("/ws/client", ClientWebSocketHandler.class)
                    .append("/ws/server", ControlWebSocketHandler.class)
            ;
        });
    }

    public static void main(String[] args) {
        WebApplication webApplication = new WebApplication(args);
        new App(webApplication);
        webApplication.start();
    }
}

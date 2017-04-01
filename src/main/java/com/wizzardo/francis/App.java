package com.wizzardo.francis;

import com.wizzardo.francis.controllers.TestController;
import com.wizzardo.francis.handlers.ClientWebSocketHandler;
import com.wizzardo.francis.handlers.ControlWebSocketHandler;
import com.wizzardo.francis.services.DBService;
import com.wizzardo.http.framework.WebApplication;
import com.wizzardo.http.framework.di.DependencyFactory;

/**
 * Created by wizzardo on 24/01/17.
 */
public class App {

    public static void main(String[] args) {
        WebApplication webApplication = new WebApplication(args);
        webApplication.onSetup(app -> {
            DependencyFactory.get(DBService.class);

            app.getUrlMapping()
                    .append("/ui/test", TestController.class, "index")
                    .append("/ws/client", ClientWebSocketHandler.class)
                    .append("/ws/server", ControlWebSocketHandler.class)
            ;
        });

        webApplication.start();
    }
}

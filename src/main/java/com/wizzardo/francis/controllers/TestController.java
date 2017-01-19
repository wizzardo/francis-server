package com.wizzardo.francis.controllers;

import com.wizzardo.http.framework.Controller;
import com.wizzardo.http.framework.template.Renderer;

/**
 * Created by wizzardo on 19/01/17.
 */
public class TestController extends Controller {

    public Renderer index() {
        return renderView();
    }
}

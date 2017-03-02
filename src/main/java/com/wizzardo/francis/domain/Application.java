package com.wizzardo.francis.domain;

/**
 * Created by wizzardo on 04/03/17.
 */
public class Application {
    public Long id;
    public String name;

    @Override
    public String toString() {
        return id + ": " + name;
    }
}

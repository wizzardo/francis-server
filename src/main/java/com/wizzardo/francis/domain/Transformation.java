package com.wizzardo.francis.domain;

import java.util.Date;

/**
 * Created by wizzardo on 09/02/17.
 */
public class Transformation {

    public long id;
    public long applicationId;
    public long version;
    public Date lastUpdated;
    public Date dateCreated;
    public String className;
    public String method;
    public String methodDescriptor;
    public String before;
    public String after;
    public String variables;

}

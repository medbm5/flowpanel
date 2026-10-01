package com.flowpanel.common;

import org.springframework.http.HttpStatus;

/** 404. Also used for resources outside the caller's tenant or supplier scope, so existence never leaks. */
public class NotFoundException extends ApiException {

    public NotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "Not found", resource + " " + id + " was not found", null);
    }
}

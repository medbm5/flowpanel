package com.flowpanel.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

    public BadRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Bad request", detail, null);
    }

    public BadRequestException(String detail, Map<String, Object> properties) {
        super(HttpStatus.BAD_REQUEST, "Bad request", detail, properties);
    }
}

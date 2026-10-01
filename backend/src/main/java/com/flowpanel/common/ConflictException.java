package com.flowpanel.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String detail) {
        this(detail, null);
    }

    public ConflictException(String detail, Map<String, Object> properties) {
        super(HttpStatus.CONFLICT, "Conflict", detail, properties);
    }
}

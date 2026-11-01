package com.raims.exception;

import com.google.gson.JsonObject;
import com.raims.ApiResponse;

public class NotFoundException extends ApiException {
    private static final long serialVersionUID = 1L;
    public NotFoundException(String message) { super(message); }
    public NotFoundException(String message, ApiResponse response) { super(message, response); }
    public NotFoundException(String message, ApiResponse response, JsonObject error) { super(message, response, error); }
}

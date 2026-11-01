package com.raims.exception;

import com.google.gson.JsonObject;
import com.raims.ApiResponse;

public class ConflictException extends ApiException {
    private static final long serialVersionUID = 1L;
    public ConflictException(String message) { super(message); }
    public ConflictException(String message, ApiResponse response) { super(message, response); }
    public ConflictException(String message, ApiResponse response, JsonObject error) { super(message, response, error); }
}

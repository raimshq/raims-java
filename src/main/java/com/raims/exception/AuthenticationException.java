package com.raims.exception;

import com.google.gson.JsonObject;
import com.raims.ApiResponse;

public class AuthenticationException extends ApiException {
    private static final long serialVersionUID = 1L;
    public AuthenticationException(String message) { super(message); }
    public AuthenticationException(String message, ApiResponse response) { super(message, response); }
    public AuthenticationException(String message, ApiResponse response, JsonObject error) { super(message, response, error); }
}

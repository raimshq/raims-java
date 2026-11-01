package com.raims.exception;

import com.google.gson.JsonObject;
import com.raims.ApiResponse;

public class RateLimitException extends ApiException {
    private static final long serialVersionUID = 1L;
    public RateLimitException(String message) { super(message); }
    public RateLimitException(String message, ApiResponse response) { super(message, response); }
    public RateLimitException(String message, ApiResponse response, JsonObject error) { super(message, response, error); }
}

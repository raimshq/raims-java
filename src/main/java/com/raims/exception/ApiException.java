package com.raims.exception;

import com.google.gson.JsonObject;
import com.raims.ApiResponse;

public class ApiException extends RaimsException {
    private static final long serialVersionUID = 1L;
    public ApiException(String message) { super(message); }
    public ApiException(String message, ApiResponse response) { super(message, response); }
    public ApiException(String message, ApiResponse response, JsonObject error) { super(message, response, error); }
}

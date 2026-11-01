package com.raims.exception;

import com.google.gson.JsonObject;
import com.raims.ApiResponse;

public class InvalidRequestException extends ApiException {
    private static final long serialVersionUID = 1L;
    public InvalidRequestException(String message) { super(message); }
    public InvalidRequestException(String message, ApiResponse response) { super(message, response); }
    public InvalidRequestException(String message, ApiResponse response, JsonObject error) { super(message, response, error); }
}

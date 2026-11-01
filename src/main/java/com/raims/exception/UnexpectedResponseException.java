package com.raims.exception;

import com.google.gson.JsonObject;
import com.raims.ApiResponse;

public class UnexpectedResponseException extends RaimsException {
    private static final long serialVersionUID = 1L;
    public UnexpectedResponseException(String message) { super(message); }
    public UnexpectedResponseException(String message, ApiResponse response) { super(message, response); }
    public UnexpectedResponseException(String message, ApiResponse response, JsonObject error) { super(message, response, error); }
}

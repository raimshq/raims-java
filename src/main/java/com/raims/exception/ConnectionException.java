package com.raims.exception;

import com.google.gson.JsonObject;
import com.raims.ApiResponse;

public class ConnectionException extends RaimsException {
    private static final long serialVersionUID = 1L;
    public ConnectionException(String message) { super(message); }
    public ConnectionException(String message, ApiResponse response) { super(message, response); }
    public ConnectionException(String message, ApiResponse response, JsonObject error) { super(message, response, error); }
}

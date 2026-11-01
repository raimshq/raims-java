package com.raims.exception;

import com.google.gson.JsonObject;
import com.raims.ApiResponse;

public class PermissionException extends ApiException {
    private static final long serialVersionUID = 1L;
    public PermissionException(String message) { super(message); }
    public PermissionException(String message, ApiResponse response) { super(message, response); }
    public PermissionException(String message, ApiResponse response, JsonObject error) { super(message, response, error); }
}

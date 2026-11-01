package com.raims.exception;

import com.google.gson.JsonObject;
import com.raims.ApiResponse;

public class SignatureVerificationException extends RaimsException {
    private static final long serialVersionUID = 1L;
    public SignatureVerificationException(String message) { super(message); }
    public SignatureVerificationException(String message, ApiResponse response) { super(message, response); }
    public SignatureVerificationException(String message, ApiResponse response, JsonObject error) { super(message, response, error); }
}

package com.hookledger.service;

public class InvoiceException extends RuntimeException {

    public InvoiceException(String message) {
        super(message);
    }

    public InvoiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

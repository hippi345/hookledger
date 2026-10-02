package com.hookledger.service;

public class InvalidMoneyEventException extends RuntimeException {

    public InvalidMoneyEventException(String message) {
        super(message);
    }

    public InvalidMoneyEventException(String message, Throwable cause) {
        super(message, cause);
    }
}

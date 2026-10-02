package com.hookledger.service;

public class PeriodClosedException extends RuntimeException {

    public PeriodClosedException(String message) {
        super(message);
    }
}

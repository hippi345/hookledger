package com.hookledger.service;

import java.util.regex.Pattern;

public final class MoneyEventValidation {

    public static final Pattern EVENT_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$");
    public static final Pattern CURRENCY = Pattern.compile("^[A-Za-z]{3}$");

    private MoneyEventValidation() {}
}

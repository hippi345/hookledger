package com.hookledger.domain;

public final class InvoiceSalesTax {

    private InvoiceSalesTax() {}

    public static long taxMinor(long amountMinor, int taxRateBasisPoints) {
        if (taxRateBasisPoints <= 0) {
            return 0;
        }
        long product = amountMinor * (long) taxRateBasisPoints;
        long quotient = product / 10_000;
        long remainder = product % 10_000;
        if (remainder == 0) {
            return quotient;
        }
        if (product > 0) {
            return remainder >= 5_000 ? quotient + 1 : quotient;
        }
        return remainder <= -5_000 ? quotient - 1 : quotient;
    }

    public static long lineTotalMinor(long amountMinor, int taxRateBasisPoints) {
        return amountMinor + taxMinor(amountMinor, taxRateBasisPoints);
    }
}

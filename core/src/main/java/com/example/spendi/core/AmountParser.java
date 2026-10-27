package com.example.spendi.core;

import java.math.BigDecimal;

/** Parses positive, two-decimal amounts into minor units without rounding.
 * This contract is for Spendi's current USD amounts, not arbitrary currencies.
 * Comma and Arabic decimal separator mean a decimal point; grouping is unsupported.
 */
public final class AmountParser {
    private AmountParser() { }

    public enum Error { REQUIRED, INVALID, NOT_POSITIVE, PRECISION, TOO_LARGE }

    public static final class InvalidAmountException extends IllegalArgumentException {
        private final Error error;

        private InvalidAmountException(Error error) {
            super(error.name());
            this.error = error;
        }

        public Error error() {
            return error;
        }
    }

    public static long parsePositiveMinor(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new InvalidAmountException(Error.REQUIRED);
        }
        StringBuilder normalized = new StringBuilder();
        for (char character : input.trim().toCharArray()) {
            int digit = Character.digit(character, 10);
            if (digit >= 0) {
                normalized.append(digit);
            } else if (character == '\u066B' || character == ',') {
                normalized.append('.');
            } else {
                normalized.append(character);
            }
        }
        String value = normalized.toString();
        if (!value.matches("[0-9]+(\\.[0-9]+)?")) {
            throw new InvalidAmountException(Error.INVALID);
        }
        int decimal = value.indexOf('.');
        if (decimal >= 0 && value.length() - decimal - 1 > 2) {
            throw new InvalidAmountException(Error.PRECISION);
        }
        BigDecimal amount = new BigDecimal(value);
        if (amount.signum() <= 0) {
            throw new InvalidAmountException(Error.NOT_POSITIVE);
        }
        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new InvalidAmountException(Error.TOO_LARGE);
        }
    }
}

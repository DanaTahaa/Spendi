package com.example.spendi.core;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Arrays;
import java.util.Collection;
import static com.example.spendi.core.AmountParser.Error.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(Parameterized.class)
public class InvalidAmountTest {
    @Parameterized.Parameters(name = "{index}: {0} -> {1}")
    public static Collection<Object[]> amounts() {
        return Arrays.asList(new Object[][] {
                {null, REQUIRED}, {"", REQUIRED}, {"   ", REQUIRED},
                {"0", NOT_POSITIVE}, {"٠٫٠٠", NOT_POSITIVE},
                {"-1", INVALID}, {"+1", INVALID}, {".5", INVALID},
                {"1.", INVALID}, {"1e2", INVALID}, {"NaN", INVALID},
                {"1 000", INVALID}, {"1,000.00", INVALID}, {"١٬٠٠٠", INVALID},
                {"1.234", PRECISION}, {"1,000", PRECISION}, {"0.001", PRECISION},
                {"92233720368547758.08", TOO_LARGE},
                {"999999999999999999999999999999", TOO_LARGE}
        });
    }

    private final String input;
    private final AmountParser.Error expected;

    public InvalidAmountTest(String input, AmountParser.Error expected) {
        this.input = input;
        this.expected = expected;
    }

    @Test public void rejectsInvalidInputWithSpecificError() {
        AmountParser.InvalidAmountException exception = assertThrows(
                AmountParser.InvalidAmountException.class,
                () -> AmountParser.parsePositiveMinor(input));
        assertEquals(expected, exception.error());
    }
}

package com.example.spendi.core;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Arrays;
import java.util.Collection;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class AmountParserTest {
    @Parameterized.Parameters(name = "{index}: {0} -> {1}")
    public static Collection<Object[]> amounts() {
        return Arrays.asList(new Object[][] {
                {"25", 2500L}, {"25.5", 2550L}, {"25.50", 2550L},
                {" 25.50 ", 2550L}, {"0.01", 1L}, {"0001.20", 120L},
                {"٢٥٫٥٠", 2550L}, {"۲۵٫۵۰", 2550L}, {"25,50", 2550L},
                {"92233720368547758.07", Long.MAX_VALUE}
        });
    }

    private final String input;
    private final long expected;

    public AmountParserTest(String input, long expected) {
        this.input = input;
        this.expected = expected;
    }

    @Test public void convertsExactlyToMinorUnits() {
        assertEquals(expected, AmountParser.parsePositiveMinor(input));
    }
}

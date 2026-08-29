package de.danoeh.antennapod.net.sync.statesync;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record FractionalIndex(String value) implements Comparable<FractionalIndex> {
    private static final BigInteger MAXIMUM = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    public FractionalIndex {
        if (value == null || !value.matches("[0-9a-f]{16}")) {
            throw new IllegalArgumentException("Invalid fractional index");
        }
        BigInteger numeric = new BigInteger(value, 16);
        if (numeric.signum() <= 0) {
            throw new IllegalArgumentException("Invalid fractional index");
        }
    }

    public static FractionalIndex between(FractionalIndex lower, FractionalIndex upper) {
        BigInteger lowerValue = lower == null ? BigInteger.ZERO : lower.numericValue();
        BigInteger upperValue = upper == null ? MAXIMUM : upper.numericValue();
        BigInteger difference = upperValue.subtract(lowerValue);
        if (difference.signum() <= 0) {
            throw new IllegalArgumentException("Invalid fractional-index bounds");
        }
        if (difference.compareTo(BigInteger.ONE) <= 0) {
            throw new IllegalStateException("Fractional-index space exhausted");
        }
        return fromNumeric(lowerValue.add(difference.divide(BigInteger.valueOf(2))));
    }

    public static List<FractionalIndex> rebase(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count must not be negative");
        }
        if (count == 0) {
            return List.of();
        }
        BigInteger step = MAXIMUM.divide(BigInteger.valueOf((long) count + 1));
        if (step.signum() <= 0) {
            throw new IllegalStateException("Fractional-index space exhausted");
        }
        List<FractionalIndex> result = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            result.add(fromNumeric(step.multiply(BigInteger.valueOf(index))));
        }
        return result;
    }

    @Override
    public int compareTo(FractionalIndex other) {
        return value.compareTo(other.value);
    }

    private BigInteger numericValue() {
        return new BigInteger(value, 16);
    }

    private static FractionalIndex fromNumeric(BigInteger numeric) {
        return new FractionalIndex(String.format(Locale.US, "%016x", numeric));
    }
}

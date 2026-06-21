package com.garage.billing.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

// ════════════════════════════════════════════════════════════
// Converts a rupee amount into Indian-English words.
// Example: 3459.00 → "Three Thousand Four Hundred Fifty Nine
//                     Rupees Only"
// Uses the Indian numbering system (Lakh, Crore) — NOT the
// Western system (Million, Billion) — because this is an
// Indian rupee invoice.
// ════════════════════════════════════════════════════════════
public class AmountInWordsConverter {

    private static final String[] ONES = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven",
        "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen",
        "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };

    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty",
        "Sixty", "Seventy", "Eighty", "Ninety"
    };

    // ── PUBLIC: Convert amount to words ──────────────────────
    public static String convert(BigDecimal amount) {
        if (amount == null) return "Zero Rupees Only";

        // Round to nearest rupee — paise are not spoken on
        // Indian invoices' "amount in words" line.
        long rupees = amount.setScale(0, RoundingMode.HALF_UP).longValue();

        if (rupees == 0) return "Zero Rupees Only";

        String words = convertToIndianWords(rupees);
        return words + " Rupees Only";
    }

    // ── PRIVATE: Indian numbering system breakdown ───────────
    // Crore (10,000,000) → Lakh (100,000) → Thousand (1,000) → Hundred
    private static String convertToIndianWords(long number) {
        if (number == 0) return "";

        StringBuilder result = new StringBuilder();

        long crore = number / 10000000;
        number %= 10000000;

        long lakh = number / 100000;
        number %= 100000;

        long thousand = number / 1000;
        number %= 1000;

        long hundred = number / 100;
        number %= 100;

        if (crore > 0) {
            result.append(convertTwoDigit(crore)).append(" Crore ");
        }
        if (lakh > 0) {
            result.append(convertTwoDigit(lakh)).append(" Lakh ");
        }
        if (thousand > 0) {
            result.append(convertTwoDigit(thousand)).append(" Thousand ");
        }
        if (hundred > 0) {
            result.append(ONES[(int) hundred]).append(" Hundred ");
        }
        if (number > 0) {
            result.append(convertTwoDigit(number));
        }

        return result.toString().trim();
    }

    // ── PRIVATE: Convert a number 0-99 to words ──────────────
    private static String convertTwoDigit(long number) {
        if (number < 20) {
            return ONES[(int) number];
        }
        int tensDigit = (int) (number / 10);
        int onesDigit = (int) (number % 10);
        return TENS[tensDigit]
            + (onesDigit > 0 ? " " + ONES[onesDigit] : "");
    }
}
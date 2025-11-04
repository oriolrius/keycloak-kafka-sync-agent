package com.miimetiq.keycloak.sync.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for masking sensitive data in logs.
 * Prevents passwords, secrets, and other sensitive information from being exposed in logs.
 */
public class SensitiveDataMasker {

    private static final String MASK = "***";

    // Patterns to detect and mask sensitive information
    private static final Pattern[] SENSITIVE_PATTERNS = {
        // Password in various formats
        Pattern.compile("(password[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,}]+)", Pattern.CASE_INSENSITIVE),
        // Secret in various formats
        Pattern.compile("(secret[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,}]+)", Pattern.CASE_INSENSITIVE),
        // Token in various formats
        Pattern.compile("(token[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,}]+)", Pattern.CASE_INSENSITIVE),
        // API key in various formats
        Pattern.compile("(api[_-]?key[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,}]+)", Pattern.CASE_INSENSITIVE),
        // Basic auth credentials (username:password format)
        Pattern.compile("([a-zA-Z0-9_-]+):([^@\\s,}]+)@"),
        // JAAS configuration containing passwords
        Pattern.compile("(password\\s*=\\s*[\"']?)([^\"'\\s;]+)", Pattern.CASE_INSENSITIVE)
    };

    private SensitiveDataMasker() {
        // Utility class
    }

    /**
     * Masks sensitive data in the given string.
     *
     * @param input the string potentially containing sensitive data
     * @return the string with sensitive data masked
     */
    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String result = input;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            Matcher matcher = pattern.matcher(result);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                // Keep the prefix (e.g., "password=") but mask the value
                String prefix = matcher.group(1);
                String replacement = prefix + MASK;
                // If there's a third group (like @ in username:password@), keep it
                if (matcher.groupCount() > 2) {
                    replacement += matcher.group(3);
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }

        return result;
    }

    /**
     * Creates a masked string representation of a configuration value.
     * Useful for logging configuration without exposing sensitive data.
     *
     * @param value the value to mask if sensitive
     * @param isSensitive whether the value is known to be sensitive
     * @return the original value or a masked version
     */
    public static String maskIfSensitive(String value, boolean isSensitive) {
        if (value == null) {
            return null;
        }

        if (isSensitive) {
            // Show only first 2 and last 2 characters if long enough
            if (value.length() <= 4) {
                return MASK;
            }
            return value.substring(0, 2) + MASK + value.substring(value.length() - 2);
        }

        return value;
    }

    /**
     * Masks a password completely.
     *
     * @param password the password to mask
     * @return the masked password
     */
    public static String maskPassword(String password) {
        return password != null ? MASK : null;
    }
}

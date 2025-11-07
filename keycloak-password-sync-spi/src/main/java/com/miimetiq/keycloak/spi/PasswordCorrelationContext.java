package com.miimetiq.keycloak.spi;

/**
 * ThreadLocal storage for correlating password data between SPIs.
 *
 * This context allows the PasswordHashProvider to store plaintext passwords
 * in a ThreadLocal, which can then be retrieved by the EventListener in the
 * same request thread before Keycloak completes the hashing process.
 *
 * Security: Passwords expire after 5 seconds and are automatically cleared
 * after retrieval to prevent memory leaks.
 */
public class PasswordCorrelationContext {

    private static final ThreadLocal<PasswordData> CURRENT_PASSWORD = new ThreadLocal<>();
    private static final long DEFAULT_MAX_AGE_MS = 5000; // 5 seconds

    /**
     * Internal class to store password with timestamp for expiration.
     */
    public static class PasswordData {
        private final String password;
        private final long timestamp;

        public PasswordData(String password) {
            this.password = password;
            this.timestamp = System.currentTimeMillis();
        }

        public String getPassword() {
            return password;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isExpired(long maxAgeMs) {
            return (System.currentTimeMillis() - timestamp) > maxAgeMs;
        }
    }

    /**
     * Store a password in the current thread's context.
     *
     * @param password the plaintext password to store
     */
    public static void setPassword(String password) {
        if (password != null && !password.isEmpty()) {
            CURRENT_PASSWORD.set(new PasswordData(password));
        }
    }

    /**
     * Retrieve and clear the password from the current thread's context.
     *
     * @return the stored password, or null if not found or expired
     */
    public static String getAndClearPassword() {
        return getAndClearPassword(DEFAULT_MAX_AGE_MS);
    }

    /**
     * Retrieve and clear the password from the current thread's context.
     *
     * @param maxAgeMs maximum age in milliseconds before password expires
     * @return the stored password, or null if not found or expired
     */
    public static String getAndClearPassword(long maxAgeMs) {
        PasswordData data = CURRENT_PASSWORD.get();
        CURRENT_PASSWORD.remove(); // Always clear after retrieval

        if (data == null) {
            return null;
        }

        if (data.isExpired(maxAgeMs)) {
            return null; // Password expired
        }

        return data.getPassword();
    }

    /**
     * Clear any stored password from the current thread's context.
     */
    public static void clear() {
        CURRENT_PASSWORD.remove();
    }
}

package com.example.sysarch.sync.polarion;

/** A Polarion REST call failed. {@code status} is the HTTP status, or 0 if the call did not get a response. */
public class PolarionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    public PolarionException(int status, String message) {
        super(message);
        this.status = status;
    }

    public PolarionException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    public int status() {
        return this.status;
    }

    /** Polarion kept answering 429 after every retry; {@code retryAfterSeconds} is its last Retry-After. */
    public static final class RateLimited extends PolarionException {

        private static final long serialVersionUID = 1L;

        private final long retryAfterSeconds;

        public RateLimited(long retryAfterSeconds) {
            super(429, "Polarion rate limit: still 429 after retries (Retry-After " + retryAfterSeconds + " s)");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return this.retryAfterSeconds;
        }
    }
}

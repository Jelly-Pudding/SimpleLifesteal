package com.jellypudding.simpleLifesteal.utils;

public class BanCheckResult {
    private final String status;
    private final long timestamp;

    public BanCheckResult(String status) {
        this.status = status;
        this.timestamp = System.currentTimeMillis();
    }

    public String getStatus() {
        return status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isExpired(long timeoutMillis) {
        return (System.currentTimeMillis() - this.timestamp) > timeoutMillis;
    }
}

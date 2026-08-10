package com.terra.gis.api;

public class CancellationToken {

    private volatile boolean cancelled;

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}

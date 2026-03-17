package com.pointofdata.podos.config;

/**
 * Configuration for the connection pool.
 * Mirrors Go's {@code PoolConfig} struct.
 */
public class PoolConfig {
    /** Maximum number of connections in the pool. 0 = no pool. */
    public int maxCapacity = 0;
    /** Number of connections to create when initializing the pool. */
    public int initialCapacity = 0;

    public PoolConfig() {}

    public PoolConfig(int maxCapacity, int initialCapacity) {
        this.maxCapacity = maxCapacity;
        this.initialCapacity = initialCapacity;
    }
}

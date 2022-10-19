package ok.dht.test.scheredin;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CircuitBreaker {

    private final int failureThreshold;
    private final int retryTimeoutMilliseconds;
    private volatile State state = State.OPEN;
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile int failureCount = 0;
    private volatile long lastFailureTime = 0;

    public CircuitBreaker(int failureThreshold, int retryTimeoutMilliseconds) {
        this.failureThreshold = failureThreshold;
        this.retryTimeoutMilliseconds = retryTimeoutMilliseconds;
    }

    public void recordFail() {
        lock.writeLock().lock();
        try {
            if (++failureCount > failureThreshold) {
                lastFailureTime = System.currentTimeMillis();
                state = State.CLOSED;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void recordSuccess() {
        lock.writeLock().lock();
        try {
            failureCount = 0;
            state = State.OPEN;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public State getState() {
        if (state.equals(State.OPEN)) {
            return state;
        }
        lock.readLock().lock();
        try {
            if (state == State.CLOSED && (System.currentTimeMillis() - lastFailureTime) > retryTimeoutMilliseconds) {
                state = State.HALF_OPEN;
            }
        } finally {
            lock.readLock().unlock();
        }
        return state;
    }

    public enum State {
        OPEN, HALF_OPEN, CLOSED
    }
}

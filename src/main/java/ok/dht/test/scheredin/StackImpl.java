package ok.dht.test.scheredin;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

public class StackImpl<T> extends LinkedBlockingDeque<T> {
    private final LinkedBlockingDeque<T> queue;

    public StackImpl(final int capacity) {
        queue = new LinkedBlockingDeque<T>(capacity);
    }

    @Override
    public T poll() {
        return super.pollLast();
    }

    @Override
    public T take() throws InterruptedException {
        return super.takeLast();
    }
}

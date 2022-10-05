package ok.dht.test.scheredin;

import java.util.concurrent.LinkedBlockingDeque;

public class StackImpl<T> extends LinkedBlockingDeque<T> {

    public StackImpl(int capacity) {
        super(capacity);
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

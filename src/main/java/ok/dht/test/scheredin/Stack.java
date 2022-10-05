package ok.dht.test.scheredin;

import java.util.concurrent.LinkedBlockingDeque;

public class Stack<T> {
    private final LinkedBlockingDeque<T> queue;

    public Stack(final int capacity) {
        queue = new LinkedBlockingDeque<T>(capacity);
    }

    public void push(T object) {
        queue.push(object);
    }

    public T pop() throws InterruptedException {
      return queue.takeLast();
    }
}

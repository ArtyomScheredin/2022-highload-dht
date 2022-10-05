package ok.dht.test.scheredin;

import javax.annotation.Nonnull;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

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

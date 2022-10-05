package ok.dht.test.scheredin;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MultiThreadedServer extends HttpServer {
    private static final int QUEUE_CAPACITY = 64;
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

    private final Queue queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final Stack stack = new Stack<>(QUEUE_CAPACITY);
    private final ExecutorService executorService = new ThreadPoolExecutor(
            AVAILABLE_PROCESSORS,
            AVAILABLE_PROCESSORS,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY));

    public MultiThreadedServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public synchronized void stop() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Failed to shutdown executor service");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        for (SelectorThread selectorThread : selectors) {
            for (Session session : selectorThread.selector) {
                session.scheduleClose();
            }
        }
        super.stop();
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        executorService.execute(() -> {
            try {
                super.handleRequest(request, session);
            } catch (IOException e) {
                sendError(session, Response.BAD_REQUEST, e.getMessage());
            } catch (RejectedExecutionException e) {
                sendError(session, Response.SERVICE_UNAVAILABLE, e.getMessage());
            }
        });
    }

    private static void sendError(HttpSession session, String serviceUnavailable, String message) {
        try {
            session.sendError(serviceUnavailable, message);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }
}

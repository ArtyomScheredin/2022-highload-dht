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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MultiThreadedServer extends HttpServer {
    private static final int QUEUE_CAPACITY = 32;
    private static final int EXECUTORS_COUNT = 8;

    private final StackImpl queue = new StackImpl<Runnable>(QUEUE_CAPACITY);
    private final ExecutorService executorService = new ThreadPoolExecutor(
            EXECUTORS_COUNT,
            EXECUTORS_COUNT,
            0L,
            TimeUnit.MILLISECONDS,
            queue,
            new ThreadPoolExecutor.AbortPolicy());

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
       try {
            executorService.execute(() -> {
                handleRequestAndCatchExceptions(request, session);
            });
        } catch (RejectedExecutionException e) {
            sendError(session, Response.BAD_REQUEST, e.getMessage());
}
    }

    private void handleRequestAndCatchExceptions(Request request, HttpSession session) {
        try {
            super.handleRequest(request, session);
        } catch (IOException e) {
            sendError(session, Response.BAD_REQUEST, e.getMessage());
        }
    }

    private static void sendError(HttpSession session, String code, String message) {
        try {
            session.sendError(code, message);
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

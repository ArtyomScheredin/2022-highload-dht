package ok.dht.test.scheredin;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.scheredin.dao.BaseEntry;
import ok.dht.test.scheredin.dao.Config;
import ok.dht.test.scheredin.dao.Entry;
import ok.dht.test.scheredin.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.abs;

public class MultiThreadedServer extends HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(MultiThreadedServer.class);
    private static final int QUEUE_CAPACITY = 32;
    private static final int EXECUTORS_COUNT = Runtime.getRuntime().availableProcessors() << 1;
    public static final String PATH = "/v0/entity";
    private MemorySegmentDao dao;
    private final ServiceConfig config;
    private static final int FLUSH_THRESHOLD_BYTES = 1 << 20; //1 MB

    private final StackImpl queue = new StackImpl<Runnable>(QUEUE_CAPACITY);
    private ExecutorService executorService = new ThreadPoolExecutor(
            EXECUTORS_COUNT,
            EXECUTORS_COUNT,
            0L,
            TimeUnit.MILLISECONDS,
            queue,
            new ThreadPoolExecutor.AbortPolicy());
    ;
    private final HttpClient client = HttpClient.newHttpClient();

    public MultiThreadedServer(ServiceConfig config, Object... routers) throws IOException {
        super(createConfigFromPort(config.selfPort()), routers);
        this.config = config;
    }

    @Override
    public synchronized void start() {
        try {
            dao = new MemorySegmentDao(new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create dao", e);
        }
        super.start();
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread selectorThread : selectors) {
            if (selectorThread.isAlive()) {
                for (Session session : selectorThread.selector) {
                    session.scheduleClose();
                }
            }
        }
        super.stop();
        try {
            dao.close();
        } catch (IOException e) {
            logger.warn("Failed to shutdown dao");
            throw new RuntimeException(e);
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Failed to shutdown executor service");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!request.getPath().equals(PATH)) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        String id = request.getParameter("id=");
        if (isIncorrectId(id)) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }


        executorService.execute(() -> {
            Integer nodeIndex = getNodeIndex(id);
            if (nodeIndex == null) {
                sendError(session, Response.SERVICE_UNAVAILABLE, null);
            }
            String url = config.clusterUrls().get(nodeIndex);
            boolean isSelfNode = url.equals(config.selfUrl());
            try {
                session.sendResponse(isSelfNode ? handleRequest(request, id) : proxyRequest(request, url));
            } catch (IOException | InterruptedException e) {
                logger.warn("Failed to send response");
                sendError(session, Response.INTERNAL_ERROR, e.getMessage());
            }
        });
    }

    private Integer getNodeIndex(String id) {
        int max = Integer.MIN_VALUE;
        Integer index = null;
        List<String> nodes = config.clusterUrls();
        for (int i = 0; i < nodes.size(); i++) {
            int cur = id.hashCode() * nodes.get(i).hashCode();
            max = Math.max(cur, max);
            index = i;
        }
        return index;
    }

    private Response proxyRequest(Request request, String url) throws IOException, InterruptedException {
        URI uriFromRequest = URI.create(request.getURI());
        byte[] body = request.getBody() == null ? new byte[]{} : request.getBody();
        HttpRequest proxyRequest = HttpRequest.newBuilder(URI.create(url + uriFromRequest.getPath() + '?' + uriFromRequest.getQuery()))
                .method(
                        request.getMethodName(),
                        HttpRequest.BodyPublishers.ofByteArray(body)
                ).build();
        HttpResponse<byte[]> response = client.send(proxyRequest, HttpResponse.BodyHandlers.ofByteArray());
        String status = switch (response.statusCode()) {
            case HttpURLConnection.HTTP_OK -> Response.OK;
            case HttpURLConnection.HTTP_CREATED -> Response.CREATED;
            case HttpURLConnection.HTTP_ACCEPTED -> Response.ACCEPTED;
            case HttpURLConnection.HTTP_NO_CONTENT -> Response.NO_CONTENT;
            case HttpURLConnection.HTTP_SEE_OTHER -> Response.SEE_OTHER;
            case HttpURLConnection.HTTP_NOT_MODIFIED -> Response.NOT_ACCEPTABLE;
            case HttpURLConnection.HTTP_USE_PROXY -> Response.USE_PROXY;
            case HttpURLConnection.HTTP_BAD_REQUEST -> Response.BAD_REQUEST;
            case HttpURLConnection.HTTP_UNAUTHORIZED -> Response.UNAUTHORIZED;
            case HttpURLConnection.HTTP_PAYMENT_REQUIRED -> Response.PAYMENT_REQUIRED;
            case HttpURLConnection.HTTP_FORBIDDEN -> Response.FORBIDDEN;
            case HttpURLConnection.HTTP_NOT_FOUND -> Response.NOT_FOUND;
            case HttpURLConnection.HTTP_NOT_ACCEPTABLE -> Response.NOT_ACCEPTABLE;
            case HttpURLConnection.HTTP_CONFLICT -> Response.CONFLICT;
            case HttpURLConnection.HTTP_GONE -> Response.GONE;
            case HttpURLConnection.HTTP_BAD_METHOD -> Response.METHOD_NOT_ALLOWED;
            case HttpURLConnection.HTTP_LENGTH_REQUIRED -> Response.LENGTH_REQUIRED;
            case HttpURLConnection.HTTP_NOT_IMPLEMENTED -> Response.NOT_IMPLEMENTED;
            case HttpURLConnection.HTTP_BAD_GATEWAY -> Response.BAD_GATEWAY;
            case HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> Response.GATEWAY_TIMEOUT;
            default -> throw new IllegalArgumentException("Unknown status code: " + response.statusCode());
        };
        return new Response(status, response.body());
    }

    private boolean isIncorrectId(String id) {
        return (id == null) || id.isBlank();
    }

    private Response handleRequest(Request request, String id) {
        MemorySegment key = MemorySegment.ofArray(id.getBytes(StandardCharsets.UTF_8));
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                Entry<MemorySegment> result = dao.get(key);
                if (result == null || result.isTombstone()) {
                    return new Response(Response.NOT_FOUND, Response.EMPTY);
                }
                return new Response(Response.OK, result.value().toByteArray());
            }
            case Request.METHOD_PUT -> {
                MemorySegment value = MemorySegment.ofArray(request.getBody());
                dao.upsert(new BaseEntry<>(key, value));
                return new Response(Response.CREATED, Response.EMPTY);
            }
            case Request.METHOD_DELETE -> {
                dao.upsert(new BaseEntry<>(key, null));
                return new Response(Response.ACCEPTED, Response.EMPTY);
            }
            default -> {
                return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            }
        }
    }

    private static HttpServerConfig createConfigFromPort(@Nonnull int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
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

package ok.dht.test.scheredin;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.scheredin.dao.BaseEntry;
import ok.dht.test.scheredin.dao.Config;
import ok.dht.test.scheredin.dao.Entry;
import ok.dht.test.scheredin.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.http.Path;
import one.nio.http.Param;
import one.nio.http.RequestMethod;
import one.nio.http.HttpServerConfig;
import one.nio.server.AcceptorConfig;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class SimpleService implements Service {

    private final ServiceConfig config;
    private HttpServer server;
    private MemorySegmentDao dao;
    private static final int FLUSH_THRESHOLD_BYTES = 1 << 20; //1 MB

    public SimpleService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        dao = new MemorySegmentDao(new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));
        server = new HttpServer(createConfigFromPort(config.selfPort())) {
            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
                session.sendResponse(response);
            }
        };
        server.start();
        server.addRequestHandlers(this);
        dao.flush();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        dao.close();
        server.stop();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id", required = true) String id) {
        if (id == null || id.isBlank()) {
            return new Response(
                    Response.BAD_REQUEST,
                    Response.EMPTY
            );
        }
        MemorySegment key = MemorySegment.ofArray(id.getBytes(StandardCharsets.UTF_8));
        Entry<MemorySegment> result = dao.get(key);
        if (result == null || result.isTombstone()) {
            return new Response(
                    Response.NOT_FOUND,
                    Response.EMPTY
            );
        }
        return new Response(
                Response.OK,
                result.value().toByteArray()
        );
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(@Param(value = "id", required = true) String id, Request request) {
        if (id == null || id.isBlank()) {
            return new Response(
                    Response.BAD_REQUEST,
                    Response.EMPTY
            );
        }
        MemorySegment key = MemorySegment.ofArray(id.getBytes(StandardCharsets.UTF_8));
        MemorySegment value = MemorySegment.ofArray(request.getBody());
        dao.upsert(new BaseEntry<>(key, value));
        return new Response(
                Response.CREATED,
                Response.EMPTY
        );
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id", required = true) String id) {
        if (id == null || id.isBlank()) {
            return new Response(
                    Response.BAD_REQUEST,
                    Response.EMPTY
            );
        }

        MemorySegment key = MemorySegment.ofArray(id.getBytes(StandardCharsets.UTF_8));
        dao.upsert(new BaseEntry<>(key, null));
        return new Response(
                Response.ACCEPTED,
                Response.EMPTY
        );
    }

    //<editor-fold desc="Utils">
    private static HttpServerConfig createConfigFromPort(@Nonnull int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }
    //</editor-fold>

    @ServiceFactory(stage = 1, week = 1)
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new SimpleService(config);
        }
    }
}

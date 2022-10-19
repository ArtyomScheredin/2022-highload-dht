package ok.dht.test.scheredin;

import ok.dht.ServiceConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Basic server stub.
 *
 * @author incubos
 */
public final class Main {
    private static final String BASE_URL = "http://localhost:";
    private static List<String> urls;

    private Main() {
        // Only main method
    }

    public static void main(String[] args) throws IOException, ExecutionException,
            InterruptedException, TimeoutException {
        int port;
        port = Integer.parseInt(args[0]);
        String url = BASE_URL + port;
        urls.add("http://localhost:19301");
        urls.add("http://localhost:19302");
        urls.add("http://localhost:19303");
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                urls,
                Files.createTempDirectory("server")
        );
        new SimpleService(cfg).start().get(1, TimeUnit.SECONDS);
    }
}

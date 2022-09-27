package ok.dht.test.scheredin;

import ok.dht.ServiceConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Basic server stub.
 *
 * @author incubos
 */
public final class Main {
    private static final int PORT = 19235;
    private static final String URL = "http://localhost:" + PORT;

    private Main() {
        // Only main method
    }

    public static void main(String[] args) throws IOException, ExecutionException,
            InterruptedException, TimeoutException {
        ServiceConfig cfg = new ServiceConfig(
                PORT,
                URL,
                Collections.singletonList(URL),
                Files.createTempDirectory("server")
        );
        new SimpleService(cfg).start().get(1, TimeUnit.SECONDS);
    }
}

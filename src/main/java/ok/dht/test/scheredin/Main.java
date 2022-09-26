package ok.dht.test.scheredin;

import ok.dht.ServiceConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.abs;

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

    public static void main(String[] args) throws Exception {
        Path serverPath = Files.createTempDirectory("server");
        ServiceConfig cfg = new ServiceConfig(
                PORT,
                URL,
                Collections.singletonList(URL),
                Path.of("/var/folders/zr/llh3lt015pg38ggz_tk96gqh0000gq/T/server7080579832102129790")
        );
        new SimpleService(cfg).start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + URL);
    }
}

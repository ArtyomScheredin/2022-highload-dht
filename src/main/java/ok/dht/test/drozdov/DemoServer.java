package ok.dht.test.drozdov;

import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Basic server stub.
 *
 * @author incubos
 */
public final class DemoServer {

    private DemoServer() {
        // Only main method
    }

    public static void main(String[] args) throws Exception {
        int port = 19235;
        String url = "http://localhost:" + port;
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Path.of("/var/folders/zr/llh3lt015pg38ggz_tk96gqh0000gq/T/server7080579832102129790")
        );
        new DemoService(cfg).start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + url);
    }
}



import java.util.UUID;

public class ServerMain {
    public static void main(String[] args) {
        int port = 5555;
        LineServer server = new LineServer(port, UUID.randomUUID().toString());
        server.start();
    }
}

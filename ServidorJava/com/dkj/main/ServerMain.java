package com.dkj.main;

import com.dkj.server.LineServer;

import java.util.UUID;

public class ServerMain {
    public static void main(String[] args) {
        Integer port = Integer.valueOf(5555);
        LineServer server = new LineServer(port, UUID.randomUUID().toString());
        server.start();
    }
}
package org.thingsboard.server.transport.coap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CoapStarter {
    public static void main(String[] args) {
        SpringApplication.run(CoapStarter.class, args);
    }
}

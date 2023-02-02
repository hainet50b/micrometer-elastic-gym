package com.programacho;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.elastic.ElasticConfig;
import io.micrometer.elastic.ElasticMeterRegistry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class MicrometerElasticGymApplication {

    public static void main(String[] args) throws IOException {
        MeterRegistry registry = elasticMeterRegistry();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        Random random = new Random();
        server.createContext("/timer", exchange -> {
            registry.timer("programacho.timer").record(() -> {
                sleep(random.nextInt(1_000));

                try (OutputStream os = exchange.getResponseBody()) {
                    exchange.sendResponseHeaders(200, 0);
                    os.write(new byte[]{});
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });

        server.createContext("/find", exchange -> {
            Timer timer = registry.find("programacho.timer").timer();

            ProgramachoTimer programachoTimer = new ProgramachoTimer(
                    timer.count(),
                    timer.max(TimeUnit.MILLISECONDS),
                    timer.mean(TimeUnit.MILLISECONDS),
                    timer.totalTime(TimeUnit.MILLISECONDS)
            );

            try (OutputStream os = exchange.getResponseBody()) {
                final String responseBody = programachoTimer + System.lineSeparator();
                final byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);

                exchange.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        server.start();
    }

    public record ProgramachoTimer(
            double count,
            double max,
            double average,
            double sum
    ) {
    }

    private static ElasticMeterRegistry elasticMeterRegistry() {
        ElasticConfig config = k -> switch (k) {
            // 以下はすべてデフォルト値
            case "elastic.host" -> "http://localhost:9200";
            case "elastic.index" -> "micrometer-metrics";
            case "elastic.userName" -> null;
            case "elastic.password" -> null;
            case "elastic.step" -> "1m";
            default -> null;
        };

        return new ElasticMeterRegistry(config, Clock.SYSTEM);
    }

    private static void sleep(int timeout) {
        try {
            TimeUnit.MILLISECONDS.sleep(timeout);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

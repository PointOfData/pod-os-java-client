package com.pointofdata.podos.connection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pointofdata.podos.errors.GatewayDError;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Transport-level dead-connection detection tests.
 *
 * <p>Each test spins up a local {@link ServerSocket}, drives a specific failure
 * scenario, and asserts the {@link ConnectionClient} classifies it correctly
 * (fatal connection-lost vs benign idle timeout) and clears its connected flag
 * on fatal errors.
 */
class DeadConnectionTest {

    /** Starts a one-shot TCP server that hands each accepted socket to {@code handler}. */
    private static ServerSocket startServer(Consumer<Socket> handler) throws IOException {
        ServerSocket server = new ServerSocket(0);
        Thread t = new Thread(() -> {
            try {
                while (!server.isClosed()) {
                    Socket s = server.accept();
                    new Thread(() -> handler.accept(s)).start();
                }
            } catch (IOException ignored) {
                // server closed
            }
        });
        t.setDaemon(true);
        t.start();
        return server;
    }

    private static ConnectionClient connect(ServerSocket server) throws IOException {
        ConnectionClient c = ConnectionClient.builder()
                .host("127.0.0.1")
                .port(String.valueOf(server.getLocalPort()))
                .receiveTimeout(Duration.ofMillis(500))
                .sendTimeout(Duration.ofMillis(500))
                .build();
        c.connect();
        return c;
    }

    @Test
    @Timeout(10)
    void corruptPrefixIsFatal() throws IOException {
        ServerSocket server = startServer(sock -> {
            try (OutputStream out = sock.getOutputStream()) {
                out.write("!!garbage".getBytes()); // 9 invalid prefix bytes
                out.flush();
                Thread.sleep(1000);
            } catch (Exception ignored) {
            }
        });
        try {
            ConnectionClient c = connect(server);
            try {
                GatewayDError err = null;
                try {
                    c.receive(1000);
                } catch (GatewayDError e) {
                    err = e;
                }
                assertNotNull(err, "expected a GatewayDError");
                assertTrue(err.isConnectionLost(), "expected connection lost, got " + err.getCode());
                assertFalse(c.isConnected(), "connected flag should be cleared");
            } finally {
                c.close();
            }
        } finally {
            server.close();
        }
    }

    @Test
    @Timeout(10)
    void rstMidResponseIsFatal() throws IOException {
        ServerSocket server = startServer(sock -> {
            try {
                OutputStream out = sock.getOutputStream();
                out.write("x00000064".getBytes()); // hex 0x64 = 100 total bytes
                out.write("partial".getBytes());
                out.flush();
                sock.setSoLinger(true, 0); // force RST on close
                sock.close();
            } catch (IOException ignored) {
            }
        });
        try {
            ConnectionClient c = connect(server);
            try {
                long start = System.currentTimeMillis();
                GatewayDError err = null;
                try {
                    c.receive(1000);
                } catch (GatewayDError e) {
                    err = e;
                }
                assertNotNull(err, "expected a GatewayDError");
                assertTrue(err.isConnectionLost(), "expected connection lost, got " + err.getCode());
                assertFalse(c.isConnected(), "connected flag should be cleared");
                assertTrue(System.currentTimeMillis() - start < 1500, "detection too slow");
            } finally {
                c.close();
            }
        } finally {
            server.close();
        }
    }

    @Test
    @Timeout(10)
    void idleTimeoutIsBenign() throws IOException {
        ServerSocket server = startServer(sock -> {
            try {
                Thread.sleep(2000); // stay connected but silent
                sock.close();
            } catch (Exception ignored) {
            }
        });
        try {
            ConnectionClient c = connect(server);
            try {
                GatewayDError err = null;
                try {
                    c.receive(300);
                } catch (GatewayDError e) {
                    err = e;
                }
                assertNotNull(err, "expected a GatewayDError");
                assertTrue(err.isIdleTimeout(), "expected idle timeout, got " + err.getCode());
                assertTrue(c.isConnected(), "connected flag must stay set on idle timeout");
            } finally {
                c.close();
            }
        } finally {
            server.close();
        }
    }

    @Test
    @Timeout(10)
    void sendAfterPeerCloseIsFatal() throws IOException, InterruptedException {
        CountDownLatch closed = new CountDownLatch(1);
        ServerSocket server = startServer(sock -> {
            try {
                sock.setSoLinger(true, 0);
                sock.close();
            } catch (IOException ignored) {
            } finally {
                closed.countDown();
            }
        });
        try {
            ConnectionClient c = connect(server);
            try {
                closed.await();
                GatewayDError err = null;
                // The first write may succeed (buffered); retry to surface the dead socket.
                for (int i = 0; i < 50; i++) {
                    try {
                        c.send("x00000009".getBytes());
                    } catch (GatewayDError e) {
                        err = e;
                        break;
                    }
                    Thread.sleep(20);
                }
                assertNotNull(err, "expected a GatewayDError from send");
                assertTrue(err.isConnectionLost(), "expected connection lost, got " + err.getCode());
                assertFalse(c.isConnected(), "connected flag should be cleared");
            } finally {
                c.close();
            }
        } finally {
            server.close();
        }
    }
}

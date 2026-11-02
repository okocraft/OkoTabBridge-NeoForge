package net.okocraft.tabbridge;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class StatusReaderTest {
    @Test void queriesMinecraftStatusWithTheConfiguredHostAndReturnsOnlineCount() throws Exception {
        try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {
            var reply=CompletableFuture.runAsync(() -> {
                try(var socket=server.accept()) {
                    var input=new DataInputStream(socket.getInputStream());
                    assertEquals(19,input.readUnsignedByte());
                    byte[] expected={0,(byte)255,(byte)255,(byte)255,(byte)255,15,9,49,50,55,46,48,46,48,46,49,(byte)(server.getLocalPort()>>>8),(byte)server.getLocalPort(),1};
                    assertArrayEquals(expected,input.readNBytes(19));
                    assertArrayEquals(new byte[]{1,0},input.readNBytes(2));
                    send(socket,"{\"players\":{\"online\":7,\"max\":20},\"description\":\"おこ鯖\"}");
                } catch(IOException e) { throw new UncheckedIOException(e); }
            });
            assertEquals(7,new StatusReader(1000).online(new Endpoint("127.0.0.1",server.getLocalPort())));
            reply.get(3,TimeUnit.SECONDS);
        }
    }
    @Test void rejectsCountsThatCannotRepresentAPlayerCount() throws Exception {
        for(String value:Arrays.asList("-1","1.5","\"7\"")) {
            try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {
                var reply=CompletableFuture.runAsync(() -> {
                    try(var socket=server.accept()) {
                        var input=new DataInputStream(socket.getInputStream());input.readNBytes(input.readUnsignedByte()+2);
                        send(socket,"{\"players\":{\"online\":"+value+"}}");
                    } catch(IOException e) { throw new UncheckedIOException(e); }
                });
                assertThrows(IOException.class,() -> new StatusReader(1000).online(new Endpoint("127.0.0.1",server.getLocalPort())));
                reply.get(3,TimeUnit.SECONDS);
            }
        }
    }
    @Test void reportsTruncatedPacketInsteadOfWaitingForMissingBytes() throws Exception {
        try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {
            var reply=CompletableFuture.runAsync(() -> {
                try(var socket=server.accept()) {
                    var input=new DataInputStream(socket.getInputStream());input.readNBytes(input.readUnsignedByte()+2);
                    socket.getOutputStream().write(new byte[]{20,0,18,123});
                } catch(IOException e) { throw new UncheckedIOException(e); }
            });
            assertThrows(EOFException.class,() -> new StatusReader(1000).online(new Endpoint("127.0.0.1",server.getLocalPort())));
            reply.get(3,TimeUnit.SECONDS);
        }
    }
    @Test void idlePeerTimesOut() throws Exception {
        try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {
            var release=new CountDownLatch(1);
            var reply=CompletableFuture.runAsync(() -> {
                try(var socket=server.accept()) { release.await(3,TimeUnit.SECONDS); }
                catch(IOException|InterruptedException e) { throw new RuntimeException(e); }
            });
            try { assertThrows(SocketTimeoutException.class,() -> new StatusReader(100).online(new Endpoint("127.0.0.1",server.getLocalPort()))); }
            finally { release.countDown(); }
            reply.get(3,TimeUnit.SECONDS);
        }
    }
    private static void send(Socket socket,String json) throws IOException {
        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
        var output=new DataOutputStream(socket.getOutputStream());
        output.writeByte(bytes.length+2);output.writeByte(0);output.writeByte(bytes.length);output.write(bytes);output.flush();
    }
}

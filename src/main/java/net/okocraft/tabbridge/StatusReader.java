package net.okocraft.tabbridge;

import com.google.gson.JsonParser;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

final class StatusReader {
    private static final int MAX_STATUS_BYTES=1_048_576;
    private final int timeoutMillis;
    private volatile Socket activeSocket;
    StatusReader(int timeoutMillis) { this.timeoutMillis=timeoutMillis; }

    int online(Endpoint endpoint) throws IOException {
        try(var socket=new Socket()) {
            activeSocket=socket;
            socket.connect(new InetSocketAddress(endpoint.host(),endpoint.port()),timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            var output=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            var bytes=new ByteArrayOutputStream();
            var handshake=new DataOutputStream(bytes);
            varInt(handshake,0); varInt(handshake,-1);
            byte[] host=endpoint.host().getBytes(StandardCharsets.UTF_8);
            varInt(handshake,host.length); handshake.write(host); handshake.writeShort(endpoint.port()); varInt(handshake,1);
            varInt(output,bytes.size()); bytes.writeTo(output); output.write(new byte[]{1,0}); output.flush();
            var input=new DataInputStream(socket.getInputStream());
            int length=readVarInt(input);
            if (length<2 || length>MAX_STATUS_BYTES) throw new IOException("Invalid status packet length");
            byte[] frame=input.readNBytes(length);
            if (frame.length!=length) throw new EOFException("Incomplete status packet");
            var packet=new DataInputStream(new ByteArrayInputStream(frame));
            if (readVarInt(packet)!=0) throw new IOException("Unexpected status packet");
            int size=readVarInt(packet);
            if (size<0 || size!=packet.available()) throw new IOException("Invalid status JSON length");
            try {
                var players=JsonParser.parseString(new String(packet.readNBytes(size),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("players");
                var count=players.getAsJsonPrimitive("online");
                if (!count.isNumber()) throw new IllegalArgumentException("Status online count is not numeric");
                int online=count.getAsBigDecimal().intValueExact();
                if (online<0) throw new IllegalArgumentException("Negative status online count");
                return online;
            } catch(RuntimeException failure) { throw new IOException("Invalid player count in Minecraft status",failure); }
        } finally { activeSocket=null; }
    }

    void cancel() {
        Socket socket=activeSocket;
        if (socket!=null) try { socket.close(); } catch(IOException ignored) { }
    }
    private static void varInt(DataOutput output,int value) throws IOException {
        do { int part=value&127; value>>>=7; output.writeByte(part|(value!=0?128:0)); } while(value!=0);
    }
    private static int readVarInt(DataInput input) throws IOException {
        int result=0;
        for(int shift=0;shift<35;shift+=7) {
            int part=input.readUnsignedByte(); result|=(part&127)<<shift;
            if ((part&128)==0) return result;
        }
        throw new IOException("Overlong Minecraft VarInt");
    }
}

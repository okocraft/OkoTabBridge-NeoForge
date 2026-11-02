package net.okocraft.tabbridge;

record Endpoint(String host,int port) {
    Endpoint {
        if (host==null || host.isBlank() || port<1 || port>65535)
            throw new IllegalArgumentException("Invalid Minecraft status endpoint");
    }
}

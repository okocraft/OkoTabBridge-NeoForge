package net.okocraft.tabbridge;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

record BridgeConfig(int refreshSeconds,int timeoutMillis,Endpoint network,Map<String,Endpoint> servers) {
    BridgeConfig {
        if (refreshSeconds<1 || timeoutMillis<1 || network==null || servers==null)
            throw new IllegalArgumentException("Invalid OkoTabBridge configuration");
        servers=Map.copyOf(servers);
    }
    static BridgeConfig load(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) try(var input=BridgeConfig.class.getResourceAsStream("/default-config.json")) {
            Files.copy(input,file);
        }
        try(var reader=Files.newBufferedReader(file)) { return new Gson().fromJson(reader,BridgeConfig.class); }
    }
}

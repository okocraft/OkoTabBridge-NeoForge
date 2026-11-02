package net.okocraft.tabbridge;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class ServerCounts implements AutoCloseable {
    private final BridgeConfig config;
    private final StatusReader reader;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(task -> {
        var thread=new Thread(task,"OkoTabBridge-status");thread.setDaemon(true);return thread;
    });
    private volatile Map<String,String> values=Map.of();
    ServerCounts(BridgeConfig config) { this.config=config; reader=new StatusReader(config.timeoutMillis()); }
    void start() { worker.scheduleWithFixedDelay(this::refresh,0,config.refreshSeconds(),TimeUnit.SECONDS); }
    String value(String server) { return values.getOrDefault(server,"…"); }
    private void refresh() {
        var endpoints=new LinkedHashMap<>(config.servers());endpoints.put("total",config.network());
        var next=new LinkedHashMap<String,String>();
        for(var entry:endpoints.entrySet()) {
            if (Thread.currentThread().isInterrupted()) return;
            try { next.put(entry.getKey(),Integer.toString(reader.online(entry.getValue()))); }
            catch(IOException failure) { next.put(entry.getKey(),"—"); }
        }
        values=Map.copyOf(next);
    }
    @Override public void close() { worker.shutdownNow();reader.cancel(); }
}

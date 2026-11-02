package net.okocraft.tabbridge;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.event.plugin.TabLoadEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(value="oko_tab_bridge",dist=Dist.DEDICATED_SERVER)
public final class OkoTabBridge {
    private ServerCounts counts;
    private BridgeConfig config;
    public OkoTabBridge() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::stopping);
    }
    private void started(ServerStartedEvent event) {
        try { config=BridgeConfig.load(FMLPaths.CONFIGDIR.get().resolve("oko-tab-bridge.json")); }
        catch(java.io.IOException failure) { throw new IllegalStateException("Cannot load OkoTabBridge configuration",failure); }
        counts=new ServerCounts(config);
        TabAPI.getInstance().getEventBus().register(TabLoadEvent.class,ignored -> register());
        register(); counts.start();
        System.getLogger("OkoTabBridge").log(System.Logger.Level.INFO,"OkoTabBridge initialized");
    }
    private void register() {
        var placeholders=TabAPI.getInstance().getPlaceholderManager();
        placeholders.registerServerPlaceholder("%bungee_total%",1000,() -> counts.value("total"));
        for(String name:config.servers().keySet())
            placeholders.registerServerPlaceholder("%bungee_"+name+"%",1000,() -> counts.value(name));
    }
    private void stopping(ServerStoppingEvent event) { if(counts!=null)counts.close(); }
}

package net.okocraft.tabbridge.testmod;

import me.neznamy.tab.api.TabAPI;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("oko_tab_test")
public final class TabNativeTests {
    private int ticks;
    private int reloadTick;
    private boolean done;
    public TabNativeTests() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private void tick(ServerTickEvent.Post event) {
        if(done || ++ticks<100)return;
        if(reloadTick>0 && ticks-reloadTick<40)return;
        try {
            var api=TabAPI.getInstance();
            if(ticks>600)throw new AssertionError("TAB player counts did not become available");
            String network=request(api,"%bungee_total%");
            if(!network.equals("0"))return;
            for(String server:new String[]{"main","hub","creative","dev"})
                if(!request(api,"%bungee_"+server+"%").equals("0"))throw new AssertionError("Backend count differs: "+server);
            if(api.getHeaderFooterManager()==null || api.getLayoutManager()==null || api.getTabListFormatManager()==null)
                throw new AssertionError("TAB player-list features are unavailable");
            if(api.getScoreboardManager()!=null || api.getBossBarManager()!=null || api.getBelowNameManager()!=null || api.getPlayerListObjectiveManager()!=null || api.getNameTagManager()!=null)
                throw new AssertionError("Persistent UI feature was enabled");
            if(reloadTick==0) {
                event.getServer().getCommands().performPrefixedCommand(event.getServer().createCommandSourceStack(),"tab reload");
                reloadTick=ticks;return;
            }
            if(ticks-reloadTick<40)return;
            done=true;System.out.println("OKO_TAB_NATIVE_TESTS_PASSED");
        } catch(Throwable failure) {
            done=true;failure.printStackTrace();System.out.println("OKO_TAB_NATIVE_TESTS_FAILED");event.getServer().halt(false);
        }
    }
    private static String request(TabAPI api,String name) throws Exception {
        Object placeholder=api.getPlaceholderManager().getPlaceholder(name);
        if(placeholder==null)throw new AssertionError("Network count placeholder is missing: "+name);
        return (String)placeholder.getClass().getMethod("request").invoke(placeholder);
    }
}

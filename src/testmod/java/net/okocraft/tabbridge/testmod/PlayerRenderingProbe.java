package net.okocraft.tabbridge.testmod;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import me.neznamy.tab.api.TabAPI;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.PrefixNode;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

final class PlayerRenderingProbe implements AutoCloseable {
    private static final UUID ID=UUID.fromString("a0f86b19-56fa-49e2-bbae-018978a3ca12");
    private static final PrefixNode TITLE=PrefixNode.builder("TAB_TEST_TITLE ",10000).build();
    private final List<Packet<?>> packets=new CopyOnWriteArrayList<>();
    private final ServerPlayer player;
    private final User user;
    PlayerRenderingProbe(MinecraftServer server) {
        var profile=new GameProfile(ID,"tab_probe");
        user=LuckPermsProvider.get().getUserManager().loadUser(ID,profile.name()).join();user.data().add(TITLE);
        player=new ServerPlayer(server,server.overworld(),profile,ClientInformation.createDefault());
        var wire=new LocalWire();
        player.connection=new ServerGamePacketListenerImpl(server,wire,player,CommonListenerCookie.createInitial(profile,false)) {
            @Override public void send(Packet<?> packet) { packets.add(packet); }
            @Override public void send(Packet<?> packet,ChannelFutureListener listener) { packets.add(packet); }
        };
        server.getPlayerList().getPlayersByUUID().put(ID,player);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
    }
    boolean rendered() {
        var api=TabAPI.getInstance();var actor=api.getPlayer(ID);
        if(actor==null || !actor.isLoaded())return false;
        String prefix=api.getTabListFormatManager().getOriginalReplacedPrefix(actor);
        if(prefix==null || !prefix.contains("TAB_TEST_TITLE"))return false;
        boolean name=false,header=false;
        for(var packet:packets) {
            if(packet instanceof ClientboundPlayerInfoUpdatePacket update)
                for(var entry:update.entries()) if(entry.displayName()!=null && entry.displayName().getString().contains("TAB_TEST_TITLE"))name=true;
            if(packet instanceof ClientboundTabListPacket tab && tab.header().getString().contains("OKOCRAFT"))header=true;
        }
        return name&&header;
    }
    String describe() {
        var api=TabAPI.getInstance();var actor=api.getPlayer(ID);
        var names=packets.stream().filter(p -> p instanceof ClientboundPlayerInfoUpdatePacket).map(p -> (ClientboundPlayerInfoUpdatePacket)p).flatMap(p -> p.entries().stream()).filter(e -> e.displayName()!=null).map(e -> e.displayName().getString()).distinct().toList();
        return "actor="+actor+"; loaded="+(actor==null?null:actor.isLoaded())+"; prefix="+(actor==null?null:api.getTabListFormatManager().getOriginalReplacedPrefix(actor))+"; lp="+user.getCachedData().getMetaData().getPrefix()+"; packets="+packets.stream().map(p -> p.getClass().getSimpleName()).distinct().toList()+"; names="+names;
    }
    @Override public void close() {
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
        player.level().getServer().getPlayerList().getPlayersByUUID().remove(ID,player);
        user.data().remove(TITLE);LuckPermsProvider.get().getUserManager().cleanupUser(user);
    }
    private static final class LocalWire extends net.minecraft.network.Connection {
        LocalWire() {
            super(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(new ChannelInitializer<Channel>() {
                @Override protected void initChannel(Channel channel) { channel.pipeline().addLast("packet_handler",LocalWire.this); }
            });
        }
        @Override public SocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1",40000); }
        @Override public void send(Packet<?> packet) { }
        @Override public void send(Packet<?> packet,ChannelFutureListener listener) { }
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush) { }
    }
}

package dev.dudie.baritonehelper.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Mock server players whose embedded connection accepts every registered mod
 * payload. The vanilla mock connection never negotiates channels, so mods that
 * sync on login (Create sends railway edge groups) would otherwise throw while
 * the player joins.
 */
final class GameTestPlayers {
    private GameTestPlayers() {
    }

    static ServerPlayer mockServerPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.onMinecraftRegister(connection, registeredPayloadIds());
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    @SuppressWarnings("unchecked")
    private static Set<ResourceLocation> registeredPayloadIds() {
        try {
            Field field = NetworkRegistry.class.getDeclaredField("PAYLOAD_REGISTRATIONS");
            field.setAccessible(true);
            Set<ResourceLocation> ids = new HashSet<>();
            ((Map<?, Map<ResourceLocation, ?>>) field.get(null)).values()
                    .forEach(registrations -> ids.addAll(registrations.keySet()));
            return ids;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read NeoForge payload registrations", exception);
        }
    }
}

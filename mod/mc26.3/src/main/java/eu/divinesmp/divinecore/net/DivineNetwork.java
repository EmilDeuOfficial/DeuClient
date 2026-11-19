package eu.divinesmp.divinecore.net;

import eu.divinesmp.divinecore.DivineConfig;
import eu.divinesmp.divinecore.DivineCore;
import eu.divinesmp.divinecore.cosmetics.Cosmetics;
import eu.divinesmp.divinecore.cosmetics.CustomCapes;
import net.fabricmc.fabric.api.client.networking.v1.ServerboundPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Sync der Divine-Cosmetics zwischen allen Divine-Spielern über den Server (DivineBridge-Plugin).
 *
 * Protokoll (Plugin-Kanäle, Strings = VarInt-Länge + UTF-8, UUID = 2 × long):
 *   C → S  divinecore:hello   { String clientVersion, String cape, String wings }
 *   S → C  divinecore:user    { UUID player, String cape, String wings }
 *   S → C  divinecore:remove  { UUID player }
 */
public final class DivineNetwork {
    private DivineNetwork() {}

    private static final int MAX = 64;

    public record Hello(String version, String cape, String wings) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.version, MAX); buf.writeUtf(p.cape, MAX); buf.writeUtf(p.wings, MAX); },
                buf -> new Hello(buf.readUtf(MAX), buf.readUtf(MAX), buf.readUtf(MAX)));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record User(UUID player, String cape, String wings) implements CustomPacketPayload {
        public static final Type<User> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "user"));
        public static final StreamCodec<RegistryFriendlyByteBuf, User> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUUID(p.player); buf.writeUtf(p.cape, MAX); buf.writeUtf(p.wings, MAX); },
                buf -> new User(buf.readUUID(), buf.readUtf(MAX), buf.readUtf(MAX)));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Remove(UUID player) implements CustomPacketPayload {
        public static final Type<Remove> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "remove"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Remove> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUUID(p.player),
                buf -> new Remove(buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C → S: eigenes Cape (PNG, max. 16 KB) */
    public record CapeUpload(byte[] png) implements CustomPacketPayload {
        public static final Type<CapeUpload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "cape_upload"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CapeUpload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeByteArray(p.png),
                buf -> new CapeUpload(buf.readByteArray(CustomCapes.MAX_BYTES)));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S → C: eigenes Cape eines anderen Divine-Spielers */
    public record CapeData(UUID player, byte[] png) implements CustomPacketPayload {
        public static final Type<CapeData> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "cape_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CapeData> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUUID(p.player); buf.writeByteArray(p.png); },
                buf -> new CapeData(buf.readUUID(), buf.readByteArray(CustomCapes.MAX_BYTES)));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(Hello.TYPE, Hello.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(User.TYPE, User.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Remove.TYPE, Remove.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CapeUpload.TYPE, CapeUpload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CapeData.TYPE, CapeData.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(CapeData.TYPE, (p, ctx) -> CustomCapes.receive(p.player(), p.png()));

        ClientPlayNetworking.registerGlobalReceiver(User.TYPE, (p, ctx) ->
                Cosmetics.setRemote(p.player(), new Cosmetics.Loadout(p.cape(), p.wings())));
        ClientPlayNetworking.registerGlobalReceiver(Remove.TYPE, (p, ctx) -> Cosmetics.removeRemote(p.player()));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sendHello());
        // Der Server meldet seine Kanäle evtl. erst nach dem Join an → dann grüßen
        ServerboundPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
            if (channels.contains(Hello.TYPE.id())) sendHello();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Cosmetics.clearRemote());
    }

    /** Teilt dem Server die eigenen Cosmetics mit (beim Joinen und nach jeder Änderung) */
    public static void sendHello() {
        if (!ClientPlayNetworking.canSend(Hello.TYPE)) return; // Server ohne DivineBridge → nur lokal sichtbar
        DivineConfig cfg = DivineConfig.get();
        String version = FabricLoader.getInstance().getModContainer(DivineCore.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        ClientPlayNetworking.send(new Hello(version, cfg.showCosmetics ? cfg.cape : "", cfg.showCosmetics ? cfg.wings : ""));
        // eigenes Cape mitschicken, damit andere Divine-Spieler es sehen
        if (cfg.showCosmetics && "custom".equals(cfg.cape) && ClientPlayNetworking.canSend(CapeUpload.TYPE)) {
            byte[] png = CustomCapes.selfBytes();
            if (png != null) ClientPlayNetworking.send(new CapeUpload(png));
        }
    }
}

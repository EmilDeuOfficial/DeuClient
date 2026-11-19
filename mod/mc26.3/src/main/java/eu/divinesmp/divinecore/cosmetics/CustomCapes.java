package eu.divinesmp.divinecore.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import eu.divinesmp.divinecore.DivineCore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Eigene Capes aus dem Launcher-Shop.
 *  - eigenes Cape: config/divinecore/custom_cape.png (legt der Launcher beim "Anlegen" ab)
 *  - Capes anderer Divine-Spieler: kommen als PNG über DivineBridge (divinecore:cape_data)
 * Alle werden als dynamische Texturen registriert und von Minecrafts Cape-/Elytra-Darstellung genutzt.
 */
public final class CustomCapes {
    private CustomCapes() {}

    public static final int MAX_BYTES = 16 * 1024;
    public static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("divinecore").resolve("custom_cape.png");
    private static final Identifier SELF = Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "dynamic/cape/self");

    private static long loadedStamp = -1;
    private static boolean selfLoaded;
    private static final Map<UUID, Identifier> REMOTE = new ConcurrentHashMap<>();

    /** Eigenes Cape (lädt bei Änderung der Datei neu); null, wenn keins vorhanden */
    public static Identifier self() {
        try {
            if (!Files.exists(FILE)) return null;
            long stamp = Files.getLastModifiedTime(FILE).toMillis();
            if (stamp != loadedStamp) {
                loadedStamp = stamp;
                selfLoaded = register(SELF, Files.readAllBytes(FILE));
            }
            return selfLoaded ? SELF : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] selfBytes() {
        try {
            if (!Files.exists(FILE) || Files.size(FILE) > MAX_BYTES) return null;
            return Files.readAllBytes(FILE);
        } catch (Exception e) {
            return null;
        }
    }

    public static Identifier remote(UUID uuid) {
        return REMOTE.get(uuid);
    }

    /** Cape eines anderen Spielers empfangen (läuft auf dem Render-Thread) */
    public static void receive(UUID uuid, byte[] png) {
        if (png.length == 0 || png.length > MAX_BYTES) return;
        Minecraft.getInstance().execute(() -> {
            Identifier id = Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "dynamic/cape/" + uuid.toString().replace("-", ""));
            if (register(id, png)) REMOTE.put(uuid, id);
        });
    }

    public static void clearRemote() {
        REMOTE.clear();
    }

    /** Nur echte Cape-Größen akzeptieren (64×32 oder 128×64) */
    private static boolean register(Identifier id, byte[] png) {
        try {
            NativeImage img = NativeImage.read(png);
            boolean ok = (img.getWidth() == 64 && img.getHeight() == 32) || (img.getWidth() == 128 && img.getHeight() == 64);
            if (!ok) {
                img.close();
                return false;
            }
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, img));
            return true;
        } catch (Exception e) {
            DivineCore.LOGGER.debug("Cape konnte nicht geladen werden", e);
            return false;
        }
    }
}

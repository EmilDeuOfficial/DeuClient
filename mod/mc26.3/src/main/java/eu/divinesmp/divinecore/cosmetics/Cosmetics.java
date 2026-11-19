package eu.divinesmp.divinecore.cosmetics;

import eu.divinesmp.divinecore.DivineConfig;
import eu.divinesmp.divinecore.DivineCore;
import net.minecraft.client.Minecraft;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Divine-Cosmetics: Capes, Flügel und das Divine-Logo neben Spielernamen.
 *
 * Die eigenen Cosmetics kommen aus der Config; die anderer Divine-Spieler schickt der
 * Server (DivineBridge-Plugin) über DivineNetwork. Jeder bekannte Eintrag bedeutet auch:
 * "dieser Spieler nutzt den Divine Client" → Logo neben dem Namen.
 */
public final class Cosmetics {
    private Cosmetics() {}

    public record Item(String id, String name) {}

    public static final List<Item> CAPES = List.of(
            new Item("gold", "Gold"), new Item("nacht", "Sternennacht"),
            new Item("flamme", "Flamme"), new Item("eis", "Eiskristall"), new Item("smaragd", "Smaragd"));

    public static final List<Item> WINGS = List.of(
            new Item("engel", "Engel"), new Item("daemon", "Dämon"),
            new Item("eis", "Eis"), new Item("phoenix", "Phönix"), new Item("schatten", "Schatten"),
            new Item("smaragd", "Smaragd"));

    /** Was ein Spieler trägt (leere Strings = nichts) */
    public record Loadout(String cape, String wings) {
        public static final Loadout NONE = new Loadout("", "");
    }

    /** Cosmetics anderer Divine-Spieler (vom Server) */
    private static final Map<UUID, Loadout> REMOTE = new ConcurrentHashMap<>();

    public static void setRemote(UUID uuid, Loadout loadout) {
        REMOTE.put(uuid, loadout);
    }

    public static void removeRemote(UUID uuid) {
        REMOTE.remove(uuid);
    }

    public static void clearRemote() {
        REMOTE.clear();
        CustomCapes.clearRemote();
    }

    public static int remoteCount() {
        return REMOTE.size();
    }

    private static boolean isSelf(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        return mc.getUser() != null && uuid.equals(mc.getUser().getProfileId());
    }

    /** Nutzt dieser Spieler den Divine Client? (man selbst immer) */
    public static boolean isDivineUser(UUID uuid) {
        return uuid != null && (isSelf(uuid) || REMOTE.containsKey(uuid));
    }

    public static Loadout loadoutFor(UUID uuid) {
        if (uuid == null || !DivineConfig.get().showCosmetics) return Loadout.NONE;
        if (isSelf(uuid)) {
            DivineConfig cfg = DivineConfig.get();
            return new Loadout(valid(CAPES, cfg.cape), valid(WINGS, cfg.wings));
        }
        Loadout l = REMOTE.get(uuid);
        return l == null ? Loadout.NONE : new Loadout(valid(CAPES, l.cape()), valid(WINGS, l.wings()));
    }

    private static String valid(List<Item> list, String id) {
        if (id == null || id.isEmpty()) return "";
        if (list == CAPES && "custom".equals(id)) return id; // eigenes Cape aus dem Launcher-Shop
        return list.stream().anyMatch(i -> i.id().equals(id)) ? id : "";
    }

    /* ---------------- Texturen ---------------- */

    public static Identifier capeTexture(String id) {
        return Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "textures/cosmetics/cape/" + id + ".png");
    }

    public static Identifier wingsTexture(String id) {
        return Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "textures/cosmetics/wings/" + id + ".png");
    }

    public static Identifier wingsLowerTexture(String id) {
        return Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "textures/cosmetics/wings/" + id + "_lower.png");
    }

    /** Cape als Skin-Asset (wird von Minecrafts eigener Cape- und Elytra-Darstellung genutzt); null = nicht verfügbar */
    public static ClientAsset.Texture capeAsset(String id) {
        Identifier tex = capeTexture(id);
        return new ClientAsset.ResourceTexture(tex, tex);
    }

    /** Wie capeAsset, unterstützt aber eigene Capes ("custom") – eigene bzw. vom Server empfangene */
    public static ClientAsset.Texture capeAssetFor(UUID uuid, String id) {
        if (!"custom".equals(id)) return capeAsset(id);
        Identifier tex = isSelf(uuid) ? CustomCapes.self() : CustomCapes.remote(uuid);
        return tex == null ? null : new ClientAsset.ResourceTexture(tex, tex);
    }

    /* ---------------- Logo neben Namen ---------------- */

    private static final Style LOGO_STYLE = Style.EMPTY
            .withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath(DivineCore.MOD_ID, "logo")))
            .withColor(0xFFFFFF);

    /** Setzt das Divine-Logo (eigenes Schrift-Glyph ) vor einen Namen */
    public static Component withLogo(Component name) {
        MutableComponent logo = Component.literal("").setStyle(LOGO_STYLE);
        return Component.empty().append(logo).append(Component.literal(" ")).append(name);
    }

    public static boolean hasLogo(Component name) {
        return name.getString().startsWith("");
    }
}

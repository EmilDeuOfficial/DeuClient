package eu.deusmp.deucore.cosmetics;

import eu.deusmp.deucore.DeuConfig;
import eu.deusmp.deucore.DeuCore;
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
 * Deu-Cosmetics: Capes, Flügel und das Deu-Logo neben Spielernamen.
 *
 * Die eigenen Cosmetics kommen aus der Config; die anderer Deu-Spieler schickt der
 * Server (DeuBridge-Plugin) über DeuNetwork. Jeder bekannte Eintrag bedeutet auch:
 * "dieser Spieler nutzt den DeuClient" → Logo neben dem Namen.
 */
public final class Cosmetics {
    private Cosmetics() {}

    public record Item(String id, String name) {}

    /** Zusammengehoerige Capes und Fluegel; im Menue und im Launcher-Shop ein einklappbarer Abschnitt */
    public record Collection(String id, String name, List<Item> capes, List<Item> wings) {}

    /** Neue Kollektionen hier anhaengen (und in src/pages/Shop.tsx) - Ids der Teile muessen eindeutig bleiben */
    public static final List<Collection> COLLECTIONS = List.of(
            new Collection("mythos", "Mythos-Kollektion",
                    List.of(new Item("gold", "Gold"), new Item("nacht", "Sternennacht"),
                            new Item("flamme", "Flamme"), new Item("eis", "Eiskristall"), new Item("smaragd", "Smaragd"),
                            new Item("daemon", "Dämon")),
                    List.of(new Item("engel", "Engel"), new Item("daemon", "Dämon"),
                            new Item("eis", "Eis"), new Item("phoenix", "Phönix"), new Item("schatten", "Schatten"),
                            new Item("smaragd", "Smaragd"))));

    /** Name der neuesten Kollektion (Karte im Hauptmenue) */
    public static final String COLLECTION = COLLECTIONS.getLast().name();

    public static final List<Item> CAPES = COLLECTIONS.stream().flatMap(k -> k.capes().stream()).toList();

    public static final List<Item> WINGS = COLLECTIONS.stream().flatMap(k -> k.wings().stream()).toList();

    /** Was ein Spieler trägt (leere Strings = nichts) */
    public record Loadout(String cape, String wings) {
        public static final Loadout NONE = new Loadout("", "");
    }

    /** Cosmetics anderer Deu-Spieler (vom Server) */
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

    /** Nutzt dieser Spieler den DeuClient? (man selbst immer) */
    public static boolean isDeuUser(UUID uuid) {
        return uuid != null && (isSelf(uuid) || REMOTE.containsKey(uuid));
    }

    /** Will dieser Spieler (nur man selbst, die Einstellung ist lokal) seine Fluegel statt der Elytra sehen? */
    public static boolean wingsInsteadOfElytra(UUID uuid) {
        return uuid != null && isSelf(uuid) && DeuConfig.get().elytraWings;
    }

    public static Loadout loadoutFor(UUID uuid) {
        if (uuid == null || !DeuConfig.get().showCosmetics) return Loadout.NONE;
        if (isSelf(uuid)) {
            DeuConfig cfg = DeuConfig.get();
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

    /**
     * Schreibt die aktuelle Auswahl in die gemeinsame Datei des Launchers (cosmetics.json),
     * damit der Shop im Launcher sie anzeigt. Der Launcher uebergibt den Ordner als -Ddeuclient.data.
     */
    public static void syncToLauncher() {
        String dir = System.getProperty("deuclient.data");
        if (dir == null || dir.isEmpty()) return;
        try {
            java.nio.file.Path file = java.nio.file.Path.of(dir, "cosmetics.json");
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            if (java.nio.file.Files.exists(file)) {
                o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
            }
            DeuConfig c = DeuConfig.get();
            // "custom" = eigenes Cape aus dem Launcher: dessen Kennung (custom:<id>) bleibt erhalten
            if (!"custom".equals(c.cape)) o.addProperty("cape", c.cape == null ? "" : c.cape);
            o.addProperty("wings", c.wings == null ? "" : c.wings);
            java.nio.file.Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception e) {
            DeuCore.LOGGER.debug("Cosmetics konnten nicht an den Launcher gemeldet werden", e);
        }
    }

    /* ---------------- Texturen ---------------- */

    public static Identifier capeTexture(String id) {
        return Identifier.fromNamespaceAndPath(DeuCore.MOD_ID, "textures/cosmetics/cape/" + id + ".png");
    }

    /** Weich gefilterte Vorschau fuer Menues (Vorderseite des Capes bzw. ein Fluegel) */
    public static Identifier capeUi(String id) {
        return eu.deusmp.deucore.DeuRender.uiTexture("cape", id);
    }

    public static Identifier wingsUi(String id) {
        return eu.deusmp.deucore.DeuRender.uiTexture("wings", id);
    }

    public static Identifier wingsTexture(String id) {
        return Identifier.fromNamespaceAndPath(DeuCore.MOD_ID, "textures/cosmetics/wings/" + id + ".png");
    }

    /** Cape als Skin-Asset (wird von Minecrafts eigener Cape- und Elytra-Darstellung genutzt); null = nicht verfügbar */
    public static ClientAsset.Texture capeAsset(String id) {
        Identifier tex = capeTexture(id);
        return new ClientAsset.ResourceTexture(tex, tex);
    }

    /** Wie capeAsset, unterstützt aber eigene Capes ("custom") - eigene bzw. vom Server empfangene */
    public static ClientAsset.Texture capeAssetFor(UUID uuid, String id) {
        if (!"custom".equals(id)) return capeAsset(id);
        Identifier tex = isSelf(uuid) ? CustomCapes.self() : CustomCapes.remote(uuid);
        return tex == null ? null : new ClientAsset.ResourceTexture(tex, tex);
    }

    /* ---------------- Logo neben Namen ---------------- */

    private static final Style LOGO_STYLE = Style.EMPTY
            .withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath(DeuCore.MOD_ID, "logo")))
            .withColor(0xFFFFFF);

    /** Setzt das Deu-Logo (eigenes Schrift-Glyph ) vor einen Namen */
    public static Component withLogo(Component name) {
        MutableComponent logo = Component.literal("").setStyle(LOGO_STYLE);
        // das Leerzeichen kommt aus der Logo-Schrift (schmaler als das normale), so sitzt das Icon dichter am Namen
        return Component.empty().append(logo).append(Component.literal(" ").setStyle(LOGO_STYLE)).append(name);
    }

    public static boolean hasLogo(Component name) {
        return name.getString().startsWith("");
    }
}

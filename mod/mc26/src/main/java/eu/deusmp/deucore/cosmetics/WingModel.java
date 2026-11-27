package eu.deusmp.deucore.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3D-Fluegelmodell aus einer Blockbench-Datei (assets/deucore/models/wings/&lt;id&gt;.bbmodel).
 *
 * Regeln fuer die Modelle: siehe tools/wings/SPEC.md. Kurzfassung (Blockbench-Raum, 16 Einheiten = 1 Block):
 * +X = vom Ruecken weg (nach aussen), +Y = oben, +Z = nach hinten, Ursprung = Schulterblatt-Ansatz.
 * Es wird nur der rechte Fluegel erstellt, der linke wird gespiegelt.
 *
 * Aufbau: Gruppen (Outliner) werden zu {@link Node}s. Jede Gruppe kennt ihre eigenen, vorberechneten Flaechen
 * (Quads mit bereits eingerechneter Cube-Rotation und normierten UVs). Pro Frame wird nur noch die Gruppen-Matrix
 * (Pivot, Ruhe-Rotation, Animation) auf einem Matrix-Stack berechnet - ohne Allokationen pro Vertex.
 *
 * Animation (nach Gruppennamen): "wing" = Oeffnungswinkel (Yaw) + Flap (Roll), "segN" (N >= 2) = phasenverzoegerter
 * Zusatz-Roll, "f_*" = leichtes Wiegen. Alles andere folgt nur seiner Elterngruppe.
 *
 * Alternativ spielt {@link Anim#clips} die Animationen aus der Blockbench-Datei ab (Fluegel statt Elytra).
 *
 * Nicht von Minecraft-Klassen abhaengig (nur Gson, JOML, SLF4J), damit es ausserhalb des Spiels testbar ist.
 * Rendern nur vom Render-Thread.
 */
public final class WingModel {
    private static final Logger LOGGER = LoggerFactory.getLogger("DeuCore");
    private static final String PATH = "/assets/deucore/models/wings/";

    private static final float DEG = (float) (Math.PI / 180.0);
    private static final float EPS = 1e-3f;
    private static final int MAX_DEPTH = 32;
    private static final int CUBE_BUDGET = 150;
    private static final float DEFAULT_CONTACT = 0.6f;

    private static final int K_OTHER = 0, K_WING = 1, K_SEG = 2, K_FEATHER = 3;

    /** Pro Quad: 4 Vertices zu je (x, y, z, u, v) plus die Flaechennormale (nx, ny, nz) */
    private static final int QUAD_FLOATS = 23;
    private static final int NORMAL_OFFSET = 20;

    /** Flaechen-Reihenfolge: north (-Z), south (+Z), east (+X), west (-X), up (+Y), down (-Y) */
    private static final String[] FACE_NAMES = {"north", "south", "east", "west", "up", "down"};
    /** Achse, die die Flaeche durchstoesst (0 = X, 1 = Y, 2 = Z) - fuer duenne Ebenen */
    private static final int[] FACE_AXIS = {2, 2, 0, 0, 1, 1};
    private static final float[][] FACE_NORMAL = {
            {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
    /**
     * Ecken je Flaeche (0 = min, 1 = max der Achsen x, y, z) in der Reihenfolge der Minecraft/Blockbench-Flaechen:
     * links oben, links unten, rechts unten, rechts oben - von aussen gesehen. Von aussen ist das gegen den
     * Uhrzeigersinn (CCW) im rechtshaendigen Blockbench-Raum, die Normale zeigt nach aussen.
     */
    private static final int[][][] FACE_VERTS = {
            {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}}, // north
            {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}}, // south
            {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}, // east
            {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}}, // west
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}}, // up
            {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}}, // down
    };

    /** Eine Zeile pro Vertex: nimmt Position und UV im Modellraum der Ebene sowie die Normale entgegen */
    public interface QuadSink {
        void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz);
    }

    /** Animationsparameter eines Frames (Winkel in Grad) */
    public static final class Anim {
        /** Oeffnungswinkel: Flügel schwenken mit der Spitze nach hinten */
        public float open;
        /** Amplitude des Flaps (Roll) in Grad, 0 = keine Bewegung */
        public float flapAmp;
        /** Phase des Flaps in Radiant */
        public float flapPhase;
        /** Amplitude des Wiegens der Federn in Grad */
        public float swayAmp;
        /** Zeit in Ticks (fuer das Wiegen) */
        public float time;
        /** Namen von Blockbench-Animationen; gesetzt = sie ersetzen die prozedurale Bewegung */
        public String[] clips;
        /** Gewicht je Eintrag in {@link #clips} (zusammen 1) */
        public float[] weights;
        /** Abspielzeit der Animationen in Sekunden */
        public float seconds;
    }

    /** Keyframes eines Kanals einer Gruppe: Zeiten in Sekunden, je Keyframe drei Werte (Grad bzw. Faktor) */
    private record Track(float[] times, float[] values) {}

    private record Clip(float length, Map<String, Track> rotations, Map<String, Track> scales) {
        /**
         * Addiert den gewichteten Wert (Catmull-Rom wie in Blockbench) der Gruppe auf out.
         * base = Ruhewert, der abgezogen wird (0 bei Drehungen, 1 bei Skalierungen).
         */
        void add(Map<String, Track> tracks, String group, float seconds, float weight, float base, float[] out) {
            Track t = tracks.get(group);
            if (t == null) return;
            float[] times = t.times(), v = t.values();
            int n = times.length;
            float u = length > 0f ? seconds % length : 0f;
            int i = 0;
            while (i < n - 2 && times[i + 1] <= u) i++;
            if (n == 1) {
                for (int a = 0; a < 3; a++) out[a] += weight * (v[a] - base);
                return;
            }
            float span = times[i + 1] - times[i];
            float f = span > 0f ? Math.max(0f, Math.min(1f, (u - times[i]) / span)) : 0f;
            // Endlosschleife: der letzte Keyframe entspricht dem ersten
            int before = i > 0 ? i - 1 : Math.max(0, n - 2), after = i + 2 < n ? i + 2 : Math.min(1, n - 1);
            float f2 = f * f, f3 = f2 * f;
            for (int a = 0; a < 3; a++) {
                float p0 = v[before * 3 + a], p1 = v[i * 3 + a], p2 = v[(i + 1) * 3 + a], p3 = v[after * 3 + a];
                float value = 0.5f * (2f * p1 + (p2 - p0) * f + (2f * p0 - 5f * p1 + 4f * p2 - p3) * f2
                        + (3f * p1 - p0 - 3f * p2 + p3) * f3);
                out[a] += weight * (value - base);
            }
        }
    }

    private static final class Node {
        /** Kennung der Gruppe in Blockbench (Animationen verweisen darauf) */
        String uuid = "";
        final String name;
        final int kind;
        final int seg;
        final float phase;
        final float ox, oy, oz;
        final float rx, ry, rz;
        final boolean rest;
        Node[] children = new Node[0];
        float[] quads = new float[0];
        int quadCount;

        Node(String name, int kind, int seg, float phase, float[] origin, float[] rotDeg) {
            this.name = name;
            this.kind = kind;
            this.seg = seg;
            this.phase = phase;
            this.ox = origin[0];
            this.oy = origin[1];
            this.oz = origin[2];
            this.rx = rotDeg[0] * DEG;
            this.ry = rotDeg[1] * DEG;
            this.rz = rotDeg[2] * DEG;
            this.rest = rotDeg[0] != 0f || rotDeg[1] != 0f || rotDeg[2] != 0f;
        }
    }

    /** Wiederverwendbare Arbeitsobjekte (pro Thread), damit pro Frame nichts pro Vertex allokiert wird */
    private static final class Scratch {
        Matrix4f[] m = new Matrix4f[0];
        final Vector3f p = new Vector3f();
        final Vector3f n = new Vector3f();
        final float[] pose = new float[3];
        final float[] grow = new float[3];

        /** Gesammelte Vertices fuer das Tiefensortieren: je (x, y, z, u, v, nx, ny, nz) */
        float[] buf = new float[8 * 4 * 512];
        int vertCount;
        long[] keys = new long[512];
        final QuadSink collector = (x, y, z, u, v, nx, ny, nz) -> {
            if ((vertCount + 1) * 8 > buf.length) buf = Arrays.copyOf(buf, buf.length * 2);
            int o = vertCount++ * 8;
            buf[o] = x;
            buf[o + 1] = y;
            buf[o + 2] = z;
            buf[o + 3] = u;
            buf[o + 4] = v;
            buf[o + 5] = nx;
            buf[o + 6] = ny;
            buf[o + 7] = nz;
        };

        /** Gibt alle gesammelten Quads von hinten nach vorn (Abstand zur Kamera im Ursprung) aus */
        void flush(QuadSink sink) {
            int nq = vertCount / 4;
            if (keys.length < nq) keys = new long[Math.max(nq, keys.length * 2)];
            for (int q = 0; q < nq; q++) {
                int o = q * 32;
                float cx = (buf[o] + buf[o + 8] + buf[o + 16] + buf[o + 24]) * 0.25f;
                float cy = (buf[o + 1] + buf[o + 9] + buf[o + 17] + buf[o + 25]) * 0.25f;
                float cz = (buf[o + 2] + buf[o + 10] + buf[o + 18] + buf[o + 26]) * 0.25f;
                float d2 = cx * cx + cy * cy + cz * cz; // positiv: Bitmuster ist monoton
                keys[q] = ((long) Float.floatToIntBits(d2) << 32) | q;
            }
            Arrays.sort(keys, 0, nq);
            for (int i = nq - 1; i >= 0; i--) { // weit entfernt zuerst
                int o = (int) (keys[i] & 0xFFFFFFFFL) * 32;
                for (int k = 0; k < 4; k++) {
                    int b = o + k * 8;
                    sink.vertex(buf[b], buf[b + 1], buf[b + 2], buf[b + 3], buf[b + 4], buf[b + 5], buf[b + 6], buf[b + 7]);
                }
            }
            vertCount = 0;
        }

        void ensure(int size) {
            if (m.length >= size) return;
            Matrix4f[] bigger = Arrays.copyOf(m, size);
            for (int i = 0; i < size; i++) if (bigger[i] == null) bigger[i] = new Matrix4f();
            m = bigger;
        }
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    /** Geladene Modelle; leer = Laden ist fehlgeschlagen (wurde einmal gemeldet) */
    private static final Map<String, Optional<WingModel>> CACHE = new ConcurrentHashMap<>();

    private final String id;
    private final Node root;
    private final int stackSize;
    private final int quadCount;
    private final int cubeCount;
    private final Map<String, Clip> clips;
    private final float contact;

    private WingModel(String id, Node root, int maxDepth, int quadCount, int cubeCount, Map<String, Clip> clips) {
        this.clips = clips;
        this.contact = contact(root);
        this.id = id;
        this.root = root;
        this.stackSize = maxDepth + 2;
        this.quadCount = quadCount;
        this.cubeCount = cubeCount;
    }

    public String id() { return id; }
    public int quadCount() { return quadCount; }
    public int cubeCount() { return cubeCount; }

    /**
     * Tiefe (Blockbench-Z), mit der der Fluegel am Ruecken anliegt - bei jedem Modell anders. Gemessen an den Ecken
     * hinter dem Rumpf: das vorderste Viertel (Ansatz, Edelsteine) sinkt in den Ruecken ein, der Rest liegt darauf.
     */
    public float contactDepth() { return contact; }

    private static float contact(Node root) {
        List<Float> depths = new ArrayList<>();
        collectDepths(root, depths);
        if (depths.isEmpty()) return DEFAULT_CONTACT;
        depths.sort(Float::compare);
        return depths.get((depths.size() - 1) / 4);
    }

    private static void collectDepths(Node n, List<Float> out) {
        float[] q = n.quads;
        for (int i = 0; i < n.quadCount; i++) {
            for (int k = 0; k < 4; k++) {
                int o = i * QUAD_FLOATS + k * 5;
                if (q[o] >= -1.5f && q[o] <= 4.5f && q[o + 1] >= -12f && q[o + 1] <= 6f) out.add(q[o + 2]);
            }
        }
        for (Node c : n.children) collectDepths(c, out);
    }

    /** true, wenn das Modell alle diese Blockbench-Animationen enthaelt */
    public boolean hasClips(String[] names) {
        for (String n : names) if (!clips.containsKey(n)) return false;
        return true;
    }

    /** Liefert das Modell zur Fluegel-ID oder null (fehlt/defekt: es wird nichts gezeichnet, eine Warnung pro ID) */
    public static WingModel get(String id) {
        if (id == null || id.isEmpty()) return null;
        Optional<WingModel> m = CACHE.get(id);
        if (m == null) {
            m = Optional.ofNullable(load(id));
            CACHE.put(id, m);
        }
        return m.orElse(null);
    }

    private static WingModel load(String id) {
        if (!id.matches("[a-z0-9_]+")) {
            LOGGER.warn("Fluegelmodell '{}': ungueltige ID", id);
            return null;
        }
        try (InputStream in = WingModel.class.getResourceAsStream(PATH + id + ".bbmodel")) {
            if (in == null) {
                LOGGER.warn("Fluegelmodell '{}' nicht gefunden ({}{}.bbmodel) - es werden keine Fluegel gezeichnet", id, PATH, id);
                return null;
            }
            WingModel model = parse(id, new InputStreamReader(in, StandardCharsets.UTF_8));
            LOGGER.info("Fluegelmodell '{}' geladen: {} Cubes, {} Flaechen", id, model.cubeCount, model.quadCount);
            return model;
        } catch (Exception e) {
            LOGGER.warn("Fluegelmodell '{}' defekt - es werden keine Fluegel gezeichnet: {}", id, e.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------ Parser

    /** Liest ein .bbmodel (Blockbench Generic Model, Per-Face-UV). Wirft bei kaputten Dateien eine Exception. */
    public static WingModel parse(String id, Reader reader) {
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
        return new Parser(id, json).build();
    }

    private static final class Parser {
        final String id;
        final JsonObject json;
        final float resW, resH;
        final Map<String, JsonObject> elements = new HashMap<>();
        final Map<String, JsonObject> groupDefs = new HashMap<>();
        final Set<String> warned = new HashSet<>();
        boolean wingFound;
        int feathers, quads, cubes, maxDepth;

        Parser(String id, JsonObject json) {
            this.id = id;
            this.json = json;
            JsonObject res = obj(json, "resolution");
            float w = res == null ? 0f : num(res, "width", 0f);
            float h = res == null ? 0f : num(res, "height", 0f);
            if (w <= 0f || h <= 0f) throw new IllegalStateException("resolution fehlt oder ist ungueltig");
            this.resW = w;
            this.resH = h;
            JsonArray els = arr(json, "elements");
            if (els != null) {
                for (JsonElement e : els) {
                    if (!e.isJsonObject()) continue;
                    String uuid = str(e.getAsJsonObject(), "uuid");
                    if (uuid != null) elements.put(uuid, e.getAsJsonObject());
                }
            }
            JsonArray grps = arr(json, "groups"); // Blockbench 5: Gruppen-Daten getrennt vom Outliner
            if (grps != null) {
                for (JsonElement g : grps) {
                    if (!g.isJsonObject()) continue;
                    String uuid = str(g.getAsJsonObject(), "uuid");
                    if (uuid != null) groupDefs.put(uuid, g.getAsJsonObject());
                }
            }
        }

        WingModel build() {
            JsonArray outliner = arr(json, "outliner");
            if (outliner == null) throw new IllegalStateException("outliner fehlt");
            for (JsonElement e : outliner) {
                if (e.isJsonObject() && "wing".equals(groupName(e.getAsJsonObject()))) wingFound = true;
            }
            Node top = new Node("root", K_OTHER, 0, 0f, new float[3], new float[3]);
            addChildren(top, outliner, 0, true);
            if (quads == 0) throw new IllegalStateException("keine renderbaren Flaechen (Cubes mit Textur und UV)");
            if (!wingFound) warn("Gruppe 'wing' fehlt - alle Gruppen der obersten Ebene werden wie 'wing' animiert");
            if (cubes > CUBE_BUDGET) warn("mehr als " + CUBE_BUDGET + " Cubes (" + cubes + ")");
            return new WingModel(id, top, maxDepth, quads, cubes, animations());
        }

        /** Liest die Dreh- und Groessen-Keyframes aller Animationen */
        Map<String, Clip> animations() {
            Map<String, Clip> out = new HashMap<>();
            JsonArray list = arr(json, "animations");
            if (list == null) return out;
            for (JsonElement a : list) {
                if (!a.isJsonObject()) continue;
                JsonObject anim = a.getAsJsonObject();
                String name = str(anim, "name");
                JsonObject animators = obj(anim, "animators");
                if (name == null || animators == null) continue;
                Map<String, Track> rotations = new HashMap<>(), scales = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : animators.entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonArray frames = arr(e.getValue().getAsJsonObject(), "keyframes");
                    if (frames == null) continue;
                    Track r = track(frames, "rotation", 0f), s = track(frames, "scale", 1f);
                    if (r != null) rotations.put(e.getKey(), r);
                    if (s != null) scales.put(e.getKey(), s);
                }
                out.put(name, new Clip(num(anim, "length", 1f), rotations, scales));
            }
            return out;
        }

        /** Keyframes eines Kanals, nach Zeit sortiert; null = keine. Molang-Ausdruecke zaehlen als Ruhewert. */
        static Track track(JsonArray frames, String channel, float rest) {
            List<float[]> keys = new ArrayList<>(); // Zeit, x, y, z
            for (JsonElement k : frames) {
                if (!k.isJsonObject() || !channel.equals(str(k.getAsJsonObject(), "channel"))) continue;
                JsonArray points = arr(k.getAsJsonObject(), "data_points");
                if (points == null || points.isEmpty() || !points.get(0).isJsonObject()) continue;
                JsonObject p = points.get(0).getAsJsonObject();
                keys.add(new float[]{num(k.getAsJsonObject(), "time", 0f), num(p, "x", rest), num(p, "y", rest), num(p, "z", rest)});
            }
            if (keys.isEmpty()) return null;
            keys.sort((x, y) -> Float.compare(x[0], y[0]));
            float[] times = new float[keys.size()], values = new float[keys.size() * 3];
            for (int i = 0; i < keys.size(); i++) {
                times[i] = keys.get(i)[0];
                System.arraycopy(keys.get(i), 1, values, i * 3, 3);
            }
            return new Track(times, values);
        }

        void warn(String msg) {
            if (warned.add(msg)) LOGGER.warn("Fluegelmodell '{}': {}", id, msg);
        }

        String groupName(JsonObject entry) {
            String name = str(entry, "name");
            if (name == null) {
                String uuid = str(entry, "uuid");
                JsonObject def = uuid == null ? null : groupDefs.get(uuid);
                if (def != null) name = str(def, "name");
            }
            return name;
        }

        void addChildren(Node node, JsonArray children, int depth, boolean top) {
            List<Node> groups = new ArrayList<>();
            FloatList quadList = new FloatList();
            for (JsonElement c : children) {
                if (c.isJsonPrimitive()) {
                    JsonObject el = elements.get(c.getAsString());
                    if (el != null) cube(quadList, el);
                } else if (c.isJsonObject() && depth < MAX_DEPTH) {
                    Node g = group(c.getAsJsonObject(), depth + 1, top);
                    if (g != null) groups.add(g);
                }
            }
            node.quads = quadList.toArray();
            node.quadCount = quadList.size / QUAD_FLOATS;
            node.children = groups.toArray(new Node[0]);
        }

        Node group(JsonObject entry, int depth, boolean top) {
            String uuid = str(entry, "uuid");
            JsonObject def = uuid == null ? null : groupDefs.get(uuid);
            if (!bool(entry, def, "export", true)) return null;
            String name = groupName(entry);
            if (name == null) name = "";
            float[] origin = vec3(entry, def, "origin");
            float[] rotation = vec3(entry, def, "rotation");

            int kind = K_OTHER, seg = 0;
            float phase = 0f;
            if (top && (!wingFound || name.equals("wing"))) {
                kind = K_WING;
            } else if (isSeg(name)) {
                kind = K_SEG;
                seg = Integer.parseInt(name.substring(3));
            } else if (name.startsWith("f_")) {
                kind = K_FEATHER;
                phase = (feathers++) * 0.83f;
            }
            Node node = new Node(name, kind, seg, phase, origin, rotation);
            if (uuid != null) node.uuid = uuid;
            maxDepth = Math.max(maxDepth, depth);
            JsonArray kids = arr(entry, "children");
            if (kids != null) addChildren(node, kids, depth, false);
            return node;
        }

        static boolean isSeg(String name) {
            if (name.length() < 4 || name.length() > 9 || !name.startsWith("seg")) return false;
            for (int i = 3; i < name.length(); i++) if (!Character.isDigit(name.charAt(i))) return false;
            return true;
        }

        /** Haengt alle renderbaren Flaechen eines Cubes an */
        void cube(FloatList out, JsonObject e) {
            String type = str(e, "type");
            if (type != null && !type.equals("cube")) {
                warn("Elementtyp '" + type + "' wird nicht unterstuetzt (nur Cubes) - uebersprungen");
                return;
            }
            if (!bool(e, null, "export", true)) return;
            cubes++;

            float[] from = vec3(e, null, "from"), to = vec3(e, null, "to");
            float inflate = num(e, "inflate", 0f);
            float[] mn = new float[3], mx = new float[3];
            int flat = -1, flatCount = 0;
            for (int i = 0; i < 3; i++) {
                mn[i] = Math.min(from[i], to[i]) - inflate;
                mx[i] = Math.max(from[i], to[i]) + inflate;
                if (mx[i] - mn[i] < EPS) {
                    flat = i;
                    flatCount++;
                }
            }
            if (flatCount >= 2) return; // Linie/Punkt: keine Flaeche
            JsonObject faces = obj(e, "faces");
            if (faces == null) return;

            float[] org = vec3(e, null, "origin"), rot = vec3(e, null, "rotation");
            Matrix4f rm = null;
            if (rot[0] != 0f || rot[1] != 0f || rot[2] != 0f) {
                // Blockbench: Euler-Reihenfolge ZYX (X zuerst auf den Vertex), um den Cube-Pivot
                rm = new Matrix4f().translate(org[0], org[1], org[2])
                        .rotateZ(rot[2] * DEG).rotateY(rot[1] * DEG).rotateX(rot[0] * DEG)
                        .translate(-org[0], -org[1], -org[2]);
            }

            Vector3f v = new Vector3f();
            boolean planeDone = false;
            for (int d = 0; d < 6; d++) {
                // duenne Ebene: nur die beiden Flaechen auf der flachen Achse, und davon nur die erste gueltige
                // (sonst liegen zwei Quads deckungsgleich und flackern)
                if (flat >= 0 && (FACE_AXIS[d] != flat || planeDone)) continue;
                JsonObject face = obj(faces, FACE_NAMES[d]);
                if (face == null) continue;
                JsonElement tex = face.get("texture");
                if (tex == null || !tex.isJsonPrimitive() || !tex.getAsJsonPrimitive().isNumber()) continue; // null/false = aus
                JsonArray uv = arr(face, "uv");
                if (uv == null || uv.size() < 4) continue;

                float u1 = uv.get(0).getAsFloat() / resW, v1 = uv.get(1).getAsFloat() / resH;
                float u2 = uv.get(2).getAsFloat() / resW, v2 = uv.get(3).getAsFloat() / resH;
                // Ecken der UV-Flaeche: links oben, links unten, rechts unten, rechts oben; v = 0 ist oben in der Textur
                float[] cu = {u1, u1, u2, u2};
                float[] cv = {v1, v2, v2, v1};
                int rotation = Math.floorMod(Math.round(num(face, "rotation", 0f)), 360) / 90;

                for (int i = 0; i < 4; i++) {
                    int[] c = FACE_VERTS[d][i];
                    float x = c[0] == 0 ? mn[0] : mx[0];
                    float y = c[1] == 0 ? mn[1] : mx[1];
                    float z = c[2] == 0 ? mn[2] : mx[2];
                    if (rm != null) {
                        rm.transformPosition(x, y, z, v);
                        x = v.x;
                        y = v.y;
                        z = v.z;
                    }
                    int k = (i + rotation) & 3; // wie in Minecraft: Rotation verschiebt die UV-Ecken
                    out.add(x, y, z, cu[k], cv[k]);
                }
                float[] n = FACE_NORMAL[d];
                if (rm != null) {
                    rm.transformDirection(n[0], n[1], n[2], v);
                    out.add(v.x, v.y, v.z);
                } else {
                    out.add(n[0], n[1], n[2]);
                }
                quads++;
                if (flat >= 0) planeDone = true;
            }
        }
    }

    /** Minimale wachsende float-Liste (nur beim Laden) */
    private static final class FloatList {
        float[] data = new float[256];
        int size;

        void ensure(int extra) {
            if (size + extra > data.length) data = Arrays.copyOf(data, Math.max(data.length * 2, size + extra));
        }

        void add(float x, float y, float z, float u, float v) {
            ensure(5);
            data[size++] = x;
            data[size++] = y;
            data[size++] = z;
            data[size++] = u;
            data[size++] = v;
        }

        void add(float x, float y, float z) {
            ensure(3);
            data[size++] = x;
            data[size++] = y;
            data[size++] = z;
        }

        float[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }

    private static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static float num(JsonObject o, String key, float def) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;
        try {
            return e.getAsFloat();
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    /** Boolean aus dem Eintrag, sonst aus der Definition (Blockbench 5 trennt Outliner und Gruppen-Daten) */
    private static boolean bool(JsonObject o, JsonObject fallback, String key, boolean def) {
        JsonElement e = o.get(key);
        if ((e == null || !e.isJsonPrimitive()) && fallback != null) e = fallback.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : def;
    }

    /** Vektor aus 3 Zahlen; fehlt er im Eintrag, wird die Definition benutzt, sonst (0, 0, 0) */
    private static float[] vec3(JsonObject o, JsonObject fallback, String key) {
        JsonElement e = o.get(key);
        if ((e == null || !e.isJsonArray()) && fallback != null) e = fallback.get(key);
        float[] r = new float[3];
        if (e != null && e.isJsonArray() && e.getAsJsonArray().size() >= 3) {
            JsonArray a = e.getAsJsonArray();
            for (int i = 0; i < 3; i++) r[i] = a.get(i).getAsFloat();
        }
        return r;
    }

    // ------------------------------------------------------------------------------------------ Rendern

    /**
     * Zeichnet einen Fluegel.
     *
     * @param base     Matrix der Ebene (Koerper, Schulterblatt-Versatz); wird nicht veraendert
     * @param side     +1 = rechter Fluegel wie im Modell, -1 = gespiegelter linker Fluegel
     * @param twoSided true = zusaetzlich jede Flaeche mit umgekehrter Windung und Normale (nur noetig, wenn die
     *                 Render-Pipeline Rueckseiten wegschneidet)
     */
    public void render(Matrix4f base, int side, Anim anim, boolean twoSided, QuadSink sink) {
        Scratch s = SCRATCH.get();
        s.ensure(stackSize);
        float sx = side / 16f;
        // Blockbench (x, y, z) -> Ebene (side * x / 16, -y / 16, z / 16); +1 / 16 Block Versatz von der Wirbelsaeule
        s.m[0].set(base).translate(sx, 0f, 0f).scale(sx, -1f / 16f, 1f / 16f);
        // diese Abbildung hat die Determinante -side: beim rechten Fluegel (side = +1) dreht sich die Windung um
        draw(root, 0, s, anim, side > 0, twoSided, sink);
    }

    /**
     * Zeichnet beide Fluegel (rechts wie im Modell, links gespiegelt) und sortiert alle Flaechen nach dem Abstand zur
     * Kamera (Ursprung des Raums von base), weit entfernte zuerst. Die Fluegel sind transluzent und schreiben Tiefe: ohne
     * Sortierung verdecken vordere, fast durchsichtige Flaechen die dahinter, je nach Blickrichtung (vorne/hinten).
     */
    public void renderSorted(Matrix4f base, Anim anim, boolean twoSided, QuadSink sink) {
        Scratch s = SCRATCH.get();
        s.vertCount = 0;
        render(base, 1, anim, twoSided, s.collector);
        render(base, -1, anim, twoSided, s.collector);
        s.flush(sink);
    }

    private void draw(Node n, int depth, Scratch s, Anim a, boolean reverse, boolean twoSided, QuadSink sink) {
        int d = depth;
        Matrix4f m = s.m[depth];
        boolean posed = a.clips != null && a.weights != null;
        float[] pose = s.pose;
        pose[0] = pose[1] = pose[2] = 0f;
        float[] grow = s.grow; // Abweichung der Skalierung von 1
        grow[0] = grow[1] = grow[2] = 0f;
        if (posed) {
            for (int i = 0; i < a.clips.length; i++) {
                Clip clip = a.weights[i] > 0.001f ? clips.get(a.clips[i]) : null;
                if (clip == null) continue;
                clip.add(clip.rotations(), n.uuid, a.seconds, a.weights[i], 0f, pose);
                clip.add(clip.scales(), n.uuid, a.seconds, a.weights[i], 1f, grow);
            }
        }
        boolean scaled = grow[0] != 0f || grow[1] != 0f || grow[2] != 0f;
        boolean moved = scaled || pose[0] != 0f || pose[1] != 0f || pose[2] != 0f;
        if (n.kind != K_OTHER || n.rest || moved) {
            d = depth + 1;
            m = s.m[d];
            m.set(s.m[depth]).translate(n.ox, n.oy, n.oz);
            // Animation sitzt im Raum der Elterngruppe, die Ruhe-Rotation des Modells wird zuerst auf die Vertices angewandt
            switch (posed ? K_OTHER : n.kind) {
                case K_WING -> {
                    m.rotateY(-a.open * DEG);
                    float roll = a.flapAmp * (float) Math.sin(a.flapPhase);
                    if (roll != 0f) m.rotateZ(roll * DEG);
                }
                case K_SEG -> {
                    if (n.seg >= 2 && a.flapAmp != 0f) {
                        int deep = n.seg - 1;
                        float amp = a.flapAmp * Math.min(0.3f * deep, 0.65f);
                        m.rotateZ(amp * (float) Math.sin(a.flapPhase - 0.75f * deep) * DEG);
                    }
                }
                case K_FEATHER -> {
                    if (a.swayAmp != 0f || a.flapAmp != 0f) {
                        float sway = a.swayAmp * (float) Math.sin(a.time * 0.11f + n.phase)
                                + a.flapAmp * 0.12f * (float) Math.sin(a.flapPhase - 1.1f - n.phase * 0.15f);
                        m.rotateZ(sway * DEG);
                    }
                }
                default -> { }
            }
            // wie in Blockbench: die Animation wird zur Ruhe-Drehung addiert
            if (moved) m.rotateZ(n.rz + pose[2] * DEG).rotateY(n.ry + pose[1] * DEG).rotateX(n.rx + pose[0] * DEG);
            else if (n.rest) m.rotateZ(n.rz).rotateY(n.ry).rotateX(n.rx);
            if (scaled) m.scale(1f + grow[0], 1f + grow[1], 1f + grow[2]);
            m.translate(-n.ox, -n.oy, -n.oz);
        }

        float[] q = n.quads;
        for (int i = 0; i < n.quadCount; i++) {
            int o = i * QUAD_FLOATS;
            m.transformDirection(q[o + NORMAL_OFFSET], q[o + NORMAL_OFFSET + 1], q[o + NORMAL_OFFSET + 2], s.n).normalize();
            float nx = s.n.x, ny = s.n.y, nz = s.n.z;
            emit(q, o, m, s.p, nx, ny, nz, reverse, sink);
            if (twoSided) emit(q, o, m, s.p, -nx, -ny, -nz, !reverse, sink);
        }
        for (Node c : n.children) draw(c, d, s, a, reverse, twoSided, sink);
    }

    private static void emit(float[] q, int o, Matrix4f m, Vector3f p, float nx, float ny, float nz, boolean reverse, QuadSink sink) {
        for (int k = 0; k < 4; k++) {
            int i = reverse ? (4 - k) & 3 : k; // 0, 3, 2, 1
            int b = o + i * 5;
            m.transformPosition(q[b], q[b + 1], q[b + 2], p);
            sink.vertex(p.x, p.y, p.z, q[b + 3], q[b + 4], nx, ny, nz);
        }
    }
}

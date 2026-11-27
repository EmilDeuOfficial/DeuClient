package de.erik.discordglass.gui;

import com.mojang.blaze3d.platform.NativeImage;
import eu.deusmp.deucore.DeuCore;
import eu.deusmp.deucore.DeuRender;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Server-Icons und Profilbilder von Discords Bildserver. Jedes Bild wird einmal geladen, rund zugeschnitten
 * und passend zur Anzeigegroesse verkleinert (ein Bildpunkt der Textur je Bildschirmpunkt).
 */
public final class DiscordImages {
   private static final int MAX_BYTES = 2_000_000;
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
   private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();
   /** nur im Zeichen-Thread benutzt */
   private static final Map<String, Source> SOURCES = new HashMap<>();
   private static final Map<String, Identifier> SIZED = new HashMap<>();
   private static int counter;

   private record Source(int width, int height, int[] pixels) {
   }

   private DiscordImages() {
   }

   /** Zeichnet das Bild rund an die Stelle; false, solange es noch nicht (oder gar nicht) geladen werden kann */
   public static boolean draw(GuiGraphicsExtractor g, String url, int x, int y, int size) {
      if (url == null || url.isEmpty()) {
         return false;
      }

      Source source = SOURCES.get(url);
      if (source == null) {
         request(url);
         return false;
      }

      int px = Math.max(4, (int)Math.round(size * DeuRender.totalScale(g)));
      Identifier id = SIZED.computeIfAbsent(url + "@" + px, k -> sized(source, px));
      g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0.0F, 0.0F, size, size, px, px, px, px, -1);
      return true;
   }

   /** Nur Bilder von Discord, als PNG in 64 Punkten (Minecraft liest nur PNG); sonst null */
   private static URI source(String url) {
      try {
         URI u = URI.create(url);
         String host = u.getHost();
         if (!"https".equals(u.getScheme()) || !("cdn.discordapp.com".equals(host) || "media.discordapp.net".equals(host))) {
            return null;
         }

         String path = u.getPath().replaceAll("\\.(webp|jpg|jpeg|gif)$", ".png");
         return new URI("https", host, path, "size=64", null);
      } catch (Exception e) {
         return null;
      }
   }

   private static void request(String url) {
      if (!REQUESTED.add(url)) {
         return;
      }

      URI uri = source(url);
      if (uri == null) {
         return;
      }

      Thread.ofVirtual().start(() -> {
         try {
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15L)).header("User-Agent", "DeuClient").build();
            HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            byte[] bytes = res.body();
            if (res.statusCode() != 200 || bytes.length > MAX_BYTES) {
               return;
            }

            Minecraft.getInstance().execute(() -> {
               try (NativeImage image = NativeImage.read(bytes)) {
                  int w = image.getWidth(), h = image.getHeight();
                  int[] pixels = new int[w * h];
                  for (int y = 0; y < h; y++) {
                     for (int x = 0; x < w; x++) pixels[y * w + x] = image.getPixel(x, y);
                  }

                  SOURCES.put(url, new Source(w, h, pixels));
               } catch (Exception e) {
                  DeuCore.LOGGER.debug("Discord-Bild nicht lesbar: {}", e.toString());
               }
            });
         } catch (Exception e) {
            DeuCore.LOGGER.debug("Discord-Bild nicht geladen: {}", e.toString());
         }
      });
   }

   /** Verkleinert durch Mitteln der Bildpunkte und schneidet mit weichem Rand rund zu */
   private static Identifier sized(Source s, int px) {
      NativeImage out = new NativeImage(px, px, true);
      double kx = (double)s.width() / px, ky = (double)s.height() / px, radius = px / 2.0;

      for (int y = 0; y < px; y++) {
         for (int x = 0; x < px; x++) {
            int x0 = (int)(x * kx), x1 = Math.max(x0 + 1, (int)Math.ceil((x + 1) * kx));
            int y0 = (int)(y * ky), y1 = Math.max(y0 + 1, (int)Math.ceil((y + 1) * ky));
            long a = 0, r = 0, g = 0, b = 0, n = 0;

            for (int sy = y0; sy < Math.min(y1, s.height()); sy++) {
               for (int sx = x0; sx < Math.min(x1, s.width()); sx++) {
                  int c = s.pixels()[sy * s.width() + sx];
                  int ca = c >>> 24;
                  a += ca;
                  r += (long)((c >> 16) & 255) * ca;
                  g += (long)((c >> 8) & 255) * ca;
                  b += (long)(c & 255) * ca;
                  n++;
               }
            }

            double cover = Math.max(0, Math.min(1, radius - Math.hypot(x + 0.5 - radius, y + 0.5 - radius) + 0.5));
            int alpha = n == 0 ? 0 : (int)Math.round((double)a / n * cover);
            int color = a == 0 ? 0 : (int)(r / a) << 16 | (int)(g / a) << 8 | (int)(b / a);
            out.setPixel(x, y, alpha << 24 | color);
         }
      }

      Identifier id = Identifier.fromNamespaceAndPath(DeuCore.MOD_ID, "dynamic/discord_" + counter++);
      Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, out));
      return id;
   }
}

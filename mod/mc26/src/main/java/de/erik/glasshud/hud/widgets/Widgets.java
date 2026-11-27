package de.erik.glasshud.hud.widgets;

import de.erik.glasshud.config.HudConfig;
import java.util.List;

public final class Widgets {
   public static final List<Widget> ALL = List.of(
      new CoordsWidget(),
      new FpsWidget(),
      new PingWidget(),
      new SpeedWidget(),
      new CompassWidget(),
      new ClockWidget(),
      new PotionsWidget(),
      new ArmorWidget(),
      new KeysWidget()
   );

   private Widgets() {
   }

   public static Widget byId(String id) {
      for (Widget w : ALL) {
         if (w.id.equals(id)) {
            return w;
         }
      }

      return null;
   }

   public static HudConfig.W defaultFor(String id) {
      Widget w = byId(id);
      return w == null ? new HudConfig.W() : w.defaults();
   }
}

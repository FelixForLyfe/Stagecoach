package net.feelix.stagecoach.client.scene;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.feelix.stagecoach.Stagecoach;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A resource-pack travel screen: the built-in road, one image, or scrolling layers over a sky (colours are 0xAARRGGBB). */
public record Scene(@Nullable String builtin, int skyTop, int skyBottom, List<Layer> layers, boolean coach,
                    @Nullable String ambientSound, @Nullable String image, boolean cover) {
    public static final String ROAD = "road";
    public static final Scene BUILTIN_ROAD = new Scene(ROAD, 0, 0, List.of(), true, Stagecoach.MOD_ID + ":journey.loop", null, true);

    /** {@code y} is the layer's top edge as a share of the screen height; {@code fill} paints below the layer. */
    public record Layer(String texture, float speed, float y, float scale, @Nullable Integer fill) {
    }

    public boolean isRoad() {
        return ROAD.equals(builtin);
    }

    public static Scene parse(JsonObject json) {
        boolean coach = !json.has("coach") || json.get("coach").getAsBoolean();
        String sound = json.has("ambient_sound") ? json.get("ambient_sound").getAsString() : null;
        if (json.has("builtin")) {
            String builtin = json.get("builtin").getAsString();
            if (!ROAD.equals(builtin)) throw new IllegalArgumentException("\"builtin\" can only be \"road\"");
            return new Scene(ROAD, 0, 0, List.of(), coach, sound, null, true);
        }
        if (json.has("image")) {
            String fit = json.has("fit") ? json.get("fit").getAsString() : "cover";
            if (!fit.equals("cover") && !fit.equals("contain")) throw new IllegalArgumentException("\"fit\" must be cover or contain");
            return new Scene(null, 0xFF000000, 0xFF000000, List.of(), coach, sound, json.get("image").getAsString(), fit.equals("cover"));
        }
        int top = 0xFF4F86D6, bottom = 0xFFA8CCF0;
        if (json.has("sky")) {
            JsonObject sky = json.getAsJsonObject("sky");
            if (sky.has("top")) top = colour(sky.get("top"));
            if (sky.has("bottom")) bottom = colour(sky.get("bottom"));
        }
        List<Layer> layers = new ArrayList<>();
        if (json.has("layers")) {
            for (JsonElement e : json.getAsJsonArray("layers")) {
                JsonObject l = e.getAsJsonObject();
                if (!l.has("texture")) throw new IllegalArgumentException("every layer needs a \"texture\"");
                float scale = l.has("scale") ? l.get("scale").getAsFloat() : 1f;
                if (!(scale > 0 && scale <= 16)) throw new IllegalArgumentException("a layer's \"scale\" must be above 0 and at most 16");
                layers.add(new Layer(l.get("texture").getAsString(), l.has("speed") ? l.get("speed").getAsFloat() : 1f,
                        l.has("y") ? l.get("y").getAsFloat() : 0.5f, scale, l.has("fill") ? colour(l.get("fill")) : null));
            }
        }
        return new Scene(null, top, bottom, List.copyOf(layers), coach, sound, null, true);
    }

    /** "#rrggbb" or "#aarrggbb". */
    static int colour(JsonElement e) {
        String s = e.getAsString().trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6 && s.length() != 8) throw new IllegalArgumentException("colours are written #rrggbb, got " + e.getAsString());
        long v = Long.parseLong(s, 16);
        return (int) (s.length() == 6 ? 0xFF000000L | v : v);
    }
}

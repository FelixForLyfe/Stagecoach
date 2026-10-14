package net.feelix.stagecoach.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.feelix.stagecoach.travel.Landscape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Patterns like "{prefix}{suffix} Crossing" filled from the word lists named in braces. */
public record NamePool(List<String> patterns, Map<String, List<String>> words) {
    private static final Pattern SLOT = Pattern.compile("\\{([a-z_]+)}");
    private static final int LAYER = 11;

    public static NamePool parse(JsonObject json) {
        List<String> patterns = strings(json, "patterns");
        if (patterns.isEmpty()) throw new IllegalArgumentException("\"patterns\" must be a non-empty list of text");
        Map<String, List<String>> words = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            if (e.getKey().equals("patterns") || !e.getValue().isJsonArray()) continue;
            List<String> list = strings(json, e.getKey());
            if (!list.isEmpty()) words.put(e.getKey(), list);
        }
        for (String pattern : patterns) {
            Matcher m = SLOT.matcher(pattern);
            while (m.find()) {
                if (!words.containsKey(m.group(1))) {
                    throw new IllegalArgumentException("pattern \"" + pattern + "\" uses {" + m.group(1) + "} but there is no \"" + m.group(1) + "\" list");
                }
            }
        }
        return new NamePool(List.copyOf(patterns), Map.copyOf(words));
    }

    private static List<String> strings(JsonObject json, String key) {
        List<String> out = new ArrayList<>();
        JsonElement e = json.get(key);
        if (e == null || !e.isJsonArray()) return out;
        for (JsonElement item : e.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("\"" + key + "\" must only hold text");
            }
            out.add(item.getAsString());
        }
        return out;
    }

    /** The same seed and attempt always give the same name. */
    public String generate(long seed, int attempt) {
        long base = Landscape.hash(seed, LAYER, attempt);
        String pattern = patterns.get(pick(base, 0, patterns.size()));
        Matcher m = SLOT.matcher(pattern);
        StringBuilder sb = new StringBuilder();
        int slot = 1;
        while (m.find()) {
            List<String> list = words.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(list.get(pick(base, slot++, list.size()))));
        }
        m.appendTail(sb);
        return sb.toString().trim();
    }

    public long combinations() {
        long total = 0;
        for (String pattern : patterns) {
            long n = 1;
            Matcher m = SLOT.matcher(pattern);
            while (m.find()) n *= words.get(m.group(1)).size();
            total += n;
        }
        return total;
    }

    private static int pick(long base, int slot, int size) {
        return (int) Math.floorMod(Landscape.hash(base, LAYER, slot), (long) size);
    }
}

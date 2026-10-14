package net.feelix.stagecoach.bukkit.travel;

public final class CoachNames {
    private CoachNames() {}

    /** Null if not allowed; length counts code points, so an emoji is one character. */
    public static String clean(String raw, int maxLength) {
        if (raw == null) return null;
        StringBuilder sb = new StringBuilder(raw.length());
        boolean space = false;
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§') { // a formatting code is two characters: the section sign and the code after it
                if (i < raw.length()) i += Character.charCount(raw.codePointAt(i));
                continue;
            }
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT) continue;
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                space = true;
                continue;
            }
            if (space && sb.length() > 0) sb.append(' ');
            space = false;
            sb.appendCodePoint(cp);
        }
        String s = sb.toString();
        int len = s.codePointCount(0, s.length());
        return len >= 1 && len <= maxLength ? s : null;
    }

    public static String defaultName(long number) {
        return "Stagecoach " + number;
    }
}

package net.feelix.stagecoach.data;

import com.google.gson.JsonParser;
import net.feelix.stagecoach.travel.CoachNames;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamePoolTest {
    private static final Path POOLS = Path.of("common/src/main/resources/data/stagecoach/stagecoach/name_pools");
    private static final Pattern SLOT = Pattern.compile("\\{([a-z_]+)}");
    private static final int MAX_NAME = 24;

    /** Places from games, books, films and shows that a generated stop must never be called. */
    private static final Set<String> FICTIONAL = Set.copyOf(List.of(
            // The Elder Scrolls
            "whiterun", "riverwood", "falkreath", "markarth", "solitude", "windhelm", "riften", "dawnstar", "morthal",
            "winterhold", "ivarstead", "rorikstead", "helgen", "kynesgrove", "karthwasten", "dragon bridge", "shor's stone",
            "darkwater crossing", "stonehills", "heartwood mill", "mixwater mill", "goldenglow", "blackreach", "chorrol",
            "bruma", "cheydinhal", "skingrad", "kvatch", "anvil", "leyawiin", "bravil", "aleswell", "hackdirt", "balmora",
            "seyda neen", "pelagiad", "caldera", "daggerfall", "wayrest", "evermor", "shornhelm", "camlorn", "northpoint",
            "mournhold", "stormhaven", "rivenspire", "glenumbra", "stonefalls", "blackwood", "craglorn", "high rock",
            "hammerfell", "elsweyr", "orsinium", "riverhold", "dragonstar", "ebonheart", "silverhome",
            // A Song of Ice and Fire
            "winterfell", "riverrun", "highgarden", "casterly rock", "storm's end", "harrenhal", "oldtown", "lannisport",
            "pyke", "sunspear", "starfall", "deepwood motte", "moat cailin", "white harbor", "eyrie", "gulltown",
            "duskendale", "maidenpool", "saltpans", "stoney sept", "fairmarket", "seagard", "greywater", "oldstones",
            "ashford", "brightwater", "bitterbridge", "goldengrove", "horn hill", "blackhaven", "nightsong", "summerhall",
            "rosby", "stokeworth", "hayford", "raventree", "darry", "acorn hall", "pinkmaiden", "sevenstreams", "ironoaks",
            "longbow hall", "greenstone", "mistwood", "evenfall", "rainwood", "tumbleton", "sandstone", "yronwood",
            "godsgrace", "barrowton", "hornwood", "karhold", "last hearth", "dreadfort", "highpoint", "wolfswood",
            "kingsgrave", "blackmont", "skyreach", "hellholt", "brimstone", "ironrath", "kingswood", "redfort", "runestone",
            "coldmoat", "felwood", "grassy vale", "stonehelm", "crakehall", "deep den", "silverhill", "golden tooth",
            "wickenden", "harvest hall", "bandallon", "blackcrown", "brightwater keep", "cider hall", "honeyholt",
            "oakenshield", "longtable", "tumbleton", "greenshield", "sweetport", "weeping town", "griffin's roost",
            "rain house", "mistwood", "harlaw", "hammerhorn", "volmark", "sealskin point", "lordsport", "pebbleton",
            "stonehouse", "saltcliffe", "seaguard", "fairmarket", "crossroads inn", "inn at the crossroads",
            // Tolkien
            "bree", "bywater", "hobbiton", "michel delving", "frogmorton", "tuckborough", "buckland", "crickhollow",
            "staddle", "combe", "archet", "rivendell", "edoras", "dale", "esgaroth", "minas tirith", "osgiliath", "isengard",
            "dunharrow", "weathertop", "fornost", "pelargir", "dol amroth", "linhir", "stock", "woodhall", "whitfurrows",
            "overhill", "waymeet", "greenholm", "brandy hall", "hardbottle", "nobottle", "little delving", "gamwich",
            "tighfield", "longbottom", "pincup", "needlehole", "oatbarton", "brockenborings", "mirkwood", "grey havens",
            "greyhaven", "lake-town", "the prancing pony", "the green dragon", "the golden perch", "the ivy bush",
            // World of Warcraft
            "goldshire", "stormwind", "westfall", "darkshire", "lakeshire", "moonbrook", "sentinel hill", "southshore",
            "tarren mill", "hillsbrad", "brill", "ashenvale", "astranaar", "booty bay", "thelsamar", "menethil",
            "hammerfall", "stonard", "kharanos", "thunder bluff", "dalaran", "lordaeron", "andorhal", "hearthglen",
            "darrowshire", "silverpine", "ambermill", "feathermoon", "theramore", "ratchet", "everlook", "gadgetzan",
            "northshire", "eastvale", "duskwood", "elwynn", "stormheim", "ironforge", "dun morogh", "redridge",
            "shadowglen", "dolanaar", "auberdine", "splintertree", "sun rock", "sunrock retreat", "freewind post",
            "camp taurajo", "orgrimmar", "undercity", "silvermoon", "fairbreeze", "tranquillien", "falconwing",
            // The Witcher
            "novigrad", "oxenfurt", "vizima", "white orchard", "blaviken", "kaer morhen", "toussaint", "beauclair",
            "lindenvale", "oreton", "mulbrydale", "downwarren", "heatherton", "blackbough", "midcopse", "crow's perch",
            "flotsam", "vergen", "vengerberg", "cintra", "kovir", "brokilon", "claywich", "lornruk", "benek", "hindhold",
            "lurtch", "malleore", "toderas", "sarborn", "carsten", "rinde", "ard carraigh", "maribor", "ellander",
            // Fable, Dragon Age, Dark Souls, Elden Ring, Diablo, Baldur's Gate and Forgotten Realms
            "bowerstone", "oakvale", "brightwall", "mourningwood", "millfields", "rookridge", "silverpines", "westcliff",
            "knothole glade", "witchwood", "darkwood", "snowspire", "twinblade", "bloodstone", "mistpeak", "lychfield",
            "greatwood", "lothering", "redcliffe", "haven", "denerim", "highever", "gwaren", "amaranthine", "honnleath",
            "kirkwall", "sundermount", "crestwood", "skyhold", "val royeaux", "ostagar", "firelink", "anor londo",
            "blighttown", "majula", "heide", "drangleic", "lothric", "irithyll", "limgrave", "liurnia", "caelid",
            "stormveil", "leyndell", "morne", "murkwater", "tristram", "kurast", "harrogath", "caldeum", "westmarch",
            "beregost", "nashkel", "candlekeep", "trademeet", "neverwinter", "phandalin", "waterdeep", "daggerford",
            "longsaddle", "triboar", "leilon", "red larch", "yartar", "silverymoon", "luskan", "mirabar", "bryn shander",
            "targos", "easthaven", "lonelywood", "termalaine", "good mead", "elturel", "baldur's gate", "saltmarsh",
            "greenest", "hommlet", "nulb", "barovia", "vallaki", "krezk",
            // RuneScape, Pokemon, Zelda, Kingdom Come, Red Dead, Fallout, Hollow Knight, others
            "lumbridge", "varrock", "falador", "draynor", "ardougne", "catherby", "taverley", "burthorpe", "rimmington",
            "edgeville", "canifis", "yanille", "brimhaven", "rellekka", "keldagrim", "hosidius", "pallet town",
            "viridian", "pewter", "cerulean", "vermilion", "lavender town", "celadon", "fuchsia", "saffron", "cinnabar",
            "littleroot", "oldale", "petalburg", "rustboro", "dewford", "slateport", "mauville", "verdanturf",
            "fallarbor", "lavaridge", "fortree", "lilycove", "mossdeep", "sootopolis", "twinleaf", "sandgem", "jubilife",
            "oreburgh", "floaroma", "eterna", "hearthome", "solaceon", "veilstone", "pastoria", "celestic", "canalave",
            "snowpoint", "sunyshore", "nuvema", "accumula", "striaton", "nacrene", "castelia", "nimbasa", "driftveil",
            "mistralton", "icirrus", "opelucid", "lacunosa", "undella", "aspertia", "floccesy", "virbank", "vaniville",
            "santalune", "lumiose", "camphrier", "ambrette", "cyllage", "shalour", "coumarine", "laverre", "dendemille",
            "anistar", "couriway", "snowbelle", "postwick", "wedgehurst", "motostoke", "turffield", "hulbury",
            "hammerlocke", "ballonlea", "circhester", "spikemuth", "wyndon", "mesagoza", "artazon", "levincia",
            "kakariko", "hateno", "lurelin", "tarrey town", "ordon", "rattay", "skalitz", "sasau", "talmberg",
            "ledetchko", "uzhitz", "rovna", "samopesh", "neuhof", "valentine", "rhodes", "saint denis", "strawberry",
            "blackwater", "armadillo", "tumbleweed", "annesburg", "van horn", "emerald ranch", "lagras", "colter",
            "wapiti", "hennigan's stead", "twin rocks", "pike's basin", "megaton", "rivet city", "diamond city",
            "goodneighbor", "sanctuary hills", "goodsprings", "primm", "novac", "freeside", "nipton", "shady sands",
            "junktown", "dirtmouth", "greenpath", "ravenholm", "dunwall", "rapture", "springfield", "seahaven",
            "anchorhead", "mos eisley", "hogsmeade", "godric's hollow", "little whinging", "ottery st catchpole",
            "little hangleton", "tinworth", "beaversdam", "cair paravel", "beruna", "anvard", "ankh-morpork", "lancre",
            "sto lat", "quirm", "pelican town", "zuzu city", "stardew", "stormhold", "wall", "castle rock", "derry",
            "twin peaks", "hill valley", "sunnydale", "stars hollow", "storybrooke", "smallville", "gotham", "metropolis",
            "riverdale", "hawkins", "cloverfield", "northgard", "icemark", "fallowmere", "hearthfire", "oakhurst",
            "the bee and barb", "the bannered mare", "the winking skeever", "the sleeping giant", "the leaky cauldron",
            "the three broomsticks", "the hog's head", "the mended drum", "the hanged man", "the drunken huntsman",
            "the ragged flagon", "the golden goose", "the jovial juggler", "the splurging sow", "the elfsong",
            "the blushing mermaid", "the friendly arm", "the pig and whistle", "the cow and corset", "the slaughtered lamb"));

    private static final List<String> BAD = List.of("fag", "nigger", "nigga", "rape", "slut", "cunt", "dick", "cock", "shit", "piss", "twat",
            "wank", "kike", "chink", "spic", "gook", "coon", "homo", "tits", "whore", "bitch", "bastard", "dyke", "paki", "retard",
            "nazi", "jizz", "porn", "anal", "sex", "cum");

    @Test
    void everyPoolHasEnoughNamesNoneFictionalOrRude() throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(POOLS)) {
            files = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertTrue(files.size() >= 12, "expected the twelve default pools, found " + files.size());
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            NamePool pool = NamePool.parse(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
            Set<String> fitting = new HashSet<>();
            for (String name : all(pool)) {
                String lower = name.toLowerCase(Locale.ROOT);
                String base = lower.replaceAll(" (crossing|stop|inn)$", "");
                if (FICTIONAL.contains(lower) || FICTIONAL.contains(base)) problems.add(file.getFileName() + ": " + name + " (fiction)");
                for (String bad : BAD) if (lower.contains(bad)) problems.add(file.getFileName() + ": " + name + " (" + bad + ")");
                if (CoachNames.clean(name, MAX_NAME) != null) fitting.add(name);
            }
            if (fitting.size() < 200) problems.add(file.getFileName() + ": only " + fitting.size() + " names of up to " + MAX_NAME + " letters");
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void theSameSeedGivesTheSameName() throws IOException {
        NamePool pool = NamePool.parse(JsonParser.parseString(Files.readString(POOLS.resolve("pastoral.json"))).getAsJsonObject());
        for (long seed = -50; seed < 50; seed++) assertEquals(pool.generate(seed * 7919, 3), pool.generate(seed * 7919, 3));
        Set<String> spread = new HashSet<>();
        for (long seed = 0; seed < 400; seed++) spread.add(pool.generate(seed * 1_000_003L, 0));
        assertTrue(spread.size() > 150, "400 seeds gave only " + spread.size() + " names");
    }

    @Test
    void combinationsCountEveryPattern() {
        NamePool pool = new NamePool(List.of("{a}{b}", "{a} Stop"), Map.of("a", List.of("X", "Y"), "b", List.of("1", "2", "3")));
        assertEquals(8, pool.combinations());
        assertEquals(8, all(pool).size());
    }

    private static List<String> all(NamePool pool) {
        List<String> out = new ArrayList<>();
        for (String pattern : pool.patterns()) expand(pattern, pool.words(), out);
        return out;
    }

    private static void expand(String pattern, Map<String, List<String>> words, List<String> out) {
        Matcher m = SLOT.matcher(pattern);
        if (!m.find()) {
            out.add(pattern);
            return;
        }
        for (String w : words.get(m.group(1))) expand(pattern.substring(0, m.start()) + w + pattern.substring(m.end()), words, out);
    }
}

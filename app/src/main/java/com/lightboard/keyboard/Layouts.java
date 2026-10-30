package com.lightboard.keyboard;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Раскладки языков (только клавиши, без картинок и шрифтов). Словари (подсказки, автоисправление, непрерывный ввод)
 * есть у en и ru; у остальных языков эти функции выключены автоматически, остаются сами клавиши и долгое нажатие на
 * буквы с диакритикой. Ряды записаны слева направо, как на экране. Весь класс — несколько килобайт.
 */
final class Layouts {
    private Layouts() { }

    static final class L {
        final String id, name, space, tag, cur, r1, r2, r3;
        final boolean lex, ru;
        final int cols;                 // сколько клавиш помещается в самом широком ряду (с учётом shift/стереть в третьем)
        private final String[] alts;    // «буква:вариант|вариант»
        private final Locale loc;       // для регистра (турецкая «i» -> «İ»)

        L(String id, String name, String space, String tag, String cur, String r1, String r2, String r3, String alts, boolean lex, Locale loc) {
            this.id = id; this.name = name; this.space = space; this.tag = tag; this.cur = cur;
            this.r1 = r1; this.r2 = r2; this.r3 = r3;
            this.lex = lex; this.ru = id.equals("ru"); this.loc = loc;
            this.alts = alts.isEmpty() ? new String[0] : alts.split(";");
            this.cols = Math.max(Math.max(r1.length(), r2.length()), r3.length() + 2);
        }

        @Override public boolean equals(Object o) { return o instanceof L && ((L) o).id.equals(id); } // после импорта словаря объект раскладки пересоздаётся: сравниваем по id
        @Override public int hashCode() { return id.hashCode(); }

        /** Варианты при долгом нажатии (через «|») или null. */
        String alt(char c) {
            for (String a : alts) if (a.charAt(0) == c) return a.substring(2);
            return null;
        }

        String upper(String s) { return s.toUpperCase(loc); }

        /** Свой словарь (подсказки, исправление, свайп): английский и (в сборке с русским словарём) русский независимо от языка телефона; у остальных — системная проверка орфографии. */
        boolean own() { return Lexicon.has(id); }

        /** Три буквенных ряда планшетной раскладки: у английского и русского как на ПК-клавиатуре Gboard, у остальных — телефонные ряды плюс «,.». */
        String[] tabletRows() {
            if (id.equals("en")) return new String[]{"qwertyuiop[]", "asdfghjkl;'", "zxcvbnm,./"};
            if (id.equals("ru")) return new String[]{"йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю,."};
            return new String[]{r1, r2, r3 + ",."};
        }

        /** Верхний регистр, если он не меняет длину (ß -> SS не годится: оставляем как есть). */
        String upperKeep(String s) {
            String u = s.toUpperCase(loc);
            return u.length() == s.length() ? u : s;
        }
    }

    private static final String EN_ALT = "a:á;e:é;i:í;o:ó;u:ú;y:ý";
    private static final String RU_ALT = "а:а\u0301;е:ё|е\u0301;и:и\u0301;о:о\u0301;у:у\u0301;ы:ы\u0301;э:э\u0301;ю:ю\u0301;я:я\u0301;ь:ъ";
    private static final String QW = "qwertyuiop", AS = "asdfghjkl", ZX = "zxcvbnm";

    static final L EN = new L("en", "English", "space", "en-US", "$", QW, AS, ZX, EN_ALT, true, Locale.ROOT);
    static final L RU = new L("ru", "Русский", "Пробел", "ru-RU", "₽", "йцукенгшщзх", "фывапролджэ", "ячсмитьбю", RU_ALT, true, Locale.ROOT);

    static final L[] ALL = {
        EN, RU,
        new L("uk", "Українська", "Пробіл", "uk-UA", "₴", "йцукенгшщзхї", "фівапролджє", "ячсмитьбю", "г:ґ;ь:'", false, Locale.ROOT),
        new L("be", "Беларуская", "Прабел", "be-BY", "$", "йцукенгшўзх'", "фывапролджэ", "ячсмітьбю", "е:ё", false, Locale.ROOT),
        new L("de", "Deutsch", "Leerzeichen", "de-DE", "€", "qwertzuiopü", "asdfghjklöä", "yxcvbnm", "s:ß;e:é|è|ê|ë;a:à|á|â;o:ó|ò|ô;u:ú|ù|û;i:í|ì|î", false, Locale.ROOT),
        new L("fr", "Français", "espace", "fr-FR", "€", "azertyuiop", "qsdfghjklm", "wxcvbn", "e:é|è|ê|ë;a:à|â|æ;u:ù|û|ü;i:î|ï;o:ô|œ;c:ç;y:ÿ", false, Locale.ROOT),
        new L("es", "Español", "espacio", "es-ES", "€", QW, "asdfghjklñ", ZX, "a:á|à|ä|â;e:é|è|ë|ê;i:í|ï|ì|î;o:ó|ö|ò|ô;u:ú|ü|ù|û;c:ç", false, Locale.ROOT),
        new L("it", "Italiano", "spazio", "it-IT", "€", QW, AS, ZX, "a:à|á;e:è|é|ê|ë;i:ì|í|î;o:ò|ó|ô;u:ù|ú|û", false, Locale.ROOT),
        new L("pt", "Português", "espaço", "pt-BR", "$", QW, AS, ZX, "a:á|à|â|ã|ä;e:é|ê|è;i:í|ì;o:ó|ô|õ|ò;u:ú|ü|ù;c:ç", false, Locale.ROOT),
        new L("pl", "Polski", "spacja", "pl-PL", "$", QW, AS, ZX, "a:ą;c:ć;e:ę;l:ł;n:ń;o:ó;s:ś;z:ż|ź", false, Locale.ROOT),
        new L("tr", "Türkçe", "boşluk", "tr-TR", "₺", "qwertyuıopğü", "asdfghjklşi", "zxcvbnmöç", "a:â;e:ê;i:î;o:ô;u:û", false, Locale.forLanguageTag("tr")),
        new L("nl", "Nederlands", "spatie", "nl-NL", "€", QW, AS, ZX, "e:é|è|ê|ë;a:á|à|ä|â;i:í|ï|î;o:ó|ö|ô;u:ú|ü|û", false, Locale.ROOT),
        new L("cs", "Čeština", "mezerník", "cs-CZ", "$", "qwertzuiop", AS, "yxcvbnm", "a:á;c:č|ć;d:ď;e:é|ě;i:í;n:ň;o:ó;r:ř;s:š;t:ť;u:ú|ů;y:ý;z:ž", false, Locale.ROOT),
        new L("sk", "Slovenčina", "medzerník", "sk-SK", "€", "qwertzuiop", AS, "yxcvbnm", "a:á|ä;c:č|ć;d:ď;e:é;i:í;l:ĺ|ľ;n:ň;o:ó|ô;r:ŕ|ř;s:š;t:ť;u:ú;y:ý;z:ž", false, Locale.ROOT),
        new L("hu", "Magyar", "szóköz", "hu-HU", "$", "qwertzuiop", AS, "yxcvbnm", "a:á;e:é;i:í;o:ó|ö|ő;u:ú|ü|ű", false, Locale.ROOT),
        new L("ro", "Română", "spațiu", "ro-RO", "$", QW, AS, ZX, "a:ă|â;i:î;s:ș;t:ț", false, Locale.ROOT),
        new L("hr", "Hrvatski", "razmak", "hr-HR", "€", "qwertzuiop", AS, "yxcvbnm", "c:č|ć;d:đ;s:š;z:ž", false, Locale.ROOT),
        new L("sv", "Svenska", "mellanslag", "sv-SE", "$", "qwertyuiopå", "asdfghjklöä", ZX, "e:é;u:ü;a:à", false, Locale.ROOT),
        new L("da", "Dansk", "mellemrum", "da-DK", "$", "qwertyuiopå", "asdfghjklæø", ZX, "e:é;a:á", false, Locale.ROOT),
        new L("nb", "Norsk", "mellomrom", "nb-NO", "$", "qwertyuiopå", "asdfghjkløæ", ZX, "e:é|è|ê;o:ó|ô;a:á|à|ä", false, Locale.ROOT),
        new L("fi", "Suomi", "väli", "fi-FI", "€", "qwertyuiopå", "asdfghjklöä", ZX, "s:š;z:ž", false, Locale.ROOT),
        new L("id", "Indonesia", "spasi", "id-ID", "$", QW, AS, ZX, "e:é", false, Locale.ROOT),
        new L("el", "Ελληνικά", "διάστημα", "el-GR", "€", "ςερτυθιοπ", "ασδφγηξκλ", "ζχψωβνμ", "α:ά;ε:έ;η:ή;ι:ί|ϊ;ο:ό;υ:ύ|ϋ;ω:ώ", false, Locale.ROOT),
        new L("he", "עברית", "רווח", "he-IL", "₪", "קראטוןםפ", "שדגכעיחלךף", "זסבהנמצתץ", "", false, Locale.ROOT),
        new L("ar", "العربية", "مسافة", "ar-SA", "$", "ضصثقفغعهخحج", "شسيبلاتنمكط", "دذرزظوةءئ", "ا:أ|إ|آ;و:ؤ;ي:ى|ئ;ل:لا", false, Locale.ROOT),
        new L("fa", "فارسی", "فاصله", "fa-IR", "$", "ضصثقفغعهخحج\u0686", "شسی\u0628لاتنم\u06A9\u06AF".replace("ی", "\u06CC").replace("\u0628", "ب"), "ظطزرذد\u067E\u0648".replace("\u0648", "و"), "ا:آ|أ;و:ؤ;\u06CC:ئ;ز:\u0698;ه:ة", false, Locale.ROOT),
    };

    /** Знаки цифровой клавиатуры (A, B, C, D) для числа со знаком или точкой (4), телефона (2) и даты/времени (3). */
    private static final String[] PAD_NUM = {",", ".", "-", "+"}, PAD_PHONE = {"*", "#", "+", "-"}, PAD_DATE = {"/", ":", "-", " "};

    static String[] pad(int mode) { return mode == 2 ? PAD_PHONE : mode == 3 ? PAD_DATE : PAD_NUM; }

    static L byId(String id) {
        if (id != null) for (L l : ALL) if (l.id.equals(id)) return l;
        return null;
    }

    /** Код языка из локали системы («ru_RU», «iw», «in», «no»…) -> раскладка или null. */
    // ---------- языки без своей раскладки: латиница -> QWERTY, кириллица -> ЙЦУКЕН ----------
    private static final String GEN_LATIN = ",ca,et,lv,lt,sl,sq,is,af,ms,tl,eo,eu,gl,vi,bs,br,cy,ga,mt,lb,sw,uz,az,fo,gd,so,zu,xh,ha,yo,mg,ht,ku,mi,fy,tk,",
            GEN_CYR = ",bg,sr,mk,kk,ky,mn,tg,tt,ba,ce,cv,os,ab,kv,",
            GEN_AUTO = ",hy,ka,th,hi,bn,ta,te,ml,si,ur,"; // письменности без своей раскладки: ряды собираются из алфавита (словаря языка или встроенного)
    private static final java.util.HashMap<String, L> gen = new java.util.HashMap<>();
    private static final java.util.HashMap<String, String> extras = new java.util.HashMap<>(); // алфавит словаря языка (заполняет Lexicon)

    static synchronized void alphabetChanged(String lang, String alphabet) { extras.put(lang, alphabet); gen.remove(lang); }

    /** Для какого письма раскладка: 1 латиница, 2 кириллица, 3 греческий, 4 иврит (по первой букве первого ряда). */
    static int script(String lang) {
        L l = byLang(lang);
        return LexBuilder.sc(l != null && !l.r1.isEmpty() ? l.r1.charAt(0) : 'a');
    }

    private static final String LAT_BASE = "\u00DFs\u0142l\u0111d\u00F8o\u00E6a\u0131i\u00F0d\u00FEt\u0153o\u0167t\u014Bn", CYR_BASE = "\u04D9\u0430\u0493\u0433\u049B\u043A\u04A3\u043D\u04E9\u043E\u04B1\u0443\u04AF\u0443\u04BB\u0445\u0456\u0438\u0457\u0438\u0454\u0435\u0491\u0433\u0458\u0439\u0459\u043B\u045A\u043D\u045B\u0446\u0452\u0434\u045F\u0447";

    /** Буквы словаря, которых нет в рядах раскладки, группируются по «родной» букве: из них получается меню по удержанию. */
    private static String altsFor(String alphabet, boolean cyr, String rows) {
        if (alphabet == null) return "";
        java.util.LinkedHashMap<Character, StringBuilder> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < alphabet.length(); i++) {
            char x = alphabet.charAt(i);
            if (rows.indexOf(x) >= 0 || !Character.isLetter(x)) continue;
            char b = 0;
            String t = cyr ? CYR_BASE : LAT_BASE;
            for (int k = 0; k + 1 < t.length(); k += 2) if (t.charAt(k) == x) b = t.charAt(k + 1);
            if (b == 0 && !cyr) {
                String d = java.text.Normalizer.normalize(String.valueOf(x), java.text.Normalizer.Form.NFD);
                if (d.length() > 1 && d.charAt(0) >= 'a' && d.charAt(0) <= 'z') b = d.charAt(0);
            }
            if (b == 0) continue;
            StringBuilder sb = m.get(b);
            if (sb == null) m.put(b, sb = new StringBuilder());
            if (sb.length() > 0) sb.append('|');
            sb.append(x);
        }
        StringBuilder out = new StringBuilder();
        for (java.util.Map.Entry<Character, StringBuilder> e : m.entrySet()) { if (out.length() > 0) out.append(';'); out.append(e.getKey()).append(':').append(e.getValue()); }
        return out.toString();
    }

    private static synchronized L generic(String c) {
        boolean cyr = GEN_CYR.contains("," + c + ","), auto = GEN_AUTO.contains("," + c + ",");
        if (!cyr && !auto && !GEN_LATIN.contains("," + c + ",")) return null;
        L l = gen.get(c);
        if (l == null) {
            Locale loc = new Locale(c);
            String nm = loc.getDisplayLanguage(loc);
            nm = nm.isEmpty() ? c : nm.substring(0, 1).toUpperCase(loc) + nm.substring(1);
            if (auto) { l = autoLayout(c, nm, loc); gen.put(c, l); return l; }
            String r1 = cyr ? "\u0439\u0446\u0443\u043A\u0435\u043D\u0433\u0448\u0449\u0437\u0445" : QW, r2 = cyr ? "\u0444\u044B\u0432\u0430\u043F\u0440\u043E\u043B\u0434\u0436\u044D" : AS, r3 = cyr ? "\u044F\u0447\u0441\u043C\u0438\u0442\u044C\u0431\u044E" : ZX;
            l = new L(c, nm, "space", c, "$", r1, r2, r3, altsFor(extras.get(c), cyr, r1 + r2 + r3), true, loc);
            gen.put(c, l);
        }
        return l;
    }

    /** Алфавиты языков из GEN_AUTO, пока словаря нет: буквы, затем знаки гласных. */
    private static String baseAlphabet(String c) {
        switch (c) {
            case "hy": return "աբգդեզէըթժիլխծկհձղճմյնշոչպջռսվտրցւփքօֆ";
            case "ka": return "აბგდევზთიკლმნოპჟრსტუფქღყშჩცძწჭხჯჰ";
            case "th": return "กขคฆงจฉชซญฎฏฐฑฒณดตถทธนบปผฝพฟภมยรลวศษสหฬอฮะาำเแโใไฤฦัิีึืุู็่้๊๋์ๆฯ";
            case "hi": return "अआइईउऊऋएऐओऔकखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसहािीुूृेैोौंँः्";
            case "bn": return "অআইঈউঊঋএঐওঔকখগঘঙচছজঝঞটঠডঢণতথদধনপফবভমযরলশষসহািীুূৃেৈোৌংঃঁ্";
            case "ta": return "அஆஇஈஉஊஎஏஐஒஓஔகஙசஞடணதநபமயரலவழளறனாிீுூெேைொோௌ்ஃ";
            case "te": return "అఆఇఈఉఊఋఎఏఐఒఓఔకఖగఘఙచఛజఝఞటఠడఢణతథదధనపఫబభమయరలవశషసహళాిీుూృెేైొోౌంః్";
            case "ml": return "അആഇഈഉഊഋഎഏഐഒഓഔകഖഗഘങചഛജഝഞടഠഡഢണതഥദധനപഫബഭമയരലവശഷസഹളഴറാിീുൂൃെേൈൊോൌംഃ്";
            case "si": return "අආඇඈඉඊඋඌඍඎඑඒඓඔඕඖකඛගඝඞචඡජඣඤටඨඩඪණතථදධනපඵබභමයරලවශෂසහළෆාැෑිීුූෘෙේෛොෝෞං";
            case "ur": return "ابپتٹثجچحخدڈذرڑزژسشصضطظعغفقکگلمنںوہھءیے";
            default: return "";
        }
    }

    /** Раскладка для письменности без готовой: буквы по алфавиту в три ряда, знаки гласных и лишние буквы — в меню удержания. */
    private static L autoLayout(String c, String nm, Locale loc) {
        String alpha = extras.get(c);
        if (alpha == null || alpha.length() < 8) alpha = baseAlphabet(c);
        StringBuilder let = new StringBuilder(), rest = new StringBuilder();
        for (int i = 0; i < alpha.length(); i++) { char x = alpha.charAt(i); if (Character.isLetter(x)) let.append(x); else rest.append(x); }
        if (let.length() > 31) { rest.insert(0, let.substring(31)); let.setLength(31); } // в ряды помещается 11 + 11 + 9
        int n = let.length();
        int a = Math.min(11, (n + 2) / 3), b = Math.min(11, (n - a + 1) / 2);
        if (n - a - b > 9) { a = 11; b = 11; }
        StringBuilder al = new StringBuilder();
        if (rest.length() > 0 && n > 0) { // оставшиеся знаки раздаём клавишам по 2–4 штуки
            int g = (rest.length() + n - 1) / n, pos = 0;
            for (int k = 0; k < n && pos < rest.length(); k++) {
                int e = Math.min(rest.length(), pos + g);
                if (al.length() > 0) al.append(';');
                al.append(let.charAt(k)).append(':');
                for (int p = pos; p < e; p++) { if (p > pos) al.append('|'); al.append(rest.charAt(p)); }
                pos = e;
            }
        }
        return new L(c, nm, "space", c, "$", let.substring(0, a), let.substring(a, a + b), let.substring(a + b), al.toString(), true, loc);
    }

    /** Все языки, для которых есть раскладка (свои и общие латиница/кириллица). */
    static String[] supportedIds() {
        java.util.ArrayList<String> o = new java.util.ArrayList<>();
        for (L l : ALL) o.add(l.id);
        for (String g : (GEN_LATIN + GEN_CYR + GEN_AUTO).split(",")) if (!g.isEmpty() && !o.contains(g)) o.add(g);
        return o.toArray(new String[0]);
    }

    static L byLang(String code) {
        if (code == null) return null;
        int i = 0;
        while (i < code.length() && code.charAt(i) != '_' && code.charAt(i) != '-') i++;
        String c = code.substring(0, i).toLowerCase(Locale.ROOT);
        if (c.equals("iw")) c = "he";
        else if (c.equals("in")) c = "id";
        else if (c.equals("no") || c.equals("nn")) c = "nb";
        L l = byId(c);
        return l != null ? l : generic(c);
    }

    /** Язык интерфейса телефона, если для него есть раскладка, иначе английский. */
    static L system() {
        L l = byLang(Locale.getDefault().getLanguage());
        return l != null ? l : EN;
    }
}

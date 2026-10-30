package com.lightboard.keyboard;

import java.util.HashSet;
import java.util.List;

/**
 * Определяет язык словаря по самим словам (список от частых к редким): письменность, затем буквы-«метки» и самые частые слова языка.
 * Нужен, когда в имени файла нет кода языка. Ответ — только предположение: перед сборкой словаря человек его подтверждает.
 */
final class LangDetect {
    private LangDetect() { }

    /** {код, частые слова через пробел, буквы-метки}; слова — только для латиницы. */
    private static final String[][] LATIN = {
        {"en", "the of and to in is you that it he was for on are with as his they at be this have from or one had by not but what all were we when your can said there use an each which she do how their if will up other about out many then them these so some her would make like him into time has look two more write go see no way could people my than first water been call who oil its now find long down day did get come made may part", "" },
        {"de", "der die und in den von zu das mit sich des auf für ist im dem nicht ein eine als auch es an werden aus er hat dass sie nach wird bei einer um am sind noch wie einem über einen so zum war haben nur oder aber vor zur bis mehr durch man sein wurde sei ich du", "äöüß"},
        {"fr", "de la le et les des en un du une que est pour qui dans a par plus pas au sur ne se ce il sont les ou avec son je vous nous mais comme on tout elle y aux cette fait être été leur sans ces entre", "éèêàçùâîôûëïœ"},
        {"es", "de la que el en y a los se del las un por con no una su para es al lo como más pero sus le ya o este sí porque esta entre cuando muy sin sobre también me hasta hay donde quien desde todo nos durante", "ñ¿¡áéíóúü"},
        {"it", "di che e il la in un a per è non una con sono mi da del le si ma i lo al ho ti come più se questo anche gli della ci tutto nel suo hanno cosa dei io loro era", "àèéìòù"},
        {"pt", "de que o e a do da em um para é com não uma os no se na por mais as dos como mas foi ao ele das tem à seu sua ou ser quando muito há nos já está eu também só pelo pela até isso ela entre era depois sem mesmo aos ter seus quem nas me esse eles estão você tinha foram essa num nem suas meu às minha têm numa pelos elas havia seja qual será nós", "ãõçâêôáéíóúà"},
        {"nl", "de het een van en in is dat op te zijn met voor niet aan er om ook als dan maar bij of uit nog worden door over ze zich was ik je we hij wat naar heeft deze geen meer", "ĳ"},
        {"sv", "och att det i en som är på för med den av har inte till jag om ett var men de vi så kan från ska också eller sig vid då han hon du dig vara", "åäö"},
        {"da", "og at det i en er til på for med den af har ikke jeg der om et var men de vi så kan fra skal også eller sig ved da han hun du vil", "æøå"},
        {"nb", "og i det er en å på som til for med av ikke jeg har den et var men de vi så kan fra skal også eller seg ved da han hun du vil", "æøå"},
        {"fi", "ja on ei että se oli mutta olen ole hän sinä minä kun niin tämä mitä jos kuin vain joka ovat tai sen kanssa kaikki hänen nyt voi tässä", "äö"},
        {"is", "og að í er ég á það við sem ekki hann til en með var þetta þú hún sér um hafa þá", "þðæöáéíóúý"},
        {"et", "ja on ei et see ta ma mis kui oli ka aga sa mitte olen ning kes nii veel siis oma kõik", "õäöü"},
        {"lv", "un ir es ka tu nav kas to uz par man tas ar vai bet viņš kā no ko jau lai", "āēģīķļņšūž"},
        {"lt", "ir tai kad aš ne su jis kaip bet yra tu iš ar jau man tik buvo mes jūs", "ąčęėįšųūž"},
        {"pl", "w i nie na się z do to że jest co o ja tak jak ale mnie po czy już tylko za go by być był było mi ci", "ąćęłńóśźż"},
        {"cs", "a v se na je že to s o z do jsem ne ale ten si by jako tak už pro jsou jen co byl když", "čěřůýžšňťď"},
        {"sk", "a v sa na je že to s o z do som nie ale ten si by ako tak už pre sú len čo bol keď", "ľĺŕôäčšžťďň"},
        {"sl", "in je v se na da ne to so za z ki ja pa sem bi ali kot tudi pri sva", "čšž"},
        {"hr", "i je u se na da to ne za su sam s što ja od ali ti kao ako ću bi samo", "čćđšž"},
        {"bs", "i je u se na da to ne za su sam ti šta ja od ali kao ako bih samo", "čćđšž"},
        {"hu", "a az és hogy nem egy is van meg de én te ez mi ki el ha már csak még volt mint", "őűéáíóöüú"},
        {"ro", "de și în la cu nu să că o un pe este mai se ce ca din care sau dar mă îți", "ăâîșțşţ"},
        {"tr", "bir ve bu da de için ne çok ben mi ama o var ile sen değil gibi daha ki ya her", "ğşıçöü"},
        {"sq", "të e dhe në që një me për nga është nuk unë ju po si më", "ëç"},
        {"mt", "li u ta fl ma il jien int hu kien minn għal", "ħġżċ"},
        {"cy", "yn y i a ar o ei mae ond ni fy wedi am yr", "ŵŷ"},
        {"ga", "an a ar ag go is na tá níl sé mé tú agus ní le", "áéíóú"},
        {"eo", "la kaj de en ne mi estas li al vi ke kun por estis", "ĉĝĥĵŝŭ"},
        {"eu", "eta da ez bat du zen ere baina nik zuk dut hori gure", ""},
        {"gl", "de que o a e non do da un en para os se é no na unha", ""},
        {"ca", "de el la que i a en no un per és amb una els les es al", "çàèéíòóúï·"},
        {"id", "yang di dan itu dengan untuk tidak ini dari akan ada saya aku kamu gak nggak banget sudah", ""},
        {"ms", "yang dan di itu untuk tidak ini dengan saya ada awak tak dah kau nak boleh", ""},
        {"tl", "ang ng sa na ako ay mga hindi ka mo ko siya", ""},
        {"vi", "và của là có không tôi một những được trong cho người này với bạn", "ăâđêôơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ"},
        {"af", "die en van ek het in is nie jy dit", ""},
        {"sw", "na ya wa kwa ni katika za la kuwa hii yake hapa", ""},
        {"br", "ar an e a da en ha he ne eo war hag", ""},
        {"lb", "de an et ass ech net mat eng zu vun op den", "ëé"},
        {"uz", "va bu bir bilan men uchun emas ham bor u", "ʻʼ"},
        {"az", "və bu bir də mən ki üçün çox deyil ilə o", "əğışçöü"},
    };

    private static HashSet<String>[] STOP;

    @SuppressWarnings("unchecked")
    private static synchronized HashSet<String>[] stop() {
        if (STOP == null) {
            HashSet<String>[] s = (HashSet<String>[]) new HashSet[LATIN.length];
            for (int i = 0; i < LATIN.length; i++) { s[i] = new HashSet<>(); for (String w : LATIN[i][1].split(" ")) s[i].add(w); }
            STOP = s;
        }
        return STOP;
    }

    private static int count(List<String> words, String letters) {
        int n = 0;
        for (String w : words) for (int i = 0; i < w.length(); i++) if (letters.indexOf(w.charAt(i)) >= 0) n++;
        return n;
    }

    private static int letters(List<String> words) { int n = 0; for (String w : words) n += w.length(); return n; }

    /** Код языка (как в Layouts) или null, если уверенного ответа нет. Слова — в нижнем регистре, от частых к редким. */
    static String detect(List<String> words) {
        if (words == null || words.size() < 20) return null;
        int[] sc = new int[15];
        int tot = 0;
        for (String w : words) { if (w.isEmpty()) continue; int s = LexBuilder.sc(w.charAt(0)); if (s > 0 && s < 15) sc[s]++; tot++; }
        int dom = 0;
        for (int s = 1; s < 15; s++) if (sc[s] > sc[dom]) dom = s;
        if (dom == 0 || sc[dom] * 10 < tot * 6) return null;
        int all = Math.max(1, letters(words));
        switch (dom) {
            case 3: return "el";
            case 4: return "he";
            case 6: return "hy";
            case 7: return "ka";
            case 8: return "th";
            case 9: return "hi";
            case 10: return "bn";
            case 11: return "ta";
            case 12: return "te";
            case 13: return "ml";
            case 14: return "si";
            case 5: {
                if (count(words, "\u0679\u0688\u0691\u06BA\u06D2\u06BE\u06C1") * 1000 >= all * 2) return "ur";
                if (count(words, "\u067E\u0686\u0698\u06AF\u06A9\u06CC") * 1000 >= all * 5) return "fa";
                return "ar";
            }
            case 2: {
                if (count(words, "\u04D9\u0493\u049B\u04A3\u04B1\u04BB") * 1000 >= all * 2) return "kk";
                if (count(words, "\u04AF\u04E9") * 1000 >= all * 3) return "mn";
                if (count(words, "\u0457\u0454\u0491") * 1000 >= all) return "uk";
                if (count(words, "\u045E") * 1000 >= all) return "be";
                if (count(words, "\u0453\u045C\u0455") * 1000 >= all) return "mk";
                if (count(words, "\u0452\u045B") * 1000 >= all) return "sr";
                if (count(words, "\u044A") * 1000 >= all * 8 && count(words, "\u044B\u044D\u0451") == 0) return "bg";
                return "ru";
            }
            default: break;
        }
        // латиница: частые слова и буквы с диакритикой
        HashSet<String>[] st = stop();
        int lim = Math.min(150, words.size());
        double best = 0, second = 0;
        int bi = -1;
        for (int k = 0; k < LATIN.length; k++) {
            int hits = 0;
            for (int i = 0; i < lim; i++) if (st[k].contains(words.get(i))) hits++;
            int mw = 0;
            String mk = LATIN[k][2];
            if (!mk.isEmpty()) for (String w : words) { for (int i = 0; i < w.length(); i++) if (mk.indexOf(w.charAt(i)) >= 0) { mw++; break; } if (mw >= 80) break; }
            double score = hits * 3.0 + mw * 0.5;
            if (score > best) { second = best; best = score; bi = k; } else if (score > second) second = score;
        }
        if (bi < 0 || best < 9.0 || best < second * 1.2) return null;
        return LATIN[bi][0];
    }
}

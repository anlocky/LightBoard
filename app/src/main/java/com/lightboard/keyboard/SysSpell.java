package com.lightboard.keyboard;

import android.content.Context;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;
import android.view.textservice.TextServicesManager;

import java.util.Locale;

/** Подсказки слов для языков без своего словаря: системная проверка орфографии (ничего не скачивается и не хранится в приложении). */
final class SysSpell implements SpellCheckerSession.SpellCheckerSessionListener {
    interface Cb { void got(String word, String[] suggestions); }

    private SpellCheckerSession ses;
    private String tag = "", asked = "";
    private Cb cb;
    private int seq; // номер запроса: ответ на прошлое слово не должен попасть к новому

    /** false — системной проверки орфографии для языка нет. */
    boolean ask(Context c, String languageTag, String word, Cb callback) {
        try {
            if (ses == null || !languageTag.equals(tag)) {
                close();
                TextServicesManager tsm = (TextServicesManager) c.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE);
                if (tsm == null) return false;
                ses = tsm.newSpellCheckerSession(null, Locale.forLanguageTag(languageTag), this, false);
                tag = languageTag;
                if (ses == null) return false;
            }
            cb = callback; asked = word;
            ses.getSuggestions(new TextInfo(word, 0, ++seq), 3);
            return true;
        } catch (RuntimeException e) { close(); return false; }
    }

    void close() {
        if (ses != null) { try { ses.close(); } catch (RuntimeException ignored) { } ses = null; }
        tag = "";
    }

    @Override public void onGetSuggestions(SuggestionsInfo[] results) {
        if (results == null || results.length == 0 || cb == null) return;
        SuggestionsInfo r = results[0];
        if (r == null || r.getSequence() != seq) return;
        int n = Math.min(3, r.getSuggestionsCount());
        String[] out = new String[n];
        for (int i = 0; i < n; i++) out[i] = r.getSuggestionAt(i);
        cb.got(asked, out);
    }

    @Override public void onGetSentenceSuggestions(android.view.textservice.SentenceSuggestionsInfo[] results) { }
}

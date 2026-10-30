package com.lightboard.keyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.view.inputmethod.InlineSuggestionsResponse;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

public final class LightBoardIme extends InputMethodService implements KeyboardView.Listener {

    private static final int OFF = 0, ON = 1, LOCK = 2;
    private static final String[] DEF_RU = {"я", "привет", "как", "что", "не", "да"}, DEF_EN = {"i", "hello", "how", "the", "not", "yes"};
    private static final String SENTENCE_END = ".!?", PUNCT = ".,!?;:";
    private static final long CLIP_TTL = 60_000L;

    private Board kv;
    private View kvView;
    private boolean gboard;
    private final Theme gtheme = new Theme();
    private FrameLayout root;
    private EmojiView emoji;
    private ClipView clipPanel;             // панель истории буфера обмена (создаётся при первом открытии)
    private boolean clipShown;
    private int clipH, ohApplied = -1;
    private SideBar side;                   // свободная область рядом с клавиатурой в режиме одной руки
    private int ohMode;                     // применённый режим одной руки (с учётом ориентации)
    private AutofillBar autofill;           // чипы автозаполнения Google в полосе подсказок (Android 11+)
    private boolean toolsOn;                // меню над клавишами открыто
    private final ClipStore clips = new ClipStore();
    private int oneHand;                    // 0 выкл, 1 слева, 2 справа
    private boolean split;                  // разделённая клавиатура
    private static final float ONE_HAND_FRAC = 0.8f;
    private SharedPreferences cfg, state; // cfg — настройки (пишет экран настроек), state — состояние (пишет клавиатура)
    private final Runnable trimR = new Runnable() { @Override public void run() { trimMemory(); } };
    private SwipeDecoder swipe;
    private Blur blur; // только API 31+
    private ClipboardManager cm;
    private final Theme theme = new Theme();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable refreshR = new Runnable() { @Override public void run() { refresh(); } };
    private final Runnable clipHide = new Runnable() { @Override public void run() {
        clipText = null;
        if (smsShown) { smsShown = false; clearSmsStore(); applyStrip(); }
        schedule();
    } };
    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = new ClipboardManager.OnPrimaryClipChangedListener() {
        @Override public void onPrimaryClipChanged() { readClip(); }
    };

    private EditorInfo ei;
    private boolean emojiShown, bottomBar, darkNow, blurNow, showDigits, showWords = true, addrField;
    private int emojiH;
    private final StringBuilder wordBuf = new StringBuilder(); // запасной учёт набираемого слова для полей, где нельзя прочитать текст
    private boolean ru = true, suggestOn, acOn, autoSpace, clipOk, fieldOk = true; // ru — язык словаря (lay.ru); fieldOk — поле допускает подсказки
    private Layouts.L lay = Layouts.RU;                                          // текущая раскладка; словари только у en и ru
    private final Runnable scheduleR = new Runnable() { @Override public void run() { schedule(); } };
    private int shift = OFF;
    private boolean shiftManual;
    private long lastShiftTap, lastSpace;

    // подсказки
    private String prefix = "", lastCorrFor, lastCorr;
    private final String[] shown = new String[3], commit = new String[3], match = new String[3];
    private boolean swipeAlt, swipeAwait;
    private int swipeEnd = -1, swipeLen, swipeInsLen;
    private String clipText;

    // правка набора
    private char afterPunct;           // последний набранный знак, после которого буква получит пробел
    private boolean trailAutoSpace;    // последний пробел вставили мы (после слова из свайпа/подсказки)
    private boolean acArmed;           // можно откатить автоисправление клавишей «стереть»
    private String acOrig, acNew, acTrig;
    private KeySounds sounds;                           // звуки нажатий (создаются при первом нажатии, если включены)
    private boolean soundOn;
    private float soundVol = 0.5f;
    private final Learner learner = new Learner();      // самообучение: слова, пары «слово -> следующее», свои слова
    private final String[] nxt = new String[3];
    private boolean ownPruned, calcOn;                  // однократная чистка «своих» слов; калькулятор по «=»
    private long ohAnimEnd, lastHeal;                             // когда закончится анимация режима одной руки
    private SysSpell spell;                             // системная проверка орфографии для языков без своего словаря
    private Runnable corrJob;
    private HandlerThread corrThread; private Handler corrH; // фоновый поток для подбора исправления (создаётся при необходимости, гасится вместе с кэшами)
    private boolean noLearn, capsNeed = true, smsShown, pwdField; // noLearn: поле просит не запоминать (инкогнито); capsNeed: пора спросить у поля про заглавную
    private int npadNow;
    private boolean fieldNoLearn, incoOn, smartDash; // инкогнито: удержание пробела, запоминание не ведётся
    private Boolean micAvail;                        // есть ли в системе служба распознавания речи (проверяем один раз)
    private String curPkg, appsRaw = "";             // приложение, в которое печатаем, и разобранные настройки отдельных приложений
    private final java.util.HashMap<String, String[]> appsMap = new java.util.HashMap<>();
    private android.net.Uri imgUri;                 // недавний скриншот или скопированная картинка в полосе подсказок (в историю не попадает)
    private android.graphics.Bitmap imgThumb;
    private int imgLabel;
    private final Runnable imgHide = new Runnable() { @Override public void run() { clearImage(); schedule(); } };
    private boolean autoCaps = true, autoPeriod = true;
    private static final long SMS_TTL = 300_000L;
    private long lexVer;
    private boolean lexScanned, soundsLoading;

    // ---------- жизненный цикл ----------
    @SuppressWarnings("deprecation")
    private SharedPreferences settingsPrefs() {
        // MODE_MULTI_PROCESS: экран настроек живёт в другом процессе; так мы подхватываем его изменения.
        // Работает надёжно только пока у каждого файла один пишущий процесс: «lb» пишут только настройки, «lbs» — только клавиатура
        // (настройки его лишь читают). Нарушать это правило нельзя: два процесса, пишущих в один файл, затирают друг друга.
        return getSharedPreferences("lb", MODE_PRIVATE | MODE_MULTI_PROCESS);
    }

    @Override public void onCreate() {
        super.onCreate();
        state = getSharedPreferences("lbs", MODE_PRIVATE);
        if (state.contains("sms_code")) state.edit().remove("sms_code").remove("sms_time").apply(); // код из СМС больше не пишется на диск
        cfg = settingsPrefs();
        Layouts.L sl = Layouts.byLang(state.getString("lay", null)); // byLang знает и «общие» языки (kk, vi, bg…), byId — только встроенные
        if (sl == null) sl = state.contains("ru") ? (state.getBoolean("ru", true) ? Layouts.RU : Layouts.EN) : Layouts.system(); // со старых версий: хранился флаг ru
        lay = sl; ru = lay.ru;
        swipe = new SwipeDecoder();
        swipe.setLearner(learner);
        learner.loadUser(state.getString("user_words", ""));
        learner.loadWords(state.getString("learned", ""));
        learner.loadPairs(state.getString("pairs", ""));
        learner.loadOwn(state.getString("own", null)); // null — обновление со старой версии
        if (clips.loadFrom(clipFile())) saveClips(); // старый файл без шифрования: перезаписываем зашифрованным
        String oldClips = state.getString("clips", null); // со старых версий буфер лежал в настройках
        if (oldClips != null) { if (clips.size() == 0) clips.load(oldClips); saveClips(); state.edit().remove("clips").apply(); }
        SmsReceiver.sink = new SmsReceiver.Sink() { @Override public void onCode(String code) { smsShow(code, SMS_TTL); applyStrip(); schedule(); } };
        oneHand = state.getInt("onehand", 0);
        split = state.getBoolean("split", false);
        Window w = getWindow().getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
        cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.addPrimaryClipChangedListener(clipListener);
        // (раньше здесь ставился глобальный обработчик падений: он цепочкой держал ссылки на все прежние экземпляры сервиса;
        // падения и так попадают в logcat системой)
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; } // без громоздкого fullscreen-ввода в ландшафте

    @Override public View onCreateInputView() {
        gboard = designGboard(); // по умолчанию Material
        newBoard();
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                if (blur != null) blur.detach();
                blur = new Blur(this, new Blur.Callback() {
                    @Override public void onBlur(boolean on) { blurNow = on; applyTheme(); }
                });
                blur.attach();
            } catch (RuntimeException e) { Diag.save(this, e); blur = null; } // без размытия клавиатура всё равно работает
        }
        root = new FrameLayout(this);
        root.addView(kvView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        emoji = null; emojiShown = false; clipPanel = null; clipShown = false; side = null; ohApplied = -1; ohMode = 0; autofill = null;
        kv.setSplit(split);
        applyOneHand(false);
        return root;
    }

    /** Настройка отдельного приложения: part 0 дизайн, 1 тема, 2 цвета; "-" — как в общих настройках. Строка разбирается только при изменении. */
    private String ovr(int part) {
        String raw = cfg.getString("apps", "");
        if (raw.isEmpty() || curPkg == null) return "-";
        if (!raw.equals(appsRaw)) {
            appsRaw = raw; appsMap.clear();
            for (String e : raw.split(";")) { String[] f = e.split(":"); if (f.length == 4) appsMap.put(f[0], f); }
        }
        String[] f = appsMap.get(curPkg);
        return f == null ? "-" : f[part + 1];
    }

    private String schemeNow() { String o = ovr(2); return o.equals("-") ? cfg.getString("g_material", "auto") : o; }

    private boolean micAvailable() {
        if (micAvail == null) micAvail = SpeechRecognizer.isRecognitionAvailable(this);
        return micAvail;
    }

    private void updNoLearn() { noLearn = fieldNoLearn || incoOn; }

    @Override public void onIncognito() {
        if (!cfg.getBoolean("incognito", true)) return;
        incoOn = !incoOn;
        updNoLearn();
        if (kv != null) kv.setIncognito(true, incoOn);
        android.widget.Toast.makeText(this, incoOn ? R.string.incognito_on : R.string.incognito_off, android.widget.Toast.LENGTH_SHORT).show();
        schedule();
    }

    /** Битое значение в настройках не должно ронять клавиатуру. */
    private boolean designGboard() {
        String o = ovr(0); // настройка отдельного приложения важнее общей
        if (o.equals("gboard")) return true;
        if (o.equals("ios")) return false;
        try { return cfg.getString("design", "gboard").equals("gboard"); } catch (ClassCastException e) { return true; }
    }

    private Theme activeTheme() { return gboard ? gtheme : theme; }

    /** Префикс настроек: у каждого дизайна свои значения. */
    private String P() { return gboard ? "g_" : "i_"; }

    /** Создаёт выбранный дизайн; вся логика ввода общая, различается только View. */
    private void newBoard() {
        kv = gboard ? new GboardView(this, gtheme) : new KeyboardView(this, theme);
        kvView = kv.view();
        kv.setListener(this);
        kv.setSwipe(swipe);
        kv.setLayout(lay);
        kv.setBottomBar(bottomBar);
    }

    /** Смена дизайна в настройках: подменяем View, состояние ввода остаётся. */
    private void swapBoard(boolean toGboard) {
        if (root == null) return;
        if (emoji != null) { root.removeView(emoji); emoji = null; }
        if (clipPanel != null) { root.removeView(clipPanel); clipPanel = null; }
        emojiShown = false; clipShown = false;
        root.removeView(kvView);
        gboard = toGboard;
        newBoard();
        root.addView(kvView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        kv.setSplit(split);
        ohApplied = -1; applyOneHand(false);
        theme.set(!darkNow, false, -1); // принудительно пересчитать палитры при следующем applyTheme()
    }

    @Override public void onWindowShown() {
        super.onWindowShown();
        h.removeCallbacks(trimR);
        lastChip = null;
        if (kv != null) { kv.playEntrance(); kv.setToolsOpen(true); } // пока ничего не набрано, меню раскрыто
    }

    @Override public void onWindowHidden() {
        super.onWindowHidden();
        if (listening) stopListening();
        saveLearned();
        flushClips();
        h.removeCallbacks(trimR);
        h.postDelayed(trimR, 8000); // клавиатура скрыта: через 8 с отдаём память
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND && !isInputViewShown()) trimMemory();
    }

    /** Освобождаем всё, что можно собрать заново: панель эмодзи, словари, кэши. */
    private void trimMemory() {
        if (isInputViewShown()) return;
        if (emoji != null && root != null) { root.removeView(emoji); emoji = null; emojiShown = false; }
        if (clipPanel != null && root != null) { root.removeView(clipPanel); clipPanel = null; clipShown = false; }
        if (sounds != null) { sounds.release(); sounds = null; }
        Lexicon.invalidate();
        lastCorrFor = null; lastCorr = null;
        clearImage();
        stopCorr();
    }

    private void applyTheme() {
        if (kv == null) return;
        boolean changed;
        if (gboard) { gtheme.setMaterial(this, darkNow, schemeNow()); changed = true; }
        else changed = theme.set(darkNow, blurNow, cfg.getInt("i_panelPct", 80));
        if (changed) {
            kv.themeChanged();
            if (emoji != null) emoji.themeChanged();
            if (clipPanel != null) clipPanel.themeChanged();
            if (side != null) { side.setTheme(activeTheme()); side.themeChanged(); }
            applyNavBar(darkNow); // системный отступ под цвет подложки клавиатуры
        }
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        try { startInputViewImpl(info); } catch (RuntimeException e) { Diag.save(this, e); }
    }

    private void startInputViewImpl(EditorInfo info) {
        curPkg = info.packageName;
        ei = info;
        cfg = settingsPrefs();
        boolean wantG = designGboard();
        if (kv != null && wantG != gboard) swapBoard(wantG);
        long rs = cfg.getLong("reset_words", 0L);
        if (rs > state.getLong("reset_done", 0L)) { // из настроек попросили сбросить личные слова
            learner.clear();
            state.edit().remove("learned").remove("pairs").remove("user_words").putLong("reset_done", rs).apply();
        }
        long fs = cfg.getLong("forget_stamp", 0L);
        if (fs > state.getLong("forget_done", 0L)) { // из настроек попросили забыть отдельные слова
            for (String fw : cfg.getString("forget", "").split(" ")) if (!fw.isEmpty()) learner.forget(fw);
            state.edit().putLong("forget_done", fs).apply();
        }
        applyImport(); // данные из файла резервной копии (настройки кладут его в filesDir, мы применяем и удаляем)
        long lv = cfg.getLong("lex_ver", 0L); // экран настроек увеличивает его после импорта словаря: на диск ходим только тогда
        if (!lexScanned || lv != lexVer) { lexScanned = true; lexVer = lv; Lexicon.refreshIfChanged(this); }
        Layouts.L fresh = Layouts.byLang(lay.id);
        if (fresh != null && fresh != lay) lay = fresh;
        if (kv == null) return;
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) adoptSubtype(imm.getCurrentInputMethodSubtype());
        } catch (RuntimeException ignored) { }
        boolean sysDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        String tp = cfg.getString(P() + "theme", "auto");
        String ot = ovr(1);
        if (!ot.equals("-")) tp = ot;
        darkNow = tp.equals("dark") || (tp.equals("auto") && sysDark);
        if (blur != null) blur.setWanted(!gboard && cfg.getBoolean("i_blur", true), cfg.getInt("i_blurDp", 20)); // у дизайна Gboard размытия нет
        applyTheme();
        applyNavBar(darkNow);

        boolean wantBar = cfg.getBoolean(P() + "bottombar", false);
        if (wantBar != bottomBar) {
            bottomBar = wantBar;
            kv.setBottomBar(bottomBar);
            if (emoji != null) { root.removeView(emoji); emoji = null; emojiShown = false; kvView.setVisibility(View.VISIBLE); kvView.setAlpha(1f); }
        }
        boolean langKeyOn = cfg.getBoolean(P() + "langkey", false); // по умолчанию выключена
        kv.setLangKey(langKeyOn);
        kv.setGlobeMode(!langKeyOn && cfg.getBoolean(P() + "globe", false)); // с отдельной клавишей языка эмодзи-клавиша всегда эмодзи
        if (emojiShown) hideEmoji(false);
        if (clipShown) hideClips(false);
        settleBoard();
        syncModes();
        kv.setHaptic(cfg.getBoolean(P() + "haptic", true));
        soundOn = cfg.getBoolean(P() + "sound", false); // по умолчанию звуков нет
        soundVol = cfg.getInt(P() + "soundvol2", 30) / 100f * KeySounds.MAX_GAIN;
        if (soundOn) warmSounds();
        kv.setSpaceCursor(cfg.getBoolean(P() + "spacecursor", true));
        kv.setClearAll(cfg.getBoolean(P() + "clearall", true), cfg.getInt(P() + "clearhold", 600));
        kv.setHeightScale(cfg.getInt(P() + "height", 100) / 100f * Ui.heightMul(this));
        kv.setTextScale(Ui.textMul(this));
        kv.setBottomPad(cfg.getInt(P() + "botpad", 0));
        kv.setFontScale(cfg.getInt(P() + "font", 100) / 100f);

        int cls = info.inputType & InputType.TYPE_MASK_CLASS, var = info.inputType & InputType.TYPE_MASK_VARIATION;
        boolean numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME;
        boolean isText = cls == InputType.TYPE_CLASS_TEXT; // вариации у разных классов пересекаются по значениям: сначала смотрим класс
        boolean password = (isText && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
                || (cls == InputType.TYPE_CLASS_NUMBER && var == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        // адреса (email, URL): подсказки и исправление есть, но знаки препинания не запускают исправление и пробел не ставится
        addrField = isText && (var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS || var == InputType.TYPE_TEXT_VARIATION_URI);
        // подсказки, исправление и свайп работают везде, кроме паролей и числовых полей (флаг «без подсказок» игнорируем)
        fieldOk = !password && !numeric;
        pwdField = password;
        if (password && listening) stopListening();
        kv.setMic(cfg.getBoolean(P() + "mic", true) && micAvailable() && !password); // в полях паролей голос не уходит в распознавание
        fieldNoLearn = (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0; // поле просит ничего не запоминать
        boolean incoAllowed = cfg.getBoolean("incognito", true);
        if (!incoAllowed) incoOn = false;
        updNoLearn();
        kv.setIncognito(incoAllowed, incoOn);
        smartDash = cfg.getBoolean("smartdash", false);
        capsNeed = true;
        applyFlags(); // и подсказки, и исправление, и свайп работают только у языков со словарём
        clipOk = !password && cfg.getBoolean(P() + "clip", true);
        showDigits = cfg.getBoolean(P() + "digits", false); // ряд цифр по умолчанию выключен в обоих дизайнах
        showWords = cfg.getBoolean(P() + "words", true);
        wordBuf.setLength(0);
        checkSms();
        findShot();

        kv.setLayout(lay);
        int npad = 0; // отдельная цифровая клавиатура: 1 PIN/целое число, 4 число со знаком или точкой, 2 телефон, 3 дата/время
        if (cls == InputType.TYPE_CLASS_PHONE) npad = 2;
        else if (cls == InputType.TYPE_CLASS_DATETIME) npad = 3;
        else if (cls == InputType.TYPE_CLASS_NUMBER) npad = (info.inputType & (InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED)) == 0 ? 1 : 4;
        kv.setNumpad(npad);
        kv.setTablet(getResources().getConfiguration().smallestScreenWidthDp >= 600); // планшетная раскладка включается сама
        kv.setRound(cfg.getInt(P() + "round", Ui.defRound(this, !gboard)));
        kv.setNumRow(showDigits && !numeric);
        npadNow = npad;
        applyStrip(); // у цифровой клавиатуры верхней панели нет (кроме случая, когда пришёл код из СМС)
        kv.setKeyBg(cfg.getBoolean(P() + "keybg", true));
        kv.setSwapIcons(cfg.getBoolean(P() + "swapicons", false));
        kv.setPopup(cfg.getBoolean(P() + "popup", true) && !password); // в паролях буквы над клавишами не показываем
        kv.setHints(cfg.getBoolean(P() + "hintnum", false), cfg.getBoolean(P() + "hintsym", false));
        kv.setLine(cfg.getBoolean(P() + "trail", true));
        kv.setLogo(cfg.getBoolean("g_logo", false));
        String mp = cfg.getString("g_micpos", "top");
        kv.setMicPos(mp.equals("bottom") ? 1 : mp.equals("both") ? 2 : 0);
        autoCaps = cfg.getBoolean(P() + "autocaps", true);
        autoPeriod = cfg.getBoolean(P() + "autoperiod", true);
        calcOn = cfg.getBoolean("calc", true) && !password;
        kv.setPage(numeric ? 1 : 0);
        if (lay.own()) Lexicon.preload(this, lay.id, scheduleR); else Lexicon.invalidate(); // у остальных языков словарь не нужен
        if (clipText != null && !chipOk()) clipText = null;
        swipeAlt = false; afterPunct = 0; trailAutoSpace = false; acArmed = false;
        kv.setEnterLabel(enterLabel());
        shift = OFF; shiftManual = false; kv.setShift(OFF);
        schedule();
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        h.removeCallbacks(refreshR);
        if (autofill != null) autofill.clear(); // чипы автозаполнения относятся к закрытому полю
        if (kv != null) kv.cancelTouches();
    }

    @Override public void onUpdateSelection(int oldSs, int oldSe, int newSs, int newSe, int cs, int ce) {
        super.onUpdateSelection(oldSs, oldSe, newSs, newSe, cs, ce);
        if (newSs != newSe || Math.abs(newSs - oldSs) != 1) capsNeed = true; // курсор прыгнул: заглавная могла измениться
        if (swipeAlt) {
            if (swipeAwait) { swipeEnd = newSs; swipeAwait = false; }
            else if (newSs != swipeEnd || newSe != swipeEnd) swipeAlt = false;
        }
        schedule();
    }

    @Override public void onComputeInsets(InputMethodService.Insets out) {
        super.onComputeInsets(out);
        if (kv != null) { // «touch fix» из v0.6 сохранён
            out.contentTopInsets = 0;
            out.visibleTopInsets = 0;
            out.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_CONTENT;
        }
    }

    @Override public void onDestroy() {
        flushClipsNow();
        SmsReceiver.sink = null;
        stopCorr();
        stopListening();
        if (sounds != null) { sounds.release(); sounds = null; }
        saveLearned();
        if (blur != null) blur.detach();
        if (cm != null) cm.removePrimaryClipChangedListener(clipListener);
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void applyNavBar(boolean dark) {
        Window w = getWindow().getWindow();
        if (w == null) return;
        w.setNavigationBarColor(activeTheme().panelBot); // тот же цвет (с той же прозрачностью), что и низ подложки
        if (Build.VERSION.SDK_INT >= 28) w.setNavigationBarDividerColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 26) {
            View dv = w.getDecorView();
            int ui = dv.getSystemUiVisibility();
            ui = dark ? (ui & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) : (ui | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            dv.setSystemUiVisibility(ui);
        }
    }

    // ---------- буфер обмена ----------
    private void readClip() {
        if (cm == null) return;
        boolean chip = cfg.getBoolean(P() + "clip", true), hist = cfg.getBoolean("clip_hist", true);
        if (!chip && !hist) return;
        try {
            ClipData d = cm.getPrimaryClip();
            if (d == null || d.getItemCount() == 0) return;
            if (d.getDescription() != null && d.getDescription().getExtras() != null
                    && d.getDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE")) return; // пароли из менеджеров паролей не трогаем
            final android.net.Uri iu = d.getItemAt(0).getUri();
            if (iu != null && d.getDescription().hasMimeType("image/*")) { // картинка: только в полосу подсказок, в историю не идёт
                if (imgAllowed()) bg().post(new Runnable() { @Override public void run() { showImage(iu, R.string.chip_image); } });
                return;
            }
            CharSequence t = d.getItemAt(0).getText(); // только текст: coerceToText лез бы в чужие провайдеры на главном потоке
            if (t == null) return;
            String s = t.toString().trim();
            if (s.isEmpty()) return;
            if (hist && !incoOn && !looksSecret(s) && clips.add(s, nowMin())) { clips.expire(nowMin(), clipTtl()); saveClips(); } // в инкогнито буфер не записывается
            if (!chip || s.length() > 200_000) return; // на панели подсказок доступен и очень длинный текст (в истории — до MAX_LEN)
            smsShown = false;
            clipText = s;
            h.removeCallbacks(clipHide);
            h.postDelayed(clipHide, CLIP_TTL);
            schedule();
        } catch (RuntimeException ignored) { }
    }

    /** Похоже на пароль или одноразовый код: на диск в историю не пишем (чип в подсказках работает как обычно). */
    private static boolean looksSecret(String s) {
        int n = s.length();
        if (n < 4 || n > 64) return false;
        boolean lo = false, up = false, dg = false, sy = false;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == '@' || c == '/') return false; // почта, пути и ссылки — не секрет
            if (Character.isDigit(c)) dg = true; else if (Character.isLowerCase(c)) lo = true; else if (Character.isUpperCase(c)) up = true; else sy = true;
        }
        if (dg && !lo && !up && !sy) return n <= 8; // PIN, код из СМС
        return n >= 8 && ((lo ? 1 : 0) + (up ? 1 : 0) + (dg ? 1 : 0) + (sy ? 1 : 0)) >= 3;
    }

    private static long nowMin() { return System.currentTimeMillis() / 60000L; }

    private int clipTtl() { return Math.max(1, cfg.getInt("clip_days", ClipStore.DEFAULT_DAYS)) * 1440; } // дней -> минут

    // ---------- недавние скриншоты и скопированные картинки ----------
    private boolean imgAllowed() {
        return cfg.getBoolean("shots", false) && !pwdField && Pics.accepts(ei)
                && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void findShot() {
        if (imgUri != null || !imgAllowed()) return;
        bg().post(new Runnable() { @Override public void run() {
            android.net.Uri u = Pics.latestScreenshot(LightBoardIme.this);
            if (u != null) showImage(u, R.string.chip_screenshot);
        } });
    }

    /** Из фонового потока: миниатюру готовим там, на экран выводим в главном. */
    private void showImage(final android.net.Uri u, final int label) {
        final android.graphics.Bitmap b = Pics.thumb(this, u);
        if (b == null) return;
        h.post(new Runnable() { @Override public void run() {
            imgUri = u; imgThumb = b; imgLabel = label;
            h.removeCallbacks(imgHide); h.postDelayed(imgHide, 120000L);
            applyStrip(); schedule();
        } });
    }

    private void clearImage() { imgUri = null; imgThumb = null; h.removeCallbacks(imgHide); }

    private File clipFile() { return new File(getFilesDir(), "clips.dat"); }

    private boolean clipsDirty;
    private String lastChip;

    /**
     * Чип над клавишами (скопированный текст, скриншот, картинка, код из СМС). Когда он появляется, меню над клавишами сворачивается:
     * иначе оно закрывает то, что клавиатура хочет предложить. Дальше меню можно открыть вручную, повторно оно не закроется.
     */
    private void setChip(String chip) {
        if (kv == null) return;
        if (chip != null && !chip.equals(lastChip) && toolsOn) kv.setToolsOpen(false);
        lastChip = chip;
        kv.setClip(chip);
    }
    private final Runnable saveClipsR = new Runnable() { @Override public void run() { flushClips(); } };

    /** Запись истории откладывается на секунду и идёт в фоне: шифрование через Keystore не должно занимать главный поток при каждом копировании. */
    private void saveClips() { clipsDirty = true; h.removeCallbacks(saveClipsR); h.postDelayed(saveClipsR, 1000); }

    private void flushClips() {
        h.removeCallbacks(saveClipsR);
        if (!clipsDirty) return;
        clipsDirty = false;
        try {
            final byte[] snap = clips.snapshot();
            final File f = clipFile();
            bg().post(new Runnable() { @Override public void run() { try { ClipStore.write(f, snap); } catch (IOException ignored) { } } });
        } catch (IOException ignored) { }
    }

    private void flushClipsNow() { // при закрытии службы: потока уже может не быть
        h.removeCallbacks(saveClipsR);
        if (!clipsDirty) return;
        clipsDirty = false;
        try { ClipStore.write(clipFile(), clips.snapshot()); } catch (IOException ignored) { }
    }

    /** Чип показываем и для кода из СМС, даже если «Буфер обмена в подсказках» выключен (но не в полях паролей). */
    private boolean chipOk() { return clipOk || (smsShown && !pwdField); }

    /** Длинный текст чипу целиком не нужен: рисуется только начало, а вставляется полный clipText. */
    private static String preview(String s) { return s.length() > 160 ? s.substring(0, 160) : s; }

    private void applyStrip() { if (kv != null) kv.setShowWords(showWords && (npadNow == 0 || (smsShown && !pwdField))); }

    // ---------- код из СМС ----------
    private void smsShow(String code, long ttl) {
        if (!cfg.getBoolean("sms", false)) return;
        clipText = code; smsShown = true;
        h.removeCallbacks(clipHide);
        h.postDelayed(clipHide, ttl);
    }

    private void checkSms() {
        String code = SmsReceiver.fresh(SMS_TTL); // только в памяти процесса; устаревший код сам стирается
        if (code != null && !smsShown) smsShow(code, SMS_TTL - SmsReceiver.age());
    }

    private void clearSmsStore() { SmsReceiver.clear(); }

    @Override public void onClipTap(boolean dismiss) {
        if (imgUri != null) { // картинка в подсказках: нажатие вставляет, крестик убирает
            InputConnection pic = getCurrentInputConnection();
            if (!dismiss && pic != null) Pics.commit(this, pic, imgUri);
            clearImage(); schedule();
            return;
        }
        String t = clipText;
        clipText = null;
        h.removeCallbacks(clipHide);
        if (smsShown) { smsShown = false; clearSmsStore(); applyStrip(); }
        if (!dismiss && t != null) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) { resetFlags(); ic.commitText(t, 1); }
        }
        schedule();
    }

    // ---------- действия клавиш ----------
    private void resetFlags() { capsNeed = true; swipeAlt = false; acArmed = false; afterPunct = 0; trailAutoSpace = false; wordBuf.setLength(0); }

    @Override public void onChar(String s) {
        if (toolsOn && kv != null) kv.setToolsOpen(false); // началась печать: меню сворачивается и освобождает панель подсказок
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        boolean letters = kv.getPage() == 0;
        String out = (shift != OFF && letters) ? lay.upperKeep(s) : s;
        if (smartDash && out.length() == 1 && out.charAt(0) == '-') { // «умное тире»: после пробела или в начале строки дефис становится тире
            CharSequence pb = ic.getTextBeforeCursor(1, 0);
            if (pb == null || pb.length() == 0 || pb.charAt(0) == ' ' || pb.charAt(0) == '\n') out = "\u2014";
        }
        char c0 = out.charAt(0);
        swipeAlt = false;
        boolean isPunct = out.length() == 1 && PUNCT.indexOf(c0) >= 0;

        if (isPunct) {
            boolean corrected = !addrField && tryAutocorrect(ic, out);
            if (trailAutoSpace) { // «слово␣» + «.» -> «слово.»
                CharSequence b = ic.getTextBeforeCursor(1, 0);
                if (b != null && b.length() == 1 && b.charAt(0) == ' ') ic.deleteSurroundingText(1, 0);
            }
            ic.commitText(out, 1);
            trailAutoSpace = false;
            afterPunct = autoSpace ? c0 : 0;
            acArmed = corrected;
            if (corrected) acTrig = out;
        } else {
            if (afterPunct != 0 && Character.isLetter(c0)) out = autoSpaceBefore(ic, out);
            ic.commitText(out, 1);
            afterPunct = 0; trailAutoSpace = false; acArmed = false;
        }
        if (calcOn && out.equals("=")) { // «12+3*4=» -> «12+3*4=24»
            CharSequence cb = ic.getTextBeforeCursor(81, 0);
            String res = cb == null || cb.length() < 2 ? null : Calc.solve(cb.subSequence(0, cb.length() - 1));
            if (res != null) ic.commitText(res, 1);
        }
        if (!pwdField && !isPunct && out.length() == 1 && Character.isLetter(out.charAt(0))) wordBuf.append(out); else wordBuf.setLength(0);
        if (shift == ON && letters) { shift = OFF; kv.setShift(OFF); }
        shiftManual = false;
        if (out.length() != 1 || !Character.isLetterOrDigit(out.charAt(0))) capsNeed = true;
        schedule();
    }

    /** После «слово.» или «слово,» следующая буква получает пробел перед собой (и заглавную после точки). */
    private String autoSpaceBefore(InputConnection ic, String out) {
        char p = afterPunct;
        afterPunct = 0;
        CharSequence b = ic.getTextBeforeCursor(3, 0);
        if (b == null || b.length() < 2 || b.charAt(b.length() - 1) != p) return out;
        char before = b.charAt(b.length() - 2);
        boolean doIt;
        if (p == ',') {
            doIt = !Character.isDigit(before) && before != ' ';
        } else {
            // точку, двоеточие и т.д. считаем концом предложения только после настоящего слова (не «т.е», не «сайт.ру»)
            boolean longWord = b.length() >= 3 && Learner.wordChar(b.charAt(b.length() - 3));
            doIt = Character.isLetter(before) && longWord && wordBeforeIsKnown(ic);
        }
        if (!doIt) return out;
        ic.commitText(" ", 1);
        if (autoCaps && SENTENCE_END.indexOf(p) >= 0 && ei != null && (ei.inputType & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0)
            out = out.toUpperCase(Locale.ROOT);
        return out;
    }

    private boolean wordBeforeIsKnown(InputConnection ic) {
        Lexicon lex = Lexicon.peek(lay.id);
        if (lex == null || lex.n < 15000) return false;
        CharSequence b = ic.getTextBeforeCursor(34, 0);
        if (b == null) return false;
        int e = b.length() - 1, s = e; // e указывает на знак
        while (s > 0 && Learner.wordChar(b.charAt(s - 1))) s--;
        if (e - s < 2) return false;
        String w = b.subSequence(s, e).toString().toLowerCase(Locale.ROOT);
        return lex.contains(w) || learner.isUser(w);
    }

    @Override public void onBack() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        capsNeed = true;
        if (acArmed && acNew != null) { // откат автоисправления: возвращаем слово как набрано и запоминаем его
            acArmed = false;
            String was = acNew + acTrig;
            CharSequence tail = ic.getTextBeforeCursor(was.length(), 0);
            if (tail != null && was.contentEquals(tail)) { // курсор мог уйти в другое место: тогда откатывать нельзя, сотрёт чужой текст
                ic.deleteSurroundingText(was.length(), 0);
                ic.commitText(acOrig + acTrig, 1);
                learn(acOrig.toLowerCase(Locale.ROOT));
                schedule();
                return;
            }
        }
        if (swipeAlt && swipeInsLen > 0) { // сразу после непрерывного ввода одно нажатие стирает слово целиком
            ic.deleteSurroundingText(swipeInsLen, 0);
            swipeAlt = false; swipeInsLen = 0; afterPunct = 0; trailAutoSpace = false;
            schedule();
            return;
        }
        swipeAlt = false; afterPunct = 0; trailAutoSpace = false;
        if (wordBuf.length() > 0) wordBuf.setLength(wordBuf.length() - 1);
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) {
            ic.commitText("", 1);
        } else {
            CharSequence b = ic.getTextBeforeCursor(2, 0);
            if (b == null) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); }
            else {
                int n = (b.length() == 2 && Character.isHighSurrogate(b.charAt(0)) && Character.isLowSurrogate(b.charAt(1))) ? 2 : 1;
                ic.deleteSurroundingText(n, 0);
            }
        }
        schedule();
    }

    @Override public void onShift() {
        long now = SystemClock.uptimeMillis();
        if (shift == LOCK) shift = OFF;
        else if (shift == ON && now - lastShiftTap < 350) shift = LOCK; // двойной тап = Caps Lock
        else if (shift == ON) shift = OFF;
        else shift = ON;
        shiftManual = true;
        lastShiftTap = now;
        kv.setShift(shift);
    }

    @Override public void onSpace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        capsNeed = true;
        long now = SystemClock.uptimeMillis();
        boolean dbl = now - lastSpace < 400;
        lastSpace = now;
        swipeAlt = false; afterPunct = 0; trailAutoSpace = false;
        if (dbl && autoPeriod) { // двойной пробел -> ". "
            CharSequence b = ic.getTextBeforeCursor(2, 0);
            if (b != null && b.length() == 2 && b.charAt(1) == ' ' && Character.isLetterOrDigit(b.charAt(0))) {
                ic.deleteSurroundingText(1, 0);
                ic.commitText(". ", 1);
                acArmed = false; shiftManual = false;
                schedule();
                return;
            }
        }
        boolean corrected = tryAutocorrect(ic, " ");
        wordBuf.setLength(0);
        ic.commitText(" ", 1);
        acArmed = corrected;
        if (corrected) acTrig = " ";
        shiftManual = false;
        schedule();
    }

    @Override public void onEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        capsNeed = true;
        swipeAlt = false; afterPunct = 0; trailAutoSpace = false; acArmed = false;
        if (ei != null) {
            int a = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            boolean noAction = (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
            if (!noAction && a != EditorInfo.IME_ACTION_NONE && a != EditorInfo.IME_ACTION_UNSPECIFIED) {
                tryAutocorrect(ic, "", false); // Enter/поиск: слово не меняем
                wordBuf.setLength(0);
                ic.performEditorAction(a);
                return;
            }
        }
        tryAutocorrect(ic, "\n", false);
        wordBuf.setLength(0);
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        acArmed = false;
    }

    // ---------- автоисправление ----------
    private void learn(String low) { if (!noLearn) learner.learnUser(low); } // сохранится при скрытии клавиатуры

    /** Слово дошло до конца (пробел, знак): считаем его и запоминаем, что оно шло после предыдущего. */
    private void finish(String fin, CharSequence b, int s) {
        if (noLearn) return;
        learner.bump(fin, kindOf(fin));
        if (b == null) return;
        String pv = Learner.prevWord(b, s, 32);
        if (pv != null) learner.pair(pv, fin);
    }

    /** Заменяет только что набранное слово, если его нет в словаре и есть уверенная правка. true — слово заменено. */
    private boolean tryAutocorrect(InputConnection ic, String trigger) { return tryAutocorrect(ic, trigger, true); }

    /** replace=false: слово только запоминается (Enter, поиск), но не заменяется. */
    private boolean tryAutocorrect(InputConnection ic, String trigger, boolean replace) {
        if (!suggestOn) return false;
        Lexicon lex = Lexicon.peek(lay.id);
        boolean canCorrect = acOn && lex != null && lex.n >= 15000; // с маленьким словарём исправлять опасно
        CharSequence b = ic.getTextBeforeCursor(32, 0);
        String w;
        boolean ctx = b != null && b.length() > 0; // false: приложение не отдаёт текст, берём своё слово
        int s = 0;
        if (ctx) {
            int e = b.length();
            s = e;
            while (s > 0 && Learner.wordChar(b.charAt(s - 1))) s--;
            if (s == e) return false;
            if (e - s < 3) { finish(b.subSequence(s, e).toString().toLowerCase(Locale.ROOT), b, s); return false; } // короткие слова не исправляем, но учим
            if (s > 0) { // слово — часть токена («it's», «a@b», «x.y», «a1») — не трогаем
                char pc = b.charAt(s - 1);
                if (pc == '\u0301' || pc == '\'' || pc == '@' || pc == '-' || pc == '_' || pc == '/' || pc == '.' || pc == '#' || Character.isDigit(pc)) return false;
            }
            w = b.subSequence(s, e).toString();
        } else {
            if (wordBuf.length() < 3) return false;
            w = wordBuf.toString();
        }
        if (w.length() > 1 && w.equals(w.toUpperCase(Locale.ROOT))) return false;
        boolean cap = Character.isUpperCase(w.charAt(0));
        if (cap) { // заглавное слово не в начале предложения — скорее имя
            if (!ctx) return false;
            int k = s - 1;
            while (k >= 0 && b.charAt(k) == ' ') k--;
            boolean start = (k < 0 && b.length() < 32) || (k >= 0 && (SENTENCE_END.indexOf(b.charAt(k)) >= 0 || b.charAt(k) == '\n'));
            if (!start) { finish(w.toLowerCase(Locale.ROOT), b, s); return false; } // имена тоже запоминаем
        }
        String low = w.toLowerCase(Locale.ROOT);
        if (learner.known(low) || !canCorrect || !replace) { finish(low, ctx ? b : null, s); return false; }
        String corr = low.equals(lastCorrFor) ? lastCorr : lex.correct(low);
        if (corr == null) { finish(low, ctx ? b : null, s); return false; }
        String out = cap ? cap(corr) : corr;
        if (!ic.deleteSurroundingText(w.length(), 0)) return false; // поле не умеет заменять текст — лучше не трогать
        ic.commitText(out, 1);
        acOrig = w; acNew = out;
        finish(corr, ctx ? b : null, s);
        return true;
    }

    // ---------- непрерывный ввод ----------
    @Override public void onSwipe(String[] words, int n) {
        if (toolsOn && kv != null) kv.setToolsOpen(false);
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || n <= 0) return;
        String w = words[0];
        if (shift == LOCK) w = w.toUpperCase(Locale.ROOT);
        else if (shift == ON) w = cap(w);
        CharSequence b = ic.getTextBeforeCursor(32, 0);
        char lc = b != null && b.length() > 0 ? b.charAt(b.length() - 1) : ' ';
        boolean sp = Character.isLetterOrDigit(lc) || PUNCT.indexOf(lc) >= 0;
        ic.commitText((sp ? " " : "") + w + " ", 1);
        afterPunct = 0; acArmed = false; trailAutoSpace = true; wordBuf.setLength(0); capsNeed = true;
        swipeLen = w.length() + 1;
        swipeInsLen = (sp ? 1 : 0) + w.length() + 1;
        observe(b, w.toLowerCase(Locale.ROOT));
        // остальные варианты — в полосу подсказок: тап заменяет только что вставленное слово
        for (int i = 0; i < 3; i++) { shown[i] = null; commit[i] = null; }
        for (int i = 0; i < n; i++) {
            String a = i == 0 ? w : (shift == LOCK ? words[i].toUpperCase(Locale.ROOT) : (shift == ON ? cap(words[i]) : words[i]));
            int slot = i == 0 ? 1 : (i == 1 ? 0 : 2);
            shown[slot] = a; commit[slot] = a;
        }
        swipeAlt = true; swipeAwait = true;
        if (shift == ON) { shift = OFF; kv.setShift(OFF); }
        shiftManual = false;
        setChip(null);
        kv.setSuggestions(shown[0], shown[1], shown[2]);
        schedule();
    }

    /** Языки, между которыми переключает глобус: список из настроек (по умолчанию английский и язык телефона). */
    /** Переключение по глобусу идёт по языкам, включённым в системе («Многоязычный ввод»): список один и тот же и там, и здесь. */
    @Override public void onLang() {
        ArrayList<InputMethodSubtype> subs = new ArrayList<>();
        ArrayList<Layouts.L> el = SysLangs.enabled(this, subs);
        if (el.isEmpty()) return;
        int j = (el.indexOf(lay) + 1) % el.size();
        switchTo(el.get(j));
        if (Build.VERSION.SDK_INT >= 28 && j < subs.size()) { // сообщаем системе, какой язык теперь выбран
            try { switchInputMethod(new ComponentName(this, LightBoardIme.class).flattenToShortString(), subs.get(j)); } catch (RuntimeException ignored) { }
        }
    }

    private void applyFlags() {
        suggestOn = fieldOk && lay.own();
        acOn = suggestOn && cfg.getBoolean(P() + "autocorrect", true);
        autoSpace = suggestOn && !addrField && cfg.getBoolean(P() + "autospace", true);
        if (kv != null) kv.setSwipeEnabled(suggestOn && cfg.getBoolean(P() + "swipe", true));
    }

    private void switchTo(Layouts.L l) {
        if (l == null || kv == null) return;
        if (l.id.equals(lay.id)) { if (l != lay) { lay = l; kv.setLayout(l); } return; } // тот же язык: только обновить объект раскладки
        lay = l; ru = l.ru;
        state.edit().putString("lay", l.id).apply();
        kv.setLayout(l); lastCorrFor = null; wordBuf.setLength(0);
        applyFlags();
        if (l.own()) Lexicon.preload(this, lay.id, scheduleR); else Lexicon.invalidate();
        schedule();
    }

    @Override public void onEmojiKey(boolean longPress) {
        boolean globe = cfg.getBoolean(P() + "globe", false);
        if (longPress == globe) showEmoji(); else onLang();
    }

    // ---------- голосовой ввод: системный распознаватель (Google), текст вставляется прямо из клавиатуры ----------
    private SpeechRecognizer sr;
    private boolean listening;
    private final Runnable startListenR = new Runnable() { @Override public void run() { startListening(); } };

    @Override public void onMic() {
        if (listening) stopListening(); else startListening();
    }

    private void openMicPermission() {
        try {
            Intent i = new Intent(this, VoiceActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(i);
        } catch (RuntimeException e) { Diag.save(this, e); }
    }

    /** Android 12+: сначала распознаватель на устройстве (звук не уходит в сеть); нет языкового пакета — обычный системный. */
    private void startListening() {
        startListening(Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this));
    }

    private void startListening(final boolean onDevice) {
        if (kv == null) return;
        if (sr != null) stopListening(); // предыдущий распознаватель не бросаем
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { openMicPermission(); return; }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.speech_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            sr = onDevice ? SpeechRecognizer.createOnDeviceSpeechRecognizer(this) : SpeechRecognizer.createSpeechRecognizer(this);
            sr.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) { }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rms) { }
                @Override public void onBufferReceived(byte[] b) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onEvent(int t, Bundle p) { }
                @Override public void onPartialResults(Bundle b) {
                    ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (r != null && !r.isEmpty() && showWords) kv.setSuggestions(null, r.get(0), null);
                }
                @Override public void onResults(Bundle b) {
                    ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String v = r == null || r.isEmpty() ? null : r.get(0);
                    stopListening();
                    if (v != null) commitSpoken(v);
                }
                @Override public void onError(int code) {
                    stopListening();
                    if (onDevice && (code == 12 || code == 13)) { startListening(false); return; } // ERROR_LANGUAGE_NOT_SUPPORTED / UNAVAILABLE: пакета языка на устройстве нет
                    if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) openMicPermission();
                    else if (code != SpeechRecognizer.ERROR_NO_MATCH && code != SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                        Toast.makeText(LightBoardIme.this, getString(R.string.speech_error, code), Toast.LENGTH_SHORT).show();
                }
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lay.tag);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true); // если языковой пакет скачан, обычный распознаватель тоже работает без сети
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            sr.startListening(i);
            if (toolsOn) kv.setToolsOpen(false); // идёт диктовка: панель нужна для текста
            listening = true;
            kv.setListening(true);
        } catch (RuntimeException e) { Diag.save(this, e); stopListening(); }
    }

    private void stopListening() {
        listening = false;
        if (kv != null) kv.setListening(false);
        if (sr != null) { try { sr.cancel(); sr.destroy(); } catch (RuntimeException ignored) { } sr = null; }
        schedule();
    }

    private void commitSpoken(String v) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || v.isEmpty()) return;
        CharSequence b = ic.getTextBeforeCursor(1, 0);
        boolean sp = b != null && b.length() == 1 && (Character.isLetterOrDigit(b.charAt(0)) || PUNCT.indexOf(b.charAt(0)) >= 0);
        if (autoCaps && ei != null && ic.getCursorCapsMode(ei.inputType) != 0) v = cap(v);
        resetFlags();
        ic.commitText((sp ? " " : "") + v, 1);
        schedule();
    }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        if (attribute != null) ei = attribute;
        if (VoiceActivity.takeGranted()) { h.removeCallbacks(startListenR); h.postDelayed(startListenR, 500); } // разрешение только что выдали
    }

    /** Системный переключатель раскладок: подхватываем смену подтипа (и при открытии, если система сменила его без уведомления). */
    private void adoptSubtype(InputMethodSubtype st) {
        if (st == null || st.getLocale() == null) return;
        String loc = st.getLocale();
        if (loc.equals(state.getString("sys_subtype", ""))) return; // с прошлого раза не менялся — оставляем свой выбор языка
        state.edit().putString("sys_subtype", loc).apply();
        switchTo(Layouts.byLang(loc)); // язык подтипа без раскладки игнорируется
    }

    @Override public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype st) {
        super.onCurrentInputMethodSubtypeChanged(st);
        adoptSubtype(st);
    }

    // ---------- жесты ----------
    /** Tab на планшетной раскладке. */
    @Override public void onSpecial(int keyCode) {
        resetFlags();
        sendDownUpKeyEvents(keyCode);
        schedule();
    }

    /** Caps Lock на планшетной раскладке: включает и выключает постоянные заглавные. */
    @Override public void onCaps() {
        shift = shift == LOCK ? OFF : LOCK;
        shiftManual = true;
        kv.setShift(shift);
    }

    @Override public void onCursorV(int dir) {
        resetFlags();
        sendDownUpKeyEvents(dir < 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN);
        schedule();
    }

    @Override public void onCursor(int dir) {
        resetFlags();
        sendDownUpKeyEvents(dir < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT);
        schedule();
    }

    @Override public void onClearAll() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        resetFlags();
        ic.beginBatchEdit();
        ic.performContextMenuAction(android.R.id.selectAll);
        ic.commitText("", 1);
        CharSequence b = ic.getTextBeforeCursor(1, 0), a = ic.getTextAfterCursor(1, 0);
        if ((b != null && b.length() > 0) || (a != null && a.length() > 0)) ic.deleteSurroundingText(1000000, 1000000);
        ic.endBatchEdit();
        schedule();
    }

    @Override public void onOpenSettings() {
        try {
            Intent i = new Intent(this, SettingsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (RuntimeException e) { Diag.save(this, e); }
    }

    /** Вид сообщил о повторном сбое рисования: пересоздаём его (не чаще раза в 3 с). */
    @Override public void onBroken() {
        long now = SystemClock.uptimeMillis();
        if (now - lastHeal < 3000) return;
        lastHeal = now;
        h.post(new Runnable() { @Override public void run() { swapBoard(gboard); refresh(); } });
    }

    /** Окончательное состояние вида: прозрачность, видимость, сдвиги не должны оставаться от прерванных анимаций. */
    private void settleBoard() {
        if (kvView == null) return;
        if (!kv.healthy() && kvView.getWidth() > 0) { swapBoard(gboard); return; } // вид собран неверно — пересоздаём
        if (emojiShown || clipShown) return;
        kvView.animate().cancel();
        kvView.setAlpha(1f); kvView.setVisibility(View.VISIBLE);
        if (emoji != null) emoji.setVisibility(View.GONE);
        if (clipPanel != null) clipPanel.setVisibility(View.GONE);
    }

    @Override public void onToolsToggle(boolean open) {
        toolsOn = open;
        if (autofill != null) { autofill.setHidden(open); if (kv != null) kv.setStripHidden(autofill.has() && !open); }
    }

    /** Автозаполнение: службе нужен размер чипов; без включённой настройки отвечаем «не надо». */
    @Override public InlineSuggestionsRequest onCreateInlineSuggestionsRequest(Bundle uiExtras) {
        if (Build.VERSION.SDK_INT < 30 || cfg == null || kv == null || !cfg.getBoolean("autofill", true)) return null;
        Theme at = activeTheme();
        return AutofillBar.request(kv.strip()[2], getResources().getDisplayMetrics().density, at.text, at.muted);
    }

    @Override public boolean onInlineSuggestionsResponse(InlineSuggestionsResponse response) {
        if (Build.VERSION.SDK_INT < 30 || root == null || kv == null || kvView == null) return false;
        if (autofill == null) autofill = new AutofillBar(this, root, new AutofillBar.Cb() {
            @Override public void shown(boolean on) {
                if (kv == null) return;
                if (on && toolsOn) kv.setToolsOpen(false); // автозаполнение предложило вариант: меню сворачивается
                kv.setStripHidden(on && !toolsOn);
            }
        });
        autofill.show(response.getInlineSuggestions(), kv.strip(), (FrameLayout.LayoutParams) kvView.getLayoutParams());
        return true;
    }

    @Override public void onKeySound(int kind) {
        if (!soundOn) return;
        KeySounds s = sounds;
        if (s == null) { warmSounds(); return; } // на первом нажатии главный поток не блокируем
        s.play(kind, soundVol);
    }

    /** Звуки готовятся в фоне (чтение, синтез, запись файлов), на главный поток возвращается готовый объект. */
    private void warmSounds() {
        if (sounds != null || soundsLoading) return;
        soundsLoading = true;
        final android.content.Context app = getApplicationContext();
        bg().post(new Runnable() { @Override public void run() {
            final KeySounds ks = new KeySounds(app);
            h.post(new Runnable() { @Override public void run() {
                soundsLoading = false;
                if (soundOn && sounds == null) sounds = ks; else ks.release();
            } });
        } });
    }

    // ---------- меню над клавишами, режимы, история буфера ----------
    @Override public void onMenu(int id) {
        switch (id) {
            case Board.MENU_CLIP: showClips(); break;
            case Board.MENU_ONEHAND: setOneHandMode(oneHand == 0 ? state.getInt("onehand_side", 2) : 0); break;
            case Board.MENU_SPLIT: setSplitMode(!split); break;
            default: onOpenSettings(); break;
        }
    }

    private void setSplitMode(boolean on) {
        split = on;
        state.edit().putBoolean("split", on).apply();
        if (kv != null) kv.setSplit(on);
    }

    private void setOneHandMode(int m) {
        oneHand = m;
        SharedPreferences.Editor ed = state.edit().putInt("onehand", m);
        if (m != 0) ed.putInt("onehand_side", m);
        ed.apply();
        applyOneHand(true);
    }

    /**
     * Режим одной руки: клавиатура занимает часть ширины и прижимается к краю, рядом — SideBar с кнопками. В ландшафте не применяется.
     * Переходы анимируются только преобразованием вида (без пересчёта клавиш на каждом кадре): сжатие в угол, расширение, перенос на другую сторону.
     */
    private void applyOneHand(boolean animate) {
        if (root == null || kv == null) return;
        int total = getResources().getDisplayMetrics().widthPixels;
        boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int mode = (land && !Ui.tablet(this)) ? 0 : oneHand; // на планшете режим работает и в альбомной ориентации
        kv.setOneHand(oneHand);
        if (!animate && SystemClock.uptimeMillis() > ohAnimEnd) settleOneHand(mode); // после прерванной анимации не остаётся прозрачных или сдвинутых видов
        int key = mode == 0 ? 0 : (mode << 20) | total;
        if (key == ohApplied) return;
        int prev = ohMode;
        ohApplied = key; ohMode = mode;
        final int kw = (int) (total * ONE_HAND_FRAC), free = total - kw;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) kvView.getLayoutParams();
        if (mode == 0) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT; lp.gravity = -1;
        } else {
            lp.width = kw; lp.gravity = (mode == 2 ? Gravity.END : Gravity.START) | Gravity.TOP;
            if (side == null) {
                side = new SideBar(this, activeTheme());
                side.setMate(kvView);
                side.setListener(new SideBar.Listener() {
                    @Override public void onExpand() { setOneHandMode(0); }
                    @Override public void onFlip() { setOneHandMode(oneHand == 1 ? 2 : 1); }
                });
                root.addView(side, new FrameLayout.LayoutParams(free, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            FrameLayout.LayoutParams sp = (FrameLayout.LayoutParams) side.getLayoutParams();
            sp.width = free; sp.gravity = (mode == 2 ? Gravity.START : Gravity.END) | Gravity.TOP;
            side.setLayoutParams(sp);
            side.setLeftSide(mode == 2);
            side.setVisibility(View.VISIBLE);
        }
        kvView.setLayoutParams(lp);
        kvView.animate().cancel(); kvView.setScaleX(1f); kvView.setTranslationX(0f);
        if (side != null) {
            side.animate().cancel(); side.setAlpha(1f); side.setTranslationX(0f);
            if (mode == 0 && !(animate && prev != 0)) side.setVisibility(View.GONE);
        }
        if (!animate || prev == mode) return;
        final float dist = free;
        ohAnimEnd = SystemClock.uptimeMillis() + 500;
        DecelerateInterpolator dec = new DecelerateInterpolator(1.6f);
        if (prev == 0) { // клавиатура сжимается в угол, рядом выезжает панель
            kvView.setPivotX(mode == 2 ? kw : 0f);
            kvView.setScaleX(1f / ONE_HAND_FRAC);
            kvView.animate().scaleX(1f).setDuration(340).setInterpolator(new OvershootInterpolator(0.8f)).start();
            side.setAlpha(0f); side.setTranslationX(mode == 2 ? -dist * 0.6f : dist * 0.6f);
            side.animate().alpha(1f).translationX(0f).setStartDelay(60).setDuration(300).setInterpolator(dec).start();
        } else if (mode == 0) { // выход: клавиатура растёт на всю ширину, панель уходит
            kvView.setPivotX(prev == 2 ? total : 0f);
            kvView.setScaleX(ONE_HAND_FRAC);
            kvView.animate().scaleX(1f).setDuration(280).setInterpolator(dec).start();
            if (side != null) {
                side.setVisibility(View.VISIBLE);
                side.animate().alpha(0f).translationX(prev == 2 ? -dist * 0.6f : dist * 0.6f).setDuration(200).withEndAction(new Runnable() {
                    @Override public void run() { if (ohMode == 0 && side != null) { side.setVisibility(View.GONE); side.setAlpha(1f); side.setTranslationX(0f); } }
                }).start();
            }
        } else { // перенос на другую сторону: клавиатура и панель меняются местами
            kvView.setTranslationX(mode == 2 ? -dist : dist);
            kvView.animate().translationX(0f).setDuration(380).setInterpolator(new OvershootInterpolator(0.7f)).start();
            side.setTranslationX(mode == 2 ? dist : -dist);
            side.animate().translationX(0f).setDuration(380).setInterpolator(new OvershootInterpolator(0.7f)).start();
        }
    }

    private void settleOneHand(int mode) {
        if (kvView == null) return;
        kvView.setScaleX(1f); kvView.setTranslationX(0f);
        if (side == null) return;
        if (mode == 0) { root.removeView(side); side = null; return; } // выключено — вид не держим в памяти
        side.animate().cancel(); side.setAlpha(1f); side.setTranslationX(0f); side.setVisibility(View.VISIBLE);
    }

    /** Значения, которые меняют на экране настроек, подхватываются при открытии клавиатуры; кнопки меню переключают на лету. */
    private void syncModes() {
        String oh = cfg.getString("onehand", null);
        if (oh != null && !oh.equals(state.getString("onehand_seen", null))) {
            state.edit().putString("onehand_seen", oh).apply();
            setOneHandMode("left".equals(oh) ? 1 : "right".equals(oh) ? 2 : 0);
        }
        if (cfg.contains("split")) {
            boolean v = cfg.getBoolean("split", false);
            if (state.getInt("split_seen", -1) != (v ? 1 : 0)) { state.edit().putInt("split_seen", v ? 1 : 0).apply(); setSplitMode(v); }
        }
        kv.setSplit(split);
        applyOneHand(false);
        long cc = cfg.getLong("clip_clear", 0L);
        boolean wipe = cc > state.getLong("clip_clear_done", 0L) || (!cfg.getBoolean("clip_hist", true) && clips.size() > 0); // выключили историю или попросили очистить
        if (wipe) { clips.clearAll(); saveClips(); state.edit().putLong("clip_clear_done", cc).apply(); }
        else if (clips.expire(nowMin(), clipTtl())) saveClips();
    }

    private void showClips() {
        if (clipShown || root == null || kv == null) return;
        if (emojiShown) hideEmoji(false);
        if (clipPanel != null && clipH != kvView.getHeight()) { root.removeView(clipPanel); clipPanel = null; }
        if (clipPanel == null) {
            clipH = kvView.getHeight();
            clipPanel = new ClipView(this, activeTheme(), clipH);
            clipPanel.setListener(new ClipView.Listener() {
                @Override public void onPaste(String t) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) { resetFlags(); ic.commitText(t, 1); }
                    if (cfg.getBoolean("clip_close", true)) hideClips(true);
                }
                @Override public void onAbc() { hideClips(true); }
                @Override public void onChanged() { saveClips(); }
            });
            root.addView(clipPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (clips.expire(nowMin(), clipTtl())) saveClips();
        clipPanel.open(clips, cfg.getBoolean("clip_hist", true));
        float dy = 20 * getResources().getDisplayMetrics().density;
        clipPanel.animate().cancel(); kvView.animate().cancel();
        clipPanel.setVisibility(View.VISIBLE); clipPanel.setAlpha(0f); clipPanel.setTranslationY(dy);
        clipPanel.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        kv.cancelTouches();
        kvView.animate().alpha(0f).setDuration(150).withEndAction(new Runnable() {
            @Override public void run() { if (clipShown) kvView.setVisibility(View.INVISIBLE); }
        }).start();
        clipShown = true;
    }

    private void hideClips(boolean animate) {
        if (!clipShown || clipPanel == null) return;
        clipShown = false;
        clipPanel.animate().cancel(); kvView.animate().cancel();
        kvView.setVisibility(View.VISIBLE);
        if (!animate) { clipPanel.setVisibility(View.GONE); kvView.setAlpha(1f); return; }
        float dy = 20 * getResources().getDisplayMetrics().density;
        kvView.setAlpha(0f);
        kvView.animate().alpha(1f).setDuration(200).start();
        clipPanel.animate().alpha(0f).translationY(dy).setDuration(170).withEndAction(new Runnable() {
            @Override public void run() { if (!clipShown && clipPanel != null) clipPanel.setVisibility(View.GONE); }
        }).start();
    }

    // ---------- самообучение (логика в Learner) ----------
    /** Слово вставлено подсказкой или жестом: считаем его и запоминаем пару с предыдущим. */
    private void observe(CharSequence before, String low) {
        if (noLearn) return;
        learner.bump(low, kindOf(low));
        if (before == null) return;
        String pv = Learner.prevWord(before, before.length(), 32);
        if (pv != null) learner.pair(pv, low);
    }

    /** 0 — слово из словаря, 1 — нет в словаре и не похоже на опечатку (своё), 2 — похоже на опечатку, 3 — словаря нет (считаем только частоту). */
    private int kindOf(String w) {
        final Lexicon lx = lay.own() ? Lexicon.peek(lay.id) : null;
        if (addrField || lx == null) return 3;
        if (!ownPruned) { ownPruned = true; learner.pruneOwn(new Learner.Has() { @Override public boolean has(String x) { return lx.contains(x); } }); }
        if (lx.contains(w)) return 0;
        if (lx.n < 15000 || w.length() < 3) return 1;
        String c = w.equals(lastCorrFor) ? lastCorr : lx.correct(w);
        return c != null ? 2 : 1;
    }

    private void spellRefresh(InputConnection ic) {
        CharSequence b = ic.getTextBeforeCursor(32, 0);
        int e = b == null ? 0 : b.length(), st = e;
        while (st > 0 && Learner.wordChar(b.charAt(st - 1))) st--;
        prefix = b == null ? "" : b.subSequence(st, e).toString();
        for (int i = 0; i < 3; i++) { shown[i] = null; commit[i] = null; }
        kv.setSuggestions(null, null, null);
        if (prefix.length() < 2) return;
        if (spell == null) spell = new SysSpell();
        spell.ask(this, lay.tag, prefix, new SysSpell.Cb() {
            @Override public void got(final String word, final String[] sug) {
                h.post(new Runnable() {
                    @Override public void run() {
                        if (kv == null || !word.equals(prefix)) return;
                        boolean up = Character.isUpperCase(word.charAt(0));
                        for (int i = 0; i < 3; i++) { String w = i < sug.length ? sug[i] : null; shown[i] = w == null ? null : (up ? cap(w) : w); commit[i] = shown[i]; }
                        kv.setSuggestions(shown[0], shown[1], shown[2]);
                    }
                });
            }
        });
    }

    /** Копия из файла: настройки кладут import.json в filesDir; применяем и удаляем (в общих настройках ничего не остаётся). */
    private void applyImport() {
        File f = new File(getFilesDir(), "import.json");
        if (!f.exists()) return;
        try {
            byte[] b = new byte[(int) Math.min(f.length(), 4_000_000L)];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int p = 0, n;
                while (p < b.length && (n = in.read(b, p, b.length - p)) > 0) p += n;
            }
            JSONObject o = new JSONObject(new String(b, "UTF-8"));
            JSONObject l = o.optJSONObject("learn");
            if (l != null) {
                learner.clear();
                learner.loadUser(l.optString("user_words", ""));
                learner.loadWords(l.optString("learned", ""));
                learner.loadPairs(l.optString("pairs", ""));
                learner.loadOwn(l.has("own") ? l.optString("own", "") : null);
                learner.markAllDirty();
                saveLearned();
            }
            String c = o.optString("clips", null);
            if (c != null) { clips.load(c); saveClips(); }
        } catch (IOException | JSONException | RuntimeException ignored) { }
        f.delete();
    }

    private void saveLearned() {
        String w = learner.saveWords(), p = learner.savePairs(), u = learner.saveUser(), o = learner.saveOwn();
        if (w == null && p == null && u == null && o == null) return; // с прошлого раза ничего не изменилось — диск не трогаем
        SharedPreferences.Editor ed = state.edit();
        if (w != null) ed.putString("learned", w);
        if (p != null) ed.putString("pairs", p);
        if (u != null) ed.putString("user_words", u);
        if (o != null) ed.putString("own", o);
        ed.apply();
    }

    // ---------- панель эмодзи ----------
    private void showEmoji() {
        if (emojiShown || root == null || kv == null) return;
        if (emoji != null && emojiH != kvView.getHeight()) { root.removeView(emoji); emoji = null; }
        if (emoji == null) {
            emojiH = kvView.getHeight();
            emoji = new EmojiView(this, activeTheme(), emojiH);
            emoji.setListener(new EmojiView.Listener() {
                @Override public void onEmoji(String e) { pickEmoji(e); }
                @Override public void onBack() { LightBoardIme.this.onBack(); }
                @Override public void onAbc() { hideEmoji(true); }
            });
            root.addView(emoji, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        emoji.open(state.getString("recent", ""));
        float dy = 20 * getResources().getDisplayMetrics().density;
        emoji.animate().cancel(); kvView.animate().cancel();
        emoji.setVisibility(View.VISIBLE); emoji.setAlpha(0f); emoji.setTranslationY(dy);
        emoji.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        kv.cancelTouches();
        kvView.animate().alpha(0f).setDuration(150).withEndAction(new Runnable() {
            @Override public void run() { if (emojiShown) kvView.setVisibility(View.INVISIBLE); }
        }).start();
        emojiShown = true;
    }

    private void hideEmoji(boolean animate) {
        if (!emojiShown || emoji == null) return;
        emojiShown = false;
        emoji.animate().cancel(); kvView.animate().cancel();
        kvView.setVisibility(View.VISIBLE);
        if (!animate) { emoji.setVisibility(View.GONE); kvView.setAlpha(1f); return; }
        float dy = 20 * getResources().getDisplayMetrics().density;
        kvView.setAlpha(0f);
        kvView.animate().alpha(1f).setDuration(200).start();
        emoji.animate().alpha(0f).translationY(dy).setDuration(170).withEndAction(new Runnable() {
            @Override public void run() { if (!emojiShown) emoji.setVisibility(View.GONE); }
        }).start();
    }

    private void pickEmoji(String e) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        resetFlags();
        ic.commitText(e, 1);
        StringBuilder sb = new StringBuilder(e);
        String old = state.getString("recent", "");
        int n = 1;
        if (!old.isEmpty()) {
            for (String o : old.split(" ")) {
                if (o.equals(e) || n >= 24) continue;
                sb.append(' ').append(o); n++;
            }
        }
        state.edit().putString("recent", sb.toString()).apply();
        schedule();
    }

    // ---------- подсказки и авто-заглавная ----------
    @Override public void onSuggest(int i) {
        if ((!suggestOn && !(fieldOk && !lay.own())) || i < 0 || i > 2 || commit[i] == null) return; // скрытые подсказки (пароли, цифровой ввод) не вставляются
        InputConnection ic = getCurrentInputConnection();
        String t = commit[i];
        if (ic == null || t == null || t.isEmpty()) return;
        if (swipeAlt) { ic.deleteSurroundingText(swipeLen, 0); swipeAlt = false; }
        else if (!prefix.isEmpty()) {
            ic.deleteSurroundingText(prefix.length(), 0);
            if (t.equalsIgnoreCase(prefix) || (i == 0 && t.equals(prefix))) learn(prefix.toLowerCase(Locale.ROOT)); // тап по «как набрано»
        }
        observe(ic.getTextBeforeCursor(32, 0), t.toLowerCase(Locale.ROOT));
        ic.commitText(t + " ", 1);
        afterPunct = 0; acArmed = false; trailAutoSpace = true; wordBuf.setLength(0); capsNeed = true;
        shiftManual = false;
        schedule();
    }

    private String enterLabel() {
        if (ei == null || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return null;
        switch (ei.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_GO:     return ru ? "Перейти" : "Go";
            case EditorInfo.IME_ACTION_SEARCH: return ru ? "Найти" : "Search";
            case EditorInfo.IME_ACTION_SEND:   return ru ? "Отпр." : "Send";
            case EditorInfo.IME_ACTION_NEXT:   return ru ? "Далее" : "Next";
            case EditorInfo.IME_ACTION_DONE:   return ru ? "Готово" : "Done";
            default: return null;
        }
    }

    /** Склеивает много обновлений подряд в одно (одна пара IPC-вызовов за кадр, а не за каждое событие). */
    private void schedule() {
        h.removeCallbacks(refreshR);
        h.post(refreshR);
    }

    private void refresh() {
        try { refreshImpl(); } catch (RuntimeException e) { Diag.save(this, e); }
    }

    private void refreshImpl() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || kv == null) return;

        if (autoCaps && capsNeed && ei != null && shift != LOCK && !shiftManual) { // авто-заглавная в начале предложения (спрашиваем у поля только когда что-то могло измениться)
            capsNeed = false;
            int ns = ic.getCursorCapsMode(ei.inputType) != 0 ? ON : OFF;
            if (ns != shift) { shift = ns; kv.setShift(ns); }
        }
        kv.setEnterLabel(enterLabel());
        if (listening) return;
        if (!showWords) { setChip(null); kv.setSuggestions(null, null, null); return; } // подсказки в настройках выключены
        boolean imgNow = imgUri != null && !pwdField;
        boolean clipNow = (clipText != null && chipOk()) || imgNow;
        String chipTxt = imgNow ? getString(imgLabel) : (clipNow ? preview(clipText) : null);
        kv.setClipImage(imgNow ? imgThumb : null);
        if (swipeAlt) { setChip(null); kv.setSuggestions(shown[0], shown[1], shown[2]); return; }
        if (!suggestOn) { setChip(chipTxt); if (fieldOk && !lay.own()) spellRefresh(ic); else kv.setSuggestions(null, null, null); return; }

        CharSequence b = ic.getTextBeforeCursor(32, 0);
        if ((b == null || b.length() == 0) && wordBuf.length() > 0) {
            prefix = wordBuf.toString(); // приложение не отдаёт текст — работаем по тому, что набрали сами
        } else {
            int e = b == null ? 0 : b.length(), s = e;
            while (s > 0 && Learner.wordChar(b.charAt(s - 1))) s--;
            prefix = b == null ? "" : b.subSequence(s, e).toString();
        }

        setChip(clipNow && prefix.isEmpty() ? chipTxt : null);
        for (int i = 0; i < 3; i++) { shown[i] = null; commit[i] = null; }
        if (prefix.isEmpty()) {
            // что вы обычно пишете после предыдущего слова (в начале предложения — чем начинаете), затем свои частые слова, затем стандартные
            int n = 0;
            String pv = b == null ? null : Learner.prevWord(b, b.length(), 32);
            if (pv != null) n = learner.next(pv, nxt, 0);
            n = learner.frequent(nxt, n, 3);
            String[] d = lay.id.equals("en") ? DEF_EN : ru ? DEF_RU : new String[0]; // стандартные слова есть только у русского и английского
            for (int i = 0; n < 3 && i < d.length; i++) {
                boolean dup = false;
                for (int j = 0; j < n; j++) if (nxt[j].equals(d[i])) dup = true;
                if (!dup) nxt[n++] = d[i];
            }
            for (int i = 0; i < 3; i++) {
                String w = shift != OFF ? cap(nxt[i]) : (lay.id.equals("en") && nxt[i].equals("i") ? "I" : nxt[i]);
                shown[i] = w; commit[i] = w;
            }
        } else {
            Lexicon lex = Lexicon.peek(lay.id);
            String low = prefix.toLowerCase(Locale.ROOT);
            int n = lex == null ? 0 : lex.suggest(low, match);
            String pb = learner.best(low);
            if (pb != null && !(n > 0 && pb.equals(match[0])) && !(n > 1 && pb.equals(match[1]))) { // своё частое слово — первым
                match[2] = match[1]; match[1] = match[0]; match[0] = pb; n = Math.min(3, n + 1);
            }
            String corr = null;
            if (acOn && lex != null && lex.n >= 15000 && prefix.length() >= 3 && !learner.known(low)) {
                if (low.equals(lastCorrFor)) corr = lastCorr;
                else if (prefix.length() == 3) { corr = lex.correct(low); lastCorrFor = low; lastCorr = corr; } // короткий префикс считается мгновенно
                else askCorrect(lex, low); // длинные — в фоне: основной поток во время набора не занят перебором словаря
            }
            String quoted = "\u201C" + prefix + "\u201D";
            shown[0] = quoted; commit[0] = prefix;
            if (corr != null) {
                shown[1] = commit[1] = caseLike(corr);
                if (n > 0) shown[2] = commit[2] = caseLike(match[0]);
            } else if (n > 0) {
                shown[1] = commit[1] = caseLike(match[0]);
                if (n > 1) shown[2] = commit[2] = caseLike(match[1]);
            } else {
                shown[0] = null; commit[0] = null;
                shown[1] = quoted; commit[1] = prefix;
                if (lex != null && lex.n >= 15000 && prefix.length() >= 5 && !learner.known(low)) { // слово не продолжается ни во что: может быть, пропущен пробел
                    String sp = lex.split(low);
                    if (sp != null) { shown[2] = commit[2] = caseLike(sp); }
                }
            }
        }
        kv.setSuggestions(shown[0], shown[1], shown[2]);
    }

    /** Подбор исправления в фоновом потоке с задержкой: пока человек печатает, считается только последняя буква. */
    private void askCorrect(final Lexicon lex, final String low) {
        if (corrJob != null) bg().removeCallbacks(corrJob);
        corrJob = new Runnable() {
            @Override public void run() {
                final String r = lex.correct(low);
                h.post(new Runnable() {
                    @Override public void run() {
                        lastCorrFor = low; lastCorr = r;
                        if (low.equals(prefix.toLowerCase(Locale.ROOT))) schedule();
                    }
                });
            }
        };
        bg().postDelayed(corrJob, 70);
    }

    /** Один фоновый поток на подбор исправлений и картинки; создаётся при первой необходимости и гасится вместе с кэшами. */
    private Handler bg() {
        if (corrH == null) {
            corrThread = new HandlerThread("lb-bg", android.os.Process.THREAD_PRIORITY_BACKGROUND);
            corrThread.start();
            corrH = new Handler(corrThread.getLooper());
        }
        return corrH;
    }

    private void stopCorr() {
        soundsLoading = false;
        if (corrThread != null) { corrThread.quitSafely(); corrThread = null; corrH = null; corrJob = null; }
    }

    private static String cap(String w) {
        return w.isEmpty() ? w : w.substring(0, 1).toUpperCase(Locale.ROOT) + w.substring(1);
    }

    private String caseLike(String w) {
        if (prefix.length() > 1 && prefix.equals(prefix.toUpperCase(Locale.ROOT))) return w.toUpperCase(Locale.ROOT);
        if (Character.isUpperCase(prefix.charAt(0))) return cap(w);
        return w;
    }
}

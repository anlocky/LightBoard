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
import android.widget.LinearLayout;
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
    private boolean material;
    private final Theme gtheme = new Theme();
    private FrameLayout root;
    private EmojiView emoji;
    private ClipView clipPanel;
    private boolean clipShown;
    private int clipH, ohApplied = -1;
    private SideBar side;
    private int ohMode;
    private AutofillBar autofill;
    private boolean toolsOn;
    private final ClipStore clipStore = new ClipStore();
    private boolean clipsLoaded;
    private long slowUntil, lockRetryAt;
    private int lockTries, learnTries;
    private boolean learnLocked;

    private ClipStore cs() {
        if (!clipsLoaded) {
            clipsLoaded = true;
            if (clipStore.loadFrom(clipFile())) saveClips();
            String oldClips = state.getString("clips", null);
            if (oldClips != null) { if (clipStore.size() == 0) clipStore.load(oldClips); saveClips(); state.edit().remove("clips").apply(); }
            if (clipStore.locked) lockRetryAt = SystemClock.uptimeMillis() + 1500L;
        } else if (clipStore.locked && SystemClock.uptimeMillis() >= lockRetryAt) {
            // The key store was busy. Copies made meanwhile stay in memory; they are merged as soon as the file opens.
            if (clipStore.mergeFrom(clipFile())) {
                lockTries = 0;
                saveClips();
            } else {
                if (lockTries < 40) lockTries++;
                lockRetryAt = SystemClock.uptimeMillis() + Math.min(60_000L, 1500L * lockTries);
            }
        }
        return clipStore;
    }
    private int oneHand;
    private boolean split;
    private static final float ONE_HAND_FRAC = 0.8f;
    private SharedPreferences cfg, state;
    private final Runnable trimR = new Runnable() { @Override public void run() { trimMemory(); } };
    private SwipeDecoder swipe;
    private Blur blur;
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
    private final StringBuilder wordBuf = new StringBuilder();
    private final Touch tt = new Touch();
    private View navFill;
    private LinearLayout inputRoot;
    private final char[] tmpCh = new char[4];
    private final float[] tmpD2 = new float[4];

    private void clearWord() { wordBuf.setLength(0); tt.clear(); }
    private boolean ru = true, suggestOn, acOn, autoSpace, clipOk, fieldOk = true;
    private Layouts.L lay = Layouts.RU;
    private final Runnable scheduleR = new Runnable() { @Override public void run() { schedule(); } };
    private int shift = OFF;
    private boolean shiftManual;
    private long lastShiftTap, lastSpace;

    private String prefix = "", lastCorrFor, lastCorr;
    private final String[] shown = new String[3], commit = new String[3], match = new String[3];
    private boolean swipeAlt, swipeAwait;
    private int swipeEnd = -1, swipeLen, swipeInsLen;
    private String clipText;

    private char afterPunct;
    private boolean trailAutoSpace;
    private boolean acArmed;
    private String acOrig, acNew, acTrig;
    private KeySounds sounds;
    private boolean soundOn;
    private float soundVol = 0.5f;
    private final Learner learner = new Learner();
    private final String[] nxt = new String[3];
    private boolean ownPruned, calcOn;
    private long ohAnimEnd, lastHeal;
    private SysSpell spell;
    private Runnable corrJob;
    private HandlerThread corrThread; private Handler corrH;
    private boolean noLearn, capsNeed = true, smsShown, pwdField;
    private int npadNow;
    private boolean fieldNoLearn, incoOn, smartDash;
    private Boolean micAvail;
    private String curPkg, appsRaw = "";
    private final java.util.HashMap<String, String[]> appsMap = new java.util.HashMap<>();
    private android.net.Uri imgUri;
    private android.graphics.Bitmap imgThumb;
    private String imgLabel;
    private final Runnable imgHide = new Runnable() { @Override public void run() { clearImage(); schedule(); } };
    private boolean autoCaps = true, autoPeriod = true;
    private static final long SMS_TTL = 300_000L;
    private long lexVer;
    private boolean lexScanned, soundsLoading;

    /** The settings file; its only writer is PrefsProvider in this process, so this instance is always current. */
    private SharedPreferences settingsPrefs() { return new SafePrefs(getSharedPreferences("lb", MODE_PRIVATE)); }

    @Override public void onCreate() {
        super.onCreate();
        state = getSharedPreferences("lbs", MODE_PRIVATE);
        if (state.contains("sms_code")) state.edit().remove("sms_code").remove("sms_time").apply();
        cfg = settingsPrefs();
        Layouts.L sl = Layouts.byLang(state.getString("lay", null));
        if (sl == null) sl = state.contains("ru") ? (state.getBoolean("ru", true) ? Layouts.RU : Layouts.EN) : Layouts.system();
        lay = sl; ru = lay.ru;
        swipe = new SwipeDecoder();
        swipe.setLearner(learner);
        loadLearned();
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

    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public View onCreateInputView() {
        material = designMaterial();
        newBoard();
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                if (blur != null) blur.detach();
                blur = new Blur(this, new Blur.Callback() {
                    @Override public void onBlur(boolean on) { blurNow = on; applyTheme(); }
                });
                blur.attach();
            } catch (RuntimeException e) { Diag.save(this, e); blur = null; }
        }
        root = new FrameLayout(this);
        root.addView(kvView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        emoji = null; emojiShown = false; clipPanel = null; clipShown = false; side = null; ohApplied = -1; ohMode = 0; autofill = null;
        kv.setSplit(split);
        applyOneHand(false);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(root, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        navFill = new View(this);
        navFill.setBackgroundColor(activeTheme().panelBot);
        wrap.addView(navFill, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));
        inputRoot = wrap;
        if (Build.VERSION.SDK_INT >= 35) {
            wrap.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets in) { fitBottom(in); return in; }
            });
            wrap.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) { fitBottom(null); }
            });
        }
        return wrap;
    }

    private void fitBottom(android.view.WindowInsets unused) {
        final LinearLayout r = inputRoot;
        final View fill = navFill;
        if (r == null || fill == null || Build.VERSION.SDK_INT < 35 || r.getHeight() <= 0) return;
        android.view.WindowInsets wi = r.getRootWindowInsets();
        if (wi == null) return;
        android.graphics.Insets nav = wi.getInsets(android.view.WindowInsets.Type.navigationBars());
        android.graphics.Insets tap = wi.getInsets(android.view.WindowInsets.Type.tappableElement());
        int nb = Math.max(nav.bottom, tap.bottom), nl = Math.max(nav.left, tap.left), nr = Math.max(nav.right, tap.right);
        int pb = nb, pl = nl, pr = nr;
        if (nb > 0 || nl > 0 || nr > 0) {
            try {
                int[] loc = new int[2];
                r.getLocationOnScreen(loc);
                android.graphics.Rect screen = ((WindowManager) getSystemService(WINDOW_SERVICE)).getMaximumWindowMetrics().getBounds();
                pb = Math.max(0, Math.min(nb, loc[1] + r.getHeight() - (screen.height() - nb)));
                pl = Math.max(0, Math.min(nl, nl - loc[0]));
                pr = Math.max(0, Math.min(nr, loc[0] + r.getWidth() - (screen.width() - nr)));
            } catch (RuntimeException ignored) { }
        }
        final ViewGroup.LayoutParams lp = fill.getLayoutParams();
        if (lp.height == pb && r.getPaddingLeft() == pl && r.getPaddingRight() == pr) return;
        final int b = pb, l = pl, rr = pr;
        r.post(new Runnable() {
            @Override public void run() {
                r.setPadding(l, 0, rr, 0);
                lp.height = b;
                fill.setLayoutParams(lp);
            }
        });
    }

    private String[] appEntry() {
        String raw = cfg.getString("apps", "");
        if (raw.isEmpty() || curPkg == null) return null;
        if (!raw.equals(appsRaw)) {
            appsRaw = raw; appsMap.clear();
            for (String e : raw.split(";")) { String[] f = e.split(":"); if (f.length == 4 || f.length == 8) appsMap.put(f[0], f); }
        }
        return appsMap.get(curPkg);
    }

    private String ovr(int part) {
        String[] f = appEntry();
        return f == null ? "-" : f[part + 1];
    }

    private int colorOf(int idx) {
        String[] f = appEntry();
        if (f != null && f.length == 8) {
            try { return Integer.parseInt(f[4 + idx]); } catch (NumberFormatException ignored) { }
        }
        return cfg.getInt(COLOR_KEYS[idx], 0);
    }

    private static final String[] COLOR_KEYS = {"i_panelHue", "i_panelInt", "i_keyHue", "i_keyInt"};

    private String schemeNow() { String o = ovr(2); return o.equals("-") ? cfg.getString("g_material", "auto") : o; }

    private boolean micAvailable() {
        if (micAvail == null) micAvail = SpeechRecognizer.isRecognitionAvailable(this);
        return micAvail;
    }

    private boolean slow() { return SystemClock.uptimeMillis() < slowUntil; }

    /** getTextBeforeCursor with a watchdog: when the target app answers slowly, optional lookups pause for a few seconds. */
    private CharSequence tb(InputConnection ic, int n) {
        if (slow()) return null;
        long t0 = SystemClock.uptimeMillis();
        CharSequence r;
        try { r = ic.getTextBeforeCursor(n, 0); }
        catch (RuntimeException e) { slowUntil = t0 + 3000L; return null; }
        long t1 = SystemClock.uptimeMillis();
        if (t1 - t0 > 120L) slowUntil = t1 + 3000L;
        return r;
    }

    private CharSequence after(InputConnection ic, int n) {
        if (slow()) return null;
        long t0 = SystemClock.uptimeMillis();
        CharSequence r;
        try { r = ic.getTextAfterCursor(n, 0); }
        catch (RuntimeException e) { slowUntil = t0 + 3000L; return null; }
        long t1 = SystemClock.uptimeMillis();
        if (t1 - t0 > 120L) slowUntil = t1 + 3000L;
        return r;
    }

    private CharSequence selected(InputConnection ic) {
        if (slow()) return null;
        long t0 = SystemClock.uptimeMillis();
        CharSequence r;
        try { r = ic.getSelectedText(0); }
        catch (RuntimeException e) { slowUntil = t0 + 3000L; return null; }
        long t1 = SystemClock.uptimeMillis();
        if (t1 - t0 > 120L) slowUntil = t1 + 3000L;
        return r;
    }

    private void updNoLearn() { noLearn = fieldNoLearn || incoOn; }

    @Override public void onIncognito() {
        if (!cfg.getBoolean("incognito", true)) return;
        incoOn = !incoOn;
        updNoLearn();
        if (kv != null) kv.setIncognito(true, incoOn);
        android.widget.Toast.makeText(this, KbText.t(incoOn ? "Инкогнито включён" : "Инкогнито выключен"), android.widget.Toast.LENGTH_SHORT).show();
        schedule();
    }

    private boolean designMaterial() {
        String o = ovr(0);
        if (o.equals("material")) return true;
        if (Ui.cupertinoId(o)) return false;
        try { return !Ui.cupertinoId(cfg.getString("design", "material")); } catch (ClassCastException e) { return true; }
    }

    private Theme activeTheme() { return material ? gtheme : theme; }

    private String P() { return material ? "g_" : "i_"; }

    private void newBoard() {
        kv = material ? new MaterialView(this, gtheme) : new KeyboardView(this, theme);
        kvView = kv.view();
        kv.setListener(this);
        kv.setSwipe(swipe);
        kv.setLayout(lay);
        kv.setBottomBar(bottomBar);
    }

    private void swapBoard(boolean toMaterial) {
        if (root == null) return;
        if (emoji != null) { root.removeView(emoji); emoji = null; }
        if (clipPanel != null) { root.removeView(clipPanel); clipPanel = null; }
        emojiShown = false; clipShown = false;
        root.removeView(kvView);
        material = toMaterial;
        newBoard();
        root.addView(kvView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        kv.setSplit(split);
        ohApplied = -1; applyOneHand(false);
        theme.set(!darkNow, false, -1);
    }

    @Override public void onWindowShown() {
        super.onWindowShown();
        h.removeCallbacks(trimR);
        lastChip = null;
        hideUndo();
        if (kv != null) { kv.playEntrance(); kv.setToolsOpen(cfg.getBoolean(P() + "toolsauto", true)); }
    }

    @Override public void onWindowHidden() {
        super.onWindowHidden();
        if (listening) stopListening();
        saveLearned();
        flushClips();
        h.removeCallbacks(trimR);
        h.postDelayed(trimR, 8000);
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND && !isInputViewShown()) trimMemory();
    }

    private void trimMemory() {
        if (isInputViewShown()) return;
        if (spell != null) { spell.close(); spell = null; }
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
        if (material) { gtheme.setMaterial(this, darkNow, schemeNow()); changed = true; }
        else changed = theme.set(darkNow, blurNow, cfg.getInt("i_panelPct", 80), colorOf(0), colorOf(1), colorOf(2), colorOf(3));
        if (changed) {
            kv.themeChanged();
            if (emoji != null) emoji.themeChanged();
            if (clipPanel != null) clipPanel.themeChanged();
            if (side != null) { side.setTheme(activeTheme()); side.themeChanged(); }
            applyNavBar(darkNow);
        }
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        try { startInputViewImpl(info); } catch (RuntimeException e) { Diag.save(this, e); }
    }

    private void startInputViewImpl(EditorInfo info) {
        curPkg = info.packageName;
        ei = info;
        KbText.set(cfg.getString("app_lang", "auto"));
        boolean wantG = designMaterial();
        if (kv != null && wantG != material) swapBoard(wantG);
        long rs = cfg.getLong("reset_words", 0L);
        if (rs > state.getLong("reset_done", 0L)) {
            learner.clear();
            Vault.barrier(learnFile(), Vault.nextSeq());
            learnFile().delete();
            state.edit().remove("learned").remove("pairs").remove("user_words").remove("own").putLong("reset_done", rs).apply();
        }
        long fs = cfg.getLong("forget_stamp", 0L);
        if (fs > state.getLong("forget_done", 0L)) {
            for (String fw : cfg.getString("forget", "").split(" ")) if (!fw.isEmpty()) learner.forget(fw);
            state.edit().putLong("forget_done", fs).apply();
            cfg.edit().remove("forget").apply();
        }
        if (learnLocked) retryLearn();
        applyImport();
        long lv = cfg.getLong("lex_ver", 0L);
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
        if (blur != null) blur.setWanted(!material && cfg.getBoolean("i_blur", true), cfg.getInt("i_blurDp", 20));
        applyTheme();
        applyNavBar(darkNow);

        boolean wantBar = cfg.getBoolean(P() + "bottombar", false);
        if (wantBar != bottomBar) {
            bottomBar = wantBar;
            kv.setBottomBar(bottomBar);
            if (emoji != null) { root.removeView(emoji); emoji = null; emojiShown = false; kvView.setVisibility(View.VISIBLE); kvView.setAlpha(1f); }
        }
        boolean langKeyOn = cfg.getBoolean(P() + "langkey", false);
        kv.setLangKey(langKeyOn);
        kv.setGlobeMode(!langKeyOn && cfg.getBoolean(P() + "globe", false));
        if (emojiShown) hideEmoji(false);
        if (clipShown) hideClips(false);
        settleBoard();
        syncModes();
        kv.setHaptic(cfg.getBoolean(P() + "haptic", true));
        soundOn = cfg.getBoolean(P() + "sound", false);
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
        boolean isText = cls == InputType.TYPE_CLASS_TEXT;
        boolean password = (isText && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
                || (cls == InputType.TYPE_CLASS_NUMBER && var == InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        addrField = isText && (var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS || var == InputType.TYPE_TEXT_VARIATION_URI);

        fieldOk = !password && !numeric;
        pwdField = password;
        if (password && listening) stopListening();
        kv.setMic(cfg.getBoolean(P() + "mic", true) && micAvailable() && !password);
        fieldNoLearn = (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0 || addrField
                || password || numeric
                || (isText && (info.inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0)
                || (isText && var == InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        boolean incoAllowed = cfg.getBoolean("incognito", true);
        if (!incoAllowed) incoOn = false;
        updNoLearn();
        kv.setIncognito(incoAllowed, incoOn);
        smartDash = cfg.getBoolean("smartdash", false);
        capsNeed = true;
        applyFlags();
        clipOk = !password && cfg.getBoolean(P() + "clip", true);
        showDigits = cfg.getBoolean(P() + "digits", false);
        showWords = cfg.getBoolean(P() + "words", true);
        clearWord();
        checkSms();
        findShot();

        kv.setLayout(lay);
        int npad = 0;
        if (cls == InputType.TYPE_CLASS_PHONE) npad = 2;
        else if (cls == InputType.TYPE_CLASS_DATETIME) npad = 3;
        else if (cls == InputType.TYPE_CLASS_NUMBER) npad = (info.inputType & (InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED)) == 0 ? 1 : 4;
        kv.setNumpad(npad);
        kv.setTablet(getResources().getConfiguration().smallestScreenWidthDp >= 600);
        kv.setRound(cfg.getInt(P() + "round", Ui.defRound(this, !material)));
        kv.setNumRow(showDigits && !numeric);
        npadNow = npad;
        applyStrip();
        kv.setKeyBg(cfg.getBoolean(P() + "keybg", true));
        kv.setSwapIcons(cfg.getBoolean(P() + "swapicons", false));
        kv.setPopup(cfg.getBoolean(P() + "popup", true) && !password);
        kv.setHints(cfg.getBoolean(P() + "hintnum", false), cfg.getBoolean(P() + "hintsym", false));
        kv.setLine(cfg.getBoolean(P() + "trail", true));
        kv.setLogo(cfg.getBoolean("g_logo", false));
        String mp = cfg.getString("g_micpos", "top");
        kv.setMicPos(mp.equals("bottom") ? 1 : mp.equals("both") ? 2 : 0);
        kv.setMenuSwap(cfg.getBoolean(P() + "menuswap", false));
        MenuBar.setOrder(cfg.getString("menu_order", MenuOrder.DEFAULT));
        autoCaps = cfg.getBoolean(P() + "autocaps", true);
        autoPeriod = cfg.getBoolean(P() + "autoperiod", true);
        calcOn = cfg.getBoolean("calc", true) && !password;
        kv.setPage(numeric ? 1 : 0);
        if (lay.own()) Lexicon.preload(this, lay.id, scheduleR); else Lexicon.invalidate();
        if (clipText != null && !chipOk()) clipText = null;
        swipeAlt = false; afterPunct = 0; trailAutoSpace = false; acArmed = false;
        kv.setEnterLabel(enterLabel());
        shift = OFF; shiftManual = false; kv.setShift(OFF);
        schedule();
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        h.removeCallbacks(refreshR);
        hideUndo();
        if (autofill != null) autofill.clear();
        if (kv != null) kv.cancelTouches();
    }

    @Override public void onUpdateSelection(int oldSs, int oldSe, int newSs, int newSe, int cs, int ce) {
        super.onUpdateSelection(oldSs, oldSe, newSs, newSe, cs, ce);
        if (newSs != newSe || Math.abs(newSs - oldSs) != 1) capsNeed = true;
        if (undoText != null) {
            if (undoArm) { undoArm = false; undoCur = newSe; }
            else if ((newSs != undoCur || newSe != undoCur) && android.os.SystemClock.uptimeMillis() - undoGuard > 450L) hideUndo();
        }
        if (swipeAlt) {
            if (swipeAwait) { swipeEnd = newSs; swipeAwait = false; }
            else if (newSs != swipeEnd || newSe != swipeEnd) swipeAlt = false;
        }
        schedule();
    }

    @Override public void onComputeInsets(InputMethodService.Insets out) {
        super.onComputeInsets(out);
        if (kv != null) {
            fitBottom(null);
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
        saveLearnedNow();
        if (spell != null) { spell.close(); spell = null; }
        if (blur != null) blur.detach();
        if (cm != null) cm.removePrimaryClipChangedListener(clipListener);
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void applyNavBar(boolean dark) {
        Window w = getWindow().getWindow();
        if (w == null) return;
        w.setNavigationBarColor(activeTheme().panelBot);
        if (navFill != null) navFill.setBackgroundColor(activeTheme().panelBot);
        if (Build.VERSION.SDK_INT >= 28) w.setNavigationBarDividerColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController wc = w.getInsetsController();
            if (wc != null) wc.setSystemBarsAppearance(dark ? 0 : android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS, android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else if (Build.VERSION.SDK_INT >= 26) legacyNavIcons(w.getDecorView(), dark);
    }

    @SuppressWarnings("deprecation")
    private static void legacyNavIcons(View dv, boolean dark) {
        int ui = dv.getSystemUiVisibility();
        dv.setSystemUiVisibility(dark ? (ui & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) : (ui | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
    }

    private void readClip() {
        if (cm == null) return;
        boolean chip = cfg.getBoolean(P() + "clip", true), hist = cfg.getBoolean("clip_hist", true);
        if (!chip && !hist) return;
        try {
            ClipData d = cm.getPrimaryClip();
            if (d == null || d.getItemCount() == 0) return;
            if (d.getDescription() != null && d.getDescription().getExtras() != null
                    && d.getDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE")) return;
            final android.net.Uri iu = d.getItemAt(0).getUri();
            if (iu != null && d.getDescription().hasMimeType("image/*")) {
                if (imgAllowed()) bg().post(new Runnable() { @Override public void run() { showImage(iu, KbText.t("Изображение")); } });
                return;
            }
            CharSequence t = d.getItemAt(0).getText();
            if (t == null) return;
            String s = t.toString().trim();
            if (s.isEmpty()) return;
            ClipStore store = cs();
            if (hist && !incoOn && !looksSecret(s) && store.add(s, nowMin())) { store.expire(nowMin(), clipTtl()); saveClips(); }
            if (!chip || s.length() > 200_000) return;
            smsShown = false;
            clipText = s;
            h.removeCallbacks(clipHide);
            h.postDelayed(clipHide, CLIP_TTL);
            schedule();
        } catch (RuntimeException ignored) { }
    }

    private static final java.util.regex.Pattern JWT = java.util.regex.Pattern.compile("[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]*");

    private static boolean looksSecret(String s) {
        int n = s.length();
        if (n >= 20 && (s.contains("PRIVATE KEY") || s.contains("-----BEGIN"))) return true;
        if (n >= 30 && n <= 4000 && JWT.matcher(s).matches()) return true;
        if (n >= 35 && n <= 300 && seedPhrase(s)) return true;
        if (n < 4 || n > 64) return false;
        boolean lo = false, up = false, dg = false, sy = false;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == '@' || c == '/') return false;
            if (Character.isDigit(c)) dg = true; else if (Character.isLowerCase(c)) lo = true; else if (Character.isUpperCase(c)) up = true; else sy = true;
        }
        if (dg && !lo && !up && !sy) return n <= 8;
        return n >= 6 && ((lo ? 1 : 0) + (up ? 1 : 0) + (dg ? 1 : 0) + (sy ? 1 : 0)) >= 3;
    }

    /** 12/15/18/21/24 short lowercase Latin words separated by single spaces: a wallet recovery phrase. */
    private static boolean seedPhrase(String s) {
        String[] w = s.split(" ", -1);
        if (w.length < 12 || w.length > 24 || w.length % 3 != 0) return false;
        for (String x : w) {
            if (x.length() < 3 || x.length() > 8) return false;
            for (int i = 0; i < x.length(); i++) { char c = x.charAt(i); if (c < 'a' || c > 'z') return false; }
        }
        return true;
    }

    private static long nowMin() { return System.currentTimeMillis() / 60000L; }

    private int clipTtl() { return Math.max(1, cfg.getInt("clip_days", ClipStore.DEFAULT_DAYS)) * 1440; }

    private boolean imgAllowed() {
        return cfg.getBoolean("shots", false) && !pwdField && Pics.accepts(ei)
                && checkSelfPermission(Pics.imagesPermission()) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void findShot() {
        if (imgUri != null || !imgAllowed()) return;
        bg().post(new Runnable() { @Override public void run() {
            android.net.Uri u = Pics.latestScreenshot(LightBoardIme.this);
            if (u != null) showImage(u, KbText.t("Скриншот"));
        } });
    }

    private void showImage(final android.net.Uri u, final String label) {
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

    private volatile boolean clipsDirty;
    private static final long UNDO_MS = 10000L;
    private String undoText;
    private boolean undoInserted, undoRedo, undoArm;
    private int undoCur;
    private long undoGuard, lastDelAt;
    private int delRun;
    private final StringBuilder delBuf = new StringBuilder();
    private final Runnable undoHide = new Runnable() { @Override public void run() { hideUndo(); } };

    private void showUndo(String text, boolean inserted) {
        if (kv == null || text == null || text.isEmpty() || pwdField || incoOn || !cfg.getBoolean("undobtn", true)) return;
        undoText = text; undoInserted = inserted; undoRedo = false; undoArm = true; undoCur = -1;
        undoGuard = android.os.SystemClock.uptimeMillis();
        publishUndo();
        h.removeCallbacks(undoHide);
        h.postDelayed(undoHide, UNDO_MS);
    }

    private void publishUndo() {
        if (kv == null) return;
        if (toolsOn) kv.setToolsOpen(false);
        kv.setUndo(KbText.t(undoRedo ? "Вернуть" : "Отменить"), undoRedo);
    }

    private void hideUndo() {
        h.removeCallbacks(undoHide);
        delBuf.setLength(0);
        delRun = 0;
        if (undoText == null) return;
        undoText = null;
        if (kv != null) kv.setUndo(null, false);
    }

    private void noteDeleted(String s) {
        if (pwdField || incoOn) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastDelAt > 130L) { delBuf.setLength(0); delRun = 0; }
        lastDelAt = now;
        delBuf.insert(0, s);
        delRun++;
        if (delRun >= 4) showUndo(delBuf.toString(), false);
    }

    @Override public void onUndo() {
        InputConnection ic = getCurrentInputConnection();
        String t = undoText;
        if (ic == null || t == null) { hideUndo(); return; }
        boolean ok = true;
        ic.beginBatchEdit();
        if (undoInserted) {
            CharSequence b = tb(ic, t.length());
            if (b != null && t.contentEquals(b)) ic.deleteSurroundingText(t.length(), 0); else ok = false;
        } else ic.commitText(t, 1);
        ic.endBatchEdit();
        if (!ok) { hideUndo(); return; }
        resetFlags();
        undoInserted = !undoInserted; undoRedo = !undoRedo; undoArm = true; undoCur = -1;
        undoGuard = android.os.SystemClock.uptimeMillis();
        publishUndo();
        h.removeCallbacks(undoHide);
        h.postDelayed(undoHide, UNDO_MS);
        schedule();
    }
    private String lastChip;

    private void setChip(String chip) {
        if (kv == null) return;
        if (chip != null && !chip.equals(lastChip) && toolsOn) kv.setToolsOpen(false);
        lastChip = chip;
        kv.setClip(chip);
    }
    private final Runnable saveClipsR = new Runnable() { @Override public void run() { flushClips(); } };

    private void saveClips() { if (clipStore.locked) return; clipsDirty = true; h.removeCallbacks(saveClipsR); h.postDelayed(saveClipsR, 1000); }

    private void wipeClips() {
        clipStore.clearAll();
        clipStore.locked = false;
        clipsLoaded = true;
        clipsDirty = false;
        h.removeCallbacks(saveClipsR);
        File f = clipFile();
        Vault.barrier(f, Vault.nextSeq());
        f.delete();
        new File(f.getPath() + ".tmp").delete();
    }

    private void flushClips() {
        h.removeCallbacks(saveClipsR);
        if (!clipsDirty || clipStore.locked) return;
        clipsDirty = false;
        try {
            final byte[] snap = clipStore.snapshot();
            final File f = clipFile();
            final long q = Vault.nextSeq();
            bg().post(new Runnable() { @Override public void run() { try { ClipStore.write(f, snap, q); } catch (IOException ignored) { clipsDirty = true; } } });
        } catch (IOException ignored) { }
    }

    private void flushClipsNow() {
        h.removeCallbacks(saveClipsR);
        if (!clipsDirty || clipStore.locked) return;
        clipsDirty = false;
        try { ClipStore.write(clipFile(), clipStore.snapshot(), Vault.nextSeq()); } catch (IOException ignored) { }
    }

    private boolean chipOk() { return clipOk || (smsShown && !pwdField); }

    private static String preview(String s) { return s.length() > 160 ? s.substring(0, 160) : s; }

    private void applyStrip() { if (kv != null) kv.setShowWords(showWords && (npadNow == 0 || (smsShown && !pwdField))); }

    private void smsShow(String code, long ttl) {
        if (!cfg.getBoolean("sms", false)) return;
        clipText = code; smsShown = true;
        h.removeCallbacks(clipHide);
        h.postDelayed(clipHide, ttl);
    }

    private void checkSms() {
        String code = SmsReceiver.fresh(SMS_TTL);
        if (code != null && !smsShown) smsShow(code, SMS_TTL - SmsReceiver.age());
    }

    private void clearSmsStore() { SmsReceiver.clear(); }

    @Override public void onClipTap(boolean dismiss) {
        if (imgUri != null) {
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
            if (ic != null) { resetFlags(); ic.commitText(t, 1); showUndo(t, true); }
        }
        schedule();
    }

    private void resetFlags() { capsNeed = true; swipeAlt = false; acArmed = false; afterPunct = 0; trailAutoSpace = false; clearWord(); }

    @Override public void onChar(String s) { try { onCharImpl(s); } catch (RuntimeException e) { Diag.save(this, e); } }

    private void onCharImpl(String s) {
        lastFin = null;
        if (toolsOn && kv != null) kv.setToolsOpen(false);
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        boolean letters = kv.getPage() == 0;
        String out = (shift != OFF && letters) ? lay.upperKeep(s) : s;
        if (smartDash && out.length() == 1 && out.charAt(0) == '-') {
            CharSequence pb = tb(ic, 1);
            if (pb == null || pb.length() == 0 || pb.charAt(0) == ' ' || pb.charAt(0) == '\n') out = "\u2014";
        }
        char c0 = out.charAt(0);
        swipeAlt = false;
        boolean isPunct = out.length() == 1 && PUNCT.indexOf(c0) >= 0;

        if (isPunct) {
            boolean corrected = !addrField && tryAutocorrect(ic, out);
            if (trailAutoSpace) {
                CharSequence b = tb(ic, 1);
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
        if (calcOn && out.equals("=")) {
            CharSequence cb = tb(ic, 81);
            String res = cb == null || cb.length() < 2 ? null : Calc.solve(cb.subSequence(0, cb.length() - 1));
            if (res != null) ic.commitText(res, 1);
        }
        if (!pwdField && !isPunct && out.length() == 1 && Character.isLetter(out.charAt(0))) {
            wordBuf.append(out);
            tt.push(Character.toLowerCase(out.charAt(0)), tmpCh, tmpD2, kv.lastTouch(tmpCh, tmpD2));
        } else clearWord();
        if (shift == ON && letters) { shift = OFF; kv.setShift(OFF); }
        shiftManual = false;
        if (out.length() != 1 || !Character.isLetterOrDigit(out.charAt(0))) capsNeed = true;
        schedule();
    }

    private String autoSpaceBefore(InputConnection ic, String out) {
        char p = afterPunct;
        afterPunct = 0;
        CharSequence b = tb(ic, 3);
        if (b == null || b.length() < 2 || b.charAt(b.length() - 1) != p) return out;
        char before = b.charAt(b.length() - 2);
        boolean doIt;
        if (p == ',') {
            doIt = !Character.isDigit(before) && before != ' ';
        } else {

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
        CharSequence b = tb(ic, 34);
        if (b == null) return false;
        int e = b.length() - 1, s = e;
        while (s > 0 && Learner.wordChar(b.charAt(s - 1))) s--;
        if (e - s < 2) return false;
        String w = b.subSequence(s, e).toString().toLowerCase(Locale.ROOT);
        return lex.contains(w) || learner.isUser(w);
    }

    @Override public void onBack() { try { onBackImpl(); } catch (RuntimeException e) { Diag.save(this, e); } }

    private void onBackImpl() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        capsNeed = true;
        if (lastFin != null) {
            if (SystemClock.uptimeMillis() - lastFinAt > 8000L) lastFin = null;
            else if (++delSince >= lastFin.length() + 1) { learner.demote(lastFin); lastFin = null; }
        }
        if (acArmed && acNew != null) {
            acArmed = false;
            String was = acNew + acTrig;
            CharSequence tail = tb(ic, was.length());
            if (tail != null && was.contentEquals(tail)) {
                ic.deleteSurroundingText(was.length(), 0);
                ic.commitText(acOrig + acTrig, 1);
                learn(acOrig.toLowerCase(Locale.ROOT));
                schedule();
                return;
            }
        }
        if (swipeAlt && swipeInsLen > 0) {
            CharSequence sw = tb(ic, swipeInsLen);
            ic.deleteSurroundingText(swipeInsLen, 0);
            if (sw != null && sw.length() == swipeInsLen) showUndo(sw.toString(), false);
            swipeAlt = false; swipeInsLen = 0; afterPunct = 0; trailAutoSpace = false;
            schedule();
            return;
        }
        swipeAlt = false; afterPunct = 0; trailAutoSpace = false;
        if (wordBuf.length() > 0) { wordBuf.setLength(wordBuf.length() - 1); tt.pop(); }
        CharSequence sel = selected(ic);
        if (sel != null && sel.length() > 0) {
            String removed = sel.toString();
            ic.commitText("", 1);
            showUndo(removed, false);
        } else {
            CharSequence b = tb(ic, 2);
            if (b == null) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); }
            else {
                int n = (b.length() == 2 && Character.isHighSurrogate(b.charAt(0)) && Character.isLowSurrogate(b.charAt(1))) ? 2 : 1;
                ic.deleteSurroundingText(n, 0);
                if (b.length() >= n) noteDeleted(b.subSequence(b.length() - n, b.length()).toString());
            }
        }
        schedule();
    }

    @Override public void onShift() {
        long now = SystemClock.uptimeMillis();
        if (shift == LOCK) shift = OFF;
        else if (shift == ON && now - lastShiftTap < 350) shift = LOCK;
        else if (shift == ON) shift = OFF;
        else shift = ON;
        shiftManual = true;
        lastShiftTap = now;
        kv.setShift(shift);
    }

    @Override public void onSpace() { try { onSpaceImpl(); } catch (RuntimeException e) { Diag.save(this, e); } }

    private void onSpaceImpl() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        capsNeed = true;
        long now = SystemClock.uptimeMillis();
        boolean dbl = now - lastSpace < 400;
        lastSpace = now;
        swipeAlt = false; afterPunct = 0; trailAutoSpace = false;
        if (dbl && autoPeriod) {
            CharSequence b = tb(ic, 2);
            if (b != null && b.length() == 2 && b.charAt(1) == ' ' && Character.isLetterOrDigit(b.charAt(0))) {
                ic.deleteSurroundingText(1, 0);
                ic.commitText(". ", 1);
                acArmed = false; shiftManual = false;
                schedule();
                return;
            }
        }
        boolean corrected = tryAutocorrect(ic, " ");
        clearWord();
        ic.commitText(" ", 1);
        acArmed = corrected;
        if (corrected) acTrig = " ";
        shiftManual = false;
        schedule();
    }

    @Override public void onEnter() { try { onEnterImpl(); } catch (RuntimeException e) { Diag.save(this, e); } }

    private void onEnterImpl() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        capsNeed = true;
        swipeAlt = false; afterPunct = 0; trailAutoSpace = false; acArmed = false;
        if (ei != null) {
            int a = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            boolean noAction = (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
            if (!noAction && a != EditorInfo.IME_ACTION_NONE && a != EditorInfo.IME_ACTION_UNSPECIFIED) {
                tryAutocorrect(ic, "", false);
                clearWord();
                ic.performEditorAction(a);
                return;
            }
        }
        tryAutocorrect(ic, "\n", false);
        clearWord();
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        acArmed = false;
    }

    private void learn(String low) { if (!noLearn) learner.learnUser(low); }

    private void finish(String fin, CharSequence b, int s) {
        if (noLearn) return;
        int k = kindOf(fin);
        learner.bump(fin, k);
        lastFin = fin; lastFinAt = SystemClock.uptimeMillis(); delSince = 0;
        if (b == null || !pairable(k, fin)) return;
        String pv = Learner.prevWord(b, s, 32);
        if (pv != null && (pv.isEmpty() || pairable(-1, pv))) learner.pair(pv, fin);
    }

    private boolean tryAutocorrect(InputConnection ic, String trigger) { return tryAutocorrect(ic, trigger, true); }

    private boolean tryAutocorrect(InputConnection ic, String trigger, boolean replace) {
        if (!suggestOn || slow()) return false;
        Lexicon lex = Lexicon.peek(lay.id);
        boolean canCorrect = acOn && lex != null && lex.n >= 15000;
        CharSequence b = tb(ic, 32);
        String w;
        boolean ctx = b != null && b.length() > 0;
        int s = 0;
        if (ctx) {
            int e = b.length();
            s = e;
            while (s > 0 && Learner.wordChar(b.charAt(s - 1))) s--;
            if (s == e) return false;
            if (e - s < 3) { finish(b.subSequence(s, e).toString().toLowerCase(Locale.ROOT), b, s); return false; }
            if (s > 0) {
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
        if (cap) {
            if (!ctx) return false;
            int k = s - 1;
            while (k >= 0 && b.charAt(k) == ' ') k--;
            boolean start = (k < 0 && b.length() < 32) || (k >= 0 && (SENTENCE_END.indexOf(b.charAt(k)) >= 0 || b.charAt(k) == '\n'));
            if (!start) { finish(w.toLowerCase(Locale.ROOT), b, s); return false; }
        }
        String low = w.toLowerCase(Locale.ROOT);
        if (learner.known(low) || !canCorrect || !replace) { finish(low, ctx ? b : null, s); return false; }
        String corr = lex.autoCorrect(low, 8_000_000L, tt);
        if (corr == null) { finish(low, ctx ? b : null, s); return false; }
        String out = cap ? cap(corr) : corr;
        if (!ic.deleteSurroundingText(w.length(), 0)) return false;
        ic.commitText(out, 1);
        acOrig = w; acNew = out;
        finish(corr, ctx ? b : null, s);
        return true;
    }

    @Override public void onSwipe(String[] words, int n) { try { onSwipeImpl(words, n); } catch (RuntimeException e) { Diag.save(this, e); } }

    private void onSwipeImpl(String[] words, int n) {
        if (toolsOn && kv != null) kv.setToolsOpen(false);
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || n <= 0) return;
        String w = words[0];
        if (shift == LOCK) w = w.toUpperCase(Locale.ROOT);
        else if (shift == ON) w = cap(w);
        CharSequence b = tb(ic, 32);
        char lc = b != null && b.length() > 0 ? b.charAt(b.length() - 1) : ' ';
        boolean sp = Character.isLetterOrDigit(lc) || PUNCT.indexOf(lc) >= 0;
        ic.commitText((sp ? " " : "") + w + " ", 1);
        afterPunct = 0; acArmed = false; trailAutoSpace = true; clearWord(); capsNeed = true;
        swipeLen = w.length() + 1;
        swipeInsLen = (sp ? 1 : 0) + w.length() + 1;
        observe(b, w.toLowerCase(Locale.ROOT));

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

    @Override public void onLang() {
        ArrayList<InputMethodSubtype> subs = new ArrayList<>();
        ArrayList<Layouts.L> el = SysLangs.enabled(this, subs);
        if (el.isEmpty()) return;
        int j = (el.indexOf(lay) + 1) % el.size();
        switchTo(el.get(j));
        if (Build.VERSION.SDK_INT >= 28 && j < subs.size()) {
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
        if (l.id.equals(lay.id)) { if (l != lay) { lay = l; kv.setLayout(l); } return; }
        lay = l; ru = l.ru;
        state.edit().putString("lay", l.id).apply();
        kv.setLayout(l); lastCorrFor = null; clearWord();
        applyFlags();
        if (l.own()) Lexicon.preload(this, lay.id, scheduleR); else Lexicon.invalidate();
        schedule();
    }

    @Override public void onEmojiKey(boolean longPress) {
        boolean globe = cfg.getBoolean(P() + "globe", false);
        if (longPress == globe) showEmoji(); else onLang();
    }

    private SpeechRecognizer sr;
    private boolean listening;
    private final Runnable startListenR = new Runnable() { @Override public void run() { if (!pwdField) startListening(); } };

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

    private void startListening() {
        startListening(Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this) ? 0 : 1);
    }

    private void startListening(final int mode) {
        if (kv == null) return;
        if (sr != null) stopListening();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { openMicPermission(); return; }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, KbText.t("Распознавание речи недоступно"), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            sr = mode == 0 ? SpeechRecognizer.createOnDeviceSpeechRecognizer(this) : SpeechRecognizer.createSpeechRecognizer(this);
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
                    if ((code == 12 || code == 13) && mode < 2) { startListening(mode + 1); return; }
                    if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) openMicPermission();
                    else if (code == 12 || code == 13) Toast.makeText(LightBoardIme.this, KbText.t("Распознаватель речи не поддерживает этот язык"), Toast.LENGTH_SHORT).show();
                    else if (code != SpeechRecognizer.ERROR_NO_MATCH && code != SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                        Toast.makeText(LightBoardIme.this, String.format(KbText.t("Ошибка распознавания: %d"), code), Toast.LENGTH_SHORT).show();
                }
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            if (mode < 2) i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lay.tag);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            sr.startListening(i);
            if (toolsOn) kv.setToolsOpen(false);
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
        CharSequence b = tb(ic, 1);
        boolean sp = b != null && b.length() == 1 && (Character.isLetterOrDigit(b.charAt(0)) || PUNCT.indexOf(b.charAt(0)) >= 0);
        if (autoCaps && ei != null && !slow() && ic.getCursorCapsMode(ei.inputType) != 0) v = cap(v);
        resetFlags();
        ic.commitText((sp ? " " : "") + v, 1);
        schedule();
    }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        if (attribute != null) ei = attribute;
        if (VoiceActivity.takeGranted()) { h.removeCallbacks(startListenR); h.postDelayed(startListenR, 500); }
    }

    private void adoptSubtype(InputMethodSubtype st) {
        if (st == null || st.getLocale() == null) return;
        String loc = st.getLocale();
        if (loc.equals(state.getString("sys_subtype", ""))) return;
        state.edit().putString("sys_subtype", loc).apply();
        switchTo(Layouts.byLang(loc));
    }

    @Override public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype st) {
        super.onCurrentInputMethodSubtypeChanged(st);
        adoptSubtype(st);
    }

    @Override public void onSpecial(int keyCode) {
        resetFlags();
        sendDownUpKeyEvents(keyCode);
        schedule();
    }

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
        CharSequence all = selected(ic);
        String gone = all != null && all.length() > 0 ? all.toString() : null;
        ic.commitText("", 1);
        CharSequence b = tb(ic, 1), a = after(ic, 1);
        if (slow() || (b != null && b.length() > 0) || (a != null && a.length() > 0)) {
            if (gone == null && !slow()) {
                CharSequence pb = tb(ic, 5000), pa = after(ic, 5000);
                gone = (pb == null ? "" : pb.toString()) + (pa == null ? "" : pa.toString());
            }
            ic.deleteSurroundingText(1000000, 1000000);
        }
        ic.endBatchEdit();
        if (gone != null && !gone.isEmpty()) showUndo(gone, false);
        schedule();
    }

    @Override public void onOpenSettings() {
        try {
            Intent i = new Intent(this, SettingsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (RuntimeException e) { Diag.save(this, e); }
    }

    @Override public void onBroken() {
        long now = SystemClock.uptimeMillis();
        if (now - lastHeal < 3000) return;
        lastHeal = now;
        h.post(new Runnable() { @Override public void run() { swapBoard(material); refresh(); } });
    }

    private void settleBoard() {
        if (kvView == null) return;
        if (!kv.healthy() && kvView.getWidth() > 0) { swapBoard(material); return; }
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
                if (on && toolsOn) kv.setToolsOpen(false);
                kv.setStripHidden(on && !toolsOn);
            }
        });
        autofill.show(response.getInlineSuggestions(), kv.strip(), (FrameLayout.LayoutParams) kvView.getLayoutParams());
        return true;
    }

    @Override public void onKeySound(int kind) {
        if (!soundOn) return;
        KeySounds s = sounds;
        if (s == null) { warmSounds(); return; }
        s.play(kind, soundVol);
    }

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

    @Override public void onMenu(int id) {
        switch (id) {
            case Board.MENU_CLIP: showClips(); break;
            case Board.MENU_ONEHAND: setOneHandMode(oneHand == 0 ? state.getInt("onehand_side", 2) : 0); break;
            case Board.MENU_SPLIT: setSplitMode(!split); break;
            default: onOpenSettings(); break;
        }
    }

    private void setSplitMode(boolean on) { setSplitMode(on, true); }

    private void setSplitMode(boolean on, boolean byUser) {
        split = on;
        SharedPreferences.Editor ed = state.edit().putBoolean("split", on);
        if (byUser) ed.putLong("mode_t", System.currentTimeMillis());
        ed.apply();
        if (kv != null) kv.setSplit(on);
    }

    private void setOneHandMode(int m) { setOneHandMode(m, true); }

    private void setOneHandMode(int m, boolean byUser) {
        oneHand = m;
        SharedPreferences.Editor ed = state.edit().putInt("onehand", m);
        if (m != 0) ed.putInt("onehand_side", m);
        if (byUser) ed.putLong("mode_t", System.currentTimeMillis());
        ed.apply();
        applyOneHand(true);
    }

    private void applyOneHand(boolean animate) {
        if (root == null || kv == null) return;
        int total = getResources().getDisplayMetrics().widthPixels;
        boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int mode = (land && !Ui.tablet(this)) ? 0 : oneHand;
        kv.setOneHand(oneHand);
        if (!animate && SystemClock.uptimeMillis() > ohAnimEnd) settleOneHand(mode);
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
        h.removeCallbacks(settleR);
        h.postDelayed(settleR, 560);
        DecelerateInterpolator dec = new DecelerateInterpolator(1.6f);
        if (prev == 0) {
            kvView.setPivotX(mode == 2 ? kw : 0f);
            kvView.setScaleX(1f / ONE_HAND_FRAC);
            kvView.animate().scaleX(1f).setDuration(340).setInterpolator(new OvershootInterpolator(0.8f)).start();
            side.setAlpha(0f); side.setTranslationX(mode == 2 ? -dist * 0.6f : dist * 0.6f);
            side.animate().alpha(1f).translationX(0f).setStartDelay(60).setDuration(300).setInterpolator(dec).start();
        } else if (mode == 0) {
            kvView.setPivotX(prev == 2 ? total : 0f);
            kvView.setScaleX(ONE_HAND_FRAC);
            kvView.animate().scaleX(1f).setDuration(280).setInterpolator(dec).start();
            if (side != null) {
                side.setVisibility(View.VISIBLE);
                side.animate().alpha(0f).translationX(prev == 2 ? -dist * 0.6f : dist * 0.6f).setDuration(200).withEndAction(new Runnable() {
                    @Override public void run() { if (ohMode == 0 && side != null) { side.setVisibility(View.GONE); side.setAlpha(1f); side.setTranslationX(0f); } }
                }).start();
            }
        } else {
            kvView.setTranslationX(mode == 2 ? -dist : dist);
            kvView.animate().translationX(0f).setDuration(380).setInterpolator(new OvershootInterpolator(0.7f)).start();
            side.setTranslationX(mode == 2 ? dist : -dist);
            side.animate().translationX(0f).setDuration(380).setInterpolator(new OvershootInterpolator(0.7f)).start();
        }
    }

    private final Runnable settleR = new Runnable() { @Override public void run() { repairOneHand(); } };

    private void repairOneHand() {
        if (root == null || kv == null || kvView == null || SystemClock.uptimeMillis() <= ohAnimEnd) return;
        boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int mode = (land && !Ui.tablet(this)) ? 0 : oneHand;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) kvView.getLayoutParams();
        boolean wide = lp.width == ViewGroup.LayoutParams.MATCH_PARENT;
        if ((mode == 0) != wide || (mode != 0 && side == null)) { ohApplied = -1; applyOneHand(false); return; }
        if (kvView.getScaleX() != 1f || kvView.getTranslationX() != 0f) { kvView.setScaleX(1f); kvView.setTranslationX(0f); }
        if (mode != 0 && side != null && (side.getAlpha() != 1f || side.getTranslationX() != 0f || side.getVisibility() != View.VISIBLE)) {
            side.setAlpha(1f); side.setTranslationX(0f); side.setVisibility(View.VISIBLE);
        }
        if (mode == 0 && side != null) { root.removeView(side); side = null; }
    }

    private void settleOneHand(int mode) {
        if (kvView == null) return;
        kvView.setScaleX(1f); kvView.setTranslationX(0f);
        if (side == null) return;
        if (mode == 0) { root.removeView(side); side = null; return; }
        side.animate().cancel(); side.setAlpha(1f); side.setTranslationX(0f); side.setVisibility(View.VISIBLE);
    }

    private void syncModes() {
        long cs = cfg.getLong("mode_t", 0L);
        if (cs > state.getLong("mode_t", 0L)) {
            String oh = cfg.getString("onehand", "off");
            int m = "left".equals(oh) ? 1 : "right".equals(oh) ? 2 : 0;
            boolean sp = cfg.getBoolean("split", false);
            state.edit().putLong("mode_t", cs).apply();
            if (m != oneHand) setOneHandMode(m, false);
            if (sp != split) setSplitMode(sp, false);
        }
        kv.setSplit(split);
        applyOneHand(false);
        repairOneHand();
        long cc = cfg.getLong("clip_clear", 0L);
        if (cc > state.getLong("clip_clear_done", 0L)) { wipeClips(); state.edit().putLong("clip_clear_done", cc).apply(); }
        else if (!cfg.getBoolean("clip_hist", true)) { if (clipFile().exists()) wipeClips(); }
        else if (clipsLoaded && clipStore.expire(nowMin(), clipTtl())) saveClips();
        else if (!clipsLoaded && clipFile().exists() && System.currentTimeMillis() - clipFile().lastModified() > clipTtl() * 60_000L) {
            if (cs().expire(nowMin(), clipTtl())) saveClips();
        }
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
                    if (ic != null) { resetFlags(); ic.commitText(t, 1); showUndo(t, true); }
                    if (cfg.getBoolean("clip_close", true)) hideClips(true);
                }
                @Override public void onAbc() { hideClips(true); }
                @Override public void onChanged() { saveClips(); }
            });
            root.addView(clipPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (cs().expire(nowMin(), clipTtl())) saveClips();
        clipPanel.open(clipStore, cfg.getBoolean("clip_hist", true));
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

    private void observe(CharSequence before, String low) {
        if (noLearn) return;
        int k = kindOf(low);
        learner.bump(low, k);
        lastFin = low; lastFinAt = SystemClock.uptimeMillis(); delSince = 0;
        if (before == null || !pairable(k, low)) return;
        String pv = Learner.prevWord(before, before.length(), 32);
        if (pv != null && (pv.isEmpty() || pairable(-1, pv))) learner.pair(pv, low);
    }

    private String lastFin;
    private long lastFinAt;
    private int delSince;

    private boolean pairable(int kind, String w) {
        if (kind == 0 || kind == 3 || learner.known(w)) return true;
        if (kind >= 0) return false;
        Lexicon lx = lay.own() ? Lexicon.peek(lay.id) : null;
        return lx == null || lx.contains(w);
    }

    private int kindOf(String w) {
        final Lexicon lx = lay.own() ? Lexicon.peek(lay.id) : null;
        if (addrField || lx == null) return 3;
        if (!ownPruned) { ownPruned = true; learner.pruneOwn(new Learner.Has() { @Override public boolean has(String x) { return lx.contains(x); } }); }
        if (lx.contains(w)) return 0;
        if (lx.n < 15000 || w.length() < 3) return 1;
        int odd = lx.unseenTriples(w);
        if (odd >= 2) return 5;
        String c = w.equals(lastCorrFor) ? lastCorr : lx.correct(w);
        return c != null || odd == 1 ? 2 : 1;
    }

    private void spellRefresh(InputConnection ic) {
        CharSequence b = tb(ic, 32);
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

    private void applyImport() {
        File f = new File(getFilesDir(), "import.json");
        if (!f.exists()) return;
        try {
            byte[] b = Vault.readFile(f);
            if (b == Vault.LOCKED) return;
            if (b == null) { f.delete(); return; }
            JSONObject o = new JSONObject(new String(b, "UTF-8"));
            JSONObject l = o.optJSONObject("learn");
            String c = o.has("clips") && !o.isNull("clips") ? o.getString("clips") : null;
            if ((l != null && learnLocked) || (c != null && cs().locked)) return;
            if (l != null) {
                learner.clear();
                learner.loadUser(l.optString("user_words", ""));
                learner.loadWords(l.optString("learned", ""));
                learner.loadPairs(l.optString("pairs", ""));
                learner.loadOwn(l.has("own") ? l.optString("own", "") : null);
                learner.markAllDirty();
                saveLearned();
            }
            if (c != null) { ClipStore store = cs(); store.load(c); store.touchAll(nowMin()); saveClips(); }
        } catch (IOException | JSONException | RuntimeException ignored) { }
        f.delete();
    }

    private File learnFile() { return new File(getFilesDir(), LearnStore.FILE); }

    private void loadLearned() {
        String[] v = LearnStore.read(learnFile());
        boolean legacy = false;
        if (v == LearnStore.LOCKED) { learnLocked = true; v = new String[]{"", "", "", ""}; }
        else if (v == null) {
            legacy = state.contains("learned") || state.contains("pairs") || state.contains("user_words");
            v = legacy ? new String[]{state.getString("learned", ""), state.getString("pairs", ""), state.getString("user_words", ""), state.getString("own", null)}
                       : new String[]{"", "", "", ""};
        }
        learner.loadUser(v[2]); learner.loadWords(v[0]); learner.loadPairs(v[1]); learner.loadOwn(v[3]);
        if (legacy) {
            learner.markAllDirty();
            if (saveLearnedNow()) state.edit().remove("learned").remove("pairs").remove("user_words").remove("own").apply();
        }
    }

    private void retryLearn() {
        String[] v = LearnStore.read(learnFile());
        if (v == LearnStore.LOCKED) {
            if (learnTries < 1000) learnTries++;
            return;
        }
        learnTries = 0;
        learnLocked = false;
        if (v != null) { learner.loadUser(v[2]); learner.loadWords(v[0]); learner.loadPairs(v[1]); }
        learner.markAllDirty();
    }

    private boolean saveLearnedNow() {
        if (learnLocked || !learner.isDirty()) return true;
        try { LearnStore.write(learnFile(), learner.snapshot(), Vault.nextSeq()); return true; }
        catch (IOException | RuntimeException e) { learner.markAllDirty(); return false; }
    }

    private void saveLearned() {
        if (learnLocked || !learner.isDirty()) return;
        final String[] snap = learner.snapshot();
        final long q = Vault.nextSeq();
        final File f = learnFile();
        bg().post(new Runnable() { @Override public void run() {
            try { LearnStore.write(f, snap, q); }
            catch (IOException | RuntimeException e) { h.post(new Runnable() { @Override public void run() { learner.markAllDirty(); } }); }
        } });
    }

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

    @Override public void onSuggest(int i) { try { onSuggestImpl(i); } catch (RuntimeException e) { Diag.save(this, e); } }

    private void onSuggestImpl(int i) {
        if ((!suggestOn && !(fieldOk && !lay.own())) || i < 0 || i > 2 || commit[i] == null) return;
        InputConnection ic = getCurrentInputConnection();
        String t = commit[i];
        if (ic == null || t == null || t.isEmpty()) return;
        if (swipeAlt) { ic.deleteSurroundingText(swipeLen, 0); swipeAlt = false; }
        else if (!prefix.isEmpty()) {
            ic.deleteSurroundingText(prefix.length(), 0);
            if (t.equalsIgnoreCase(prefix) || (i == 0 && t.equals(prefix))) learn(prefix.toLowerCase(Locale.ROOT));
        }
        observe(tb(ic, 32), t.toLowerCase(Locale.ROOT));
        ic.commitText(t + " ", 1);
        afterPunct = 0; acArmed = false; trailAutoSpace = true; clearWord(); capsNeed = true;
        shiftManual = false;
        schedule();
    }

    private String enterLabel() {
        if (ei == null || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return null;
        switch (ei.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_GO:     return KbText.t("Перейти");
            case EditorInfo.IME_ACTION_SEARCH: return KbText.t("Найти");
            case EditorInfo.IME_ACTION_SEND:   return KbText.t("Отпр.");
            case EditorInfo.IME_ACTION_NEXT:   return KbText.t("Далее");
            case EditorInfo.IME_ACTION_DONE:   return KbText.t("Готово");
            default: return null;
        }
    }

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

        if (autoCaps && capsNeed && ei != null && shift != LOCK && !shiftManual && !slow()) {
            capsNeed = false;
            int ns = ic.getCursorCapsMode(ei.inputType) != 0 ? ON : OFF;
            if (ns != shift) { shift = ns; kv.setShift(ns); }
        }
        kv.setEnterLabel(enterLabel());
        if (listening) return;
        if (!showWords) { setChip(null); kv.setSuggestions(null, null, null); return; }
        boolean imgNow = imgUri != null && !pwdField;
        boolean clipNow = (clipText != null && chipOk()) || imgNow;
        String chipTxt = imgNow ? imgLabel : (clipNow ? preview(clipText) : null);
        kv.setClipImage(imgNow ? imgThumb : null);
        if (swipeAlt) { setChip(null); kv.setSuggestions(shown[0], shown[1], shown[2]); return; }
        if (!suggestOn) { setChip(chipTxt); if (fieldOk && !noLearn && !lay.own()) spellRefresh(ic); else kv.setSuggestions(null, null, null); return; }

        CharSequence b = tb(ic, 32);
        if ((b == null || b.length() == 0) && wordBuf.length() > 0) {
            prefix = wordBuf.toString();
        } else {
            int e = b == null ? 0 : b.length(), s = e;
            while (s > 0 && Learner.wordChar(b.charAt(s - 1))) s--;
            prefix = b == null ? "" : b.subSequence(s, e).toString();
        }

        setChip(clipNow && prefix.isEmpty() ? chipTxt : null);
        for (int i = 0; i < 3; i++) { shown[i] = null; commit[i] = null; }
        if (prefix.isEmpty()) {

            int n = 0;
            String pv = b == null ? null : Learner.prevWord(b, b.length(), 32);
            if (pv != null) n = learner.next(pv, nxt, 0);
            n = learner.frequent(nxt, n, 3);
            String[] d = lay.id.equals("en") ? DEF_EN : ru ? DEF_RU : new String[0];
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
            if (pb != null && !(n > 0 && pb.equals(match[0])) && !(n > 1 && pb.equals(match[1]))) {
                match[2] = match[1]; match[1] = match[0]; match[0] = pb; n = Math.min(3, n + 1);
            }
            String corr = null;
            if (acOn && lex != null && lex.n >= 15000 && prefix.length() >= 3 && !learner.known(low)) {
                if (low.equals(lastCorrFor)) corr = lastCorr;
                else askCorrect(lex, low, tt.matches(low) ? tt.copy() : null);
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
                if (lex != null && lex.n >= 15000 && prefix.length() >= 5 && !learner.known(low)) {
                    String sp = lex.split(low);
                    if (sp != null) { shown[2] = commit[2] = caseLike(sp); }
                }
            }
        }
        kv.setSuggestions(shown[0], shown[1], shown[2]);
    }

    private void askCorrect(final Lexicon lex, final String low, final Touch snap) {
        if (corrJob != null) bg().removeCallbacks(corrJob);
        corrJob = new Runnable() {
            @Override public void run() {
                final String r = lex.correct(low, 0L, snap);
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

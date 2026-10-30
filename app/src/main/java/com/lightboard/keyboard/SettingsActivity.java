package com.lightboard.keyboard;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.content.DialogInterface;
import android.view.Window;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Настройки в стиле Material 3 Expressive: пять вкладок, крупные скругления, цвета из системной гаммы (на Android 12+ динамические),
 * свои язык, тема и палитра. Работают в отдельном процессе и выгружаются, когда не нужны. Всё рисуется кодом, без библиотек.
 */
public final class SettingsActivity extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT, REQ_EXPORT = 41, REQ_IMPORT = 42, REQ_DICT = 43;

    private SharedPreferences prefs;
    private float d;
    private boolean dark, wordsOpen, appsOpen;
    private int bg, card, tonal, text, muted, line, accent, onAccent, tab;
    private String wordsFilter = "";
    private TextView status, lastTitle;
    private volatile boolean busy;
    private ScrollView sv;
    private LinearLayout content;
    private final Handler hd = new Handler();
    private static int started; // открытых экземпляров экрана: из клавиатуры может подняться второй, пока первый уходит
    private boolean picking, stopped; // picking: открыт выбор файла или запрос разрешения, процесс должен дожить до ответа
    private final Runnable killR = new Runnable() { @Override public void run() { die(); } };

    /** Настройки не живут в памяти: как только экран закрыт (и ничего не качается), процесс завершается. Записи сначала доводятся до диска. */
    private void die() {
        if (busy || picking || started > 0) return;
        prefs.edit().commit();
        Process.killProcess(Process.myPid());
    }

    private String t(String ru) { return L10n.t(ru); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("lb", MODE_PRIVATE);
        d = getResources().getDisplayMetrics().density;
        if (b != null) tab = b.getInt("tab", 0);
        buildShell();
    }

    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); o.putInt("tab", tab); }

    @Override protected void onStart() { super.onStart(); started++; stopped = false; hd.removeCallbacks(killR); }

    /** Ушли с экрана: через долю секунды (вдруг вернулись) процесс настроек завершается, клавиатура — отдельный процесс. */
    @Override protected void onStop() { super.onStop(); started--; stopped = true; hd.postDelayed(killR, 400); }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) die();
    }

    @Override protected void onResume() {
        super.onResume();
        syncFromKeyboard();
    }

    /** Режим одной руки и разделение можно менять и в самой клавиатуре: настройки показывают то, что включено на самом деле. */
    private void syncFromKeyboard() {
        SharedPreferences st = keyboardState();
        int oh = st.getInt("onehand", 0);
        String ohs = oh == 1 ? "left" : oh == 2 ? "right" : "off";
        boolean sp = st.getBoolean("split", false);
        if (!ohs.equals(prefs.getString("onehand", "off")) || sp != prefs.getBoolean("split", false))
            prefs.edit().putString("onehand", ohs).putBoolean("split", sp).apply();
    }

    private void palette() {
        String at = prefs.getString("app_theme", "auto");
        dark = at.equals("dark") || (at.equals("auto") && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        Theme th = new Theme();
        th.setMaterial(this, dark, prefs.getString("app_palette", "auto"));
        bg = th.panelTop; card = th.key; tonal = th.spec; text = th.text; muted = th.muted; line = th.div; accent = th.accent; onAccent = th.onAccent;
    }

    private boolean isMaterial() { return prefs.getString("design", "gboard").equals("gboard"); }

    private Dlg dlg() { return new Dlg(); }

    interface Pick { void on(int i); }

    /** Диалог в цветах приложения (системный AlertDialog их не знает). Методы те же, что у AlertDialog.Builder, плюс выбор из списка и произвольные ряды. */
    private final class Dlg {
        private final Dialog d = new Dialog(SettingsActivity.this);
        private final LinearLayout box = new LinearLayout(SettingsActivity.this), items = new LinearLayout(SettingsActivity.this), btns = new LinearLayout(SettingsActivity.this);
        private final TextView tt = label("", 20, text, 2);

        Dlg() {
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(px(8), px(22), px(8), px(8));
            GradientDrawable g = new GradientDrawable();
            g.setColor(card); g.setCornerRadius(px(28));
            box.setBackground(g);
            tt.setPadding(px(16), 0, px(16), px(8));
            box.addView(tt);
            items.setOrientation(LinearLayout.VERTICAL);
            ScrollView sv = new ScrollView(SettingsActivity.this) {
                @Override protected void onMeasure(int w, int h) {
                    int cap = (int) (getResources().getDisplayMetrics().heightPixels * 0.6f);
                    super.onMeasure(w, MeasureSpec.makeMeasureSpec(MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED ? cap : Math.min(MeasureSpec.getSize(h), cap), MeasureSpec.AT_MOST));
                }
            };
            sv.addView(items);
            box.addView(sv);
            btns.setGravity(Gravity.END);
            box.addView(btns);
            d.setContentView(box);
        }

        Dlg setTitle(String s) { tt.setText(s); return this; }

        Dlg setMessage(String s) {
            TextView m = label(s, 14, muted, 0);
            m.setPadding(px(16), px(4), px(16), px(12));
            items.addView(m);
            return this;
        }

        Dlg addView(View v) { items.addView(v); return this; }

        Dlg setPositiveButton(String s, DialogInterface.OnClickListener l) { btn(s, true, l); return this; }

        Dlg setNegativeButton(String s, DialogInterface.OnClickListener l) { btn(s, false, l); return this; }

        private void btn(String s, boolean main, final DialogInterface.OnClickListener l) {
            TextView b = label(s, 15, accent, main ? 2 : 1);
            b.setPadding(px(18), px(12), px(18), px(12));
            pressable(b, new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); if (l != null) l.onClick(d, 0); } });
            btns.addView(b, main ? btns.getChildCount() : 0); // «Отмена» левее главной кнопки
        }

        Dlg setMultiChoiceItems(String[] names, final boolean[] on, final DialogInterface.OnMultiChoiceClickListener l) {
            for (int i = 0; i < names.length; i++) {
                final int idx = i;
                LinearLayout r = new LinearLayout(SettingsActivity.this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(px(16), px(10), px(16), px(10));
                r.addView(label(names[i], 16, text, 0), new LinearLayout.LayoutParams(0, WRAP, 1f));
                final SwitchView sw = new SwitchView(SettingsActivity.this, on[i], accent, onAccent, tonal, muted);
                sw.setListener(new SwitchView.Listener() { @Override public void onChanged(boolean v) { on[idx] = v; if (l != null) l.onClick(d, idx, v); } });
                r.addView(sw);
                pressable(r, new View.OnClickListener() { @Override public void onClick(View v) { sw.toggle(); } });
                items.addView(r);
            }
            return this;
        }

        /** Выбор одного пункта: нажатие закрывает диалог и сообщает номер. */
        Dlg setSingleChoice(String[] names, int checked, final Pick pick) {
            for (int i = 0; i < names.length; i++) {
                final int idx = i;
                TextView r = label(names[i], 16, i == checked ? accent : text, i == checked ? 1 : 0);
                r.setPadding(px(16), px(14), px(16), px(14));
                pressable(r, new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); pick.on(idx); } });
                items.addView(r);
            }
            return this;
        }

        void dismiss() { d.dismiss(); }

        void show() {
            d.show();
            Window w = d.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setLayout(Math.min(px(360), getResources().getDisplayMetrics().widthPixels - px(40)), WRAP);
            }
        }
    }

    /** Есть ли в системе приложение, умеющее обработать такое действие (иначе пункт настроек не показываем). */
    private boolean can(Intent i) { return getPackageManager().resolveActivity(i, 0) != null; }

    // ---------- каркас: содержимое вкладки и нижняя навигация ----------
    private void buildShell() {
        L10n.set(prefs.getString("app_lang", "auto"));
        palette();
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(bg);
        sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(16), px(12), px(16), px(24));
        FrameLayout box = new FrameLayout(this); // на широких экранах (планшет, альбом) содержимое по центру, не растянуто
        boolean wide = getResources().getConfiguration().screenWidthDp >= 600;
        box.addView(content, new FrameLayout.LayoutParams(wide ? px(640) : MATCH, WRAP, Gravity.CENTER_HORIZONTAL));
        sv.addView(box, new ViewGroup.LayoutParams(MATCH, WRAP));
        shell.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        NavBarView nav = new NavBarView(this, new String[]{t("Начало"), t("Вид"), t("Ввод"), t("Данные"), t("Ещё")}, card, text, muted, tonal);
        nav.select(tab);
        nav.setListener(new NavBarView.Listener() { @Override public void onTab(int i) { tab = i; showTab(true); } });
        shell.addView(nav, new LinearLayout.LayoutParams(MATCH, WRAP));
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(card);
        int fl = getWindow().getDecorView().getSystemUiVisibility();
        fl = dark ? (fl & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) : (fl | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        if (Build.VERSION.SDK_INT >= 26) fl = dark ? (fl & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) : (fl | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().getDecorView().setSystemUiVisibility(fl);
        setContentView(shell);
        showTab(false);
    }

    private void showTab(boolean animate) {
        content.removeAllViews();
        status = null;
        switch (tab) {
            case 0: tabHome(); break;
            case 1: tabLook(); break;
            case 2: tabInput(); break;
            case 3: tabData(); break;
            default: tabAbout(); break;
        }
        if (animate) {
            sv.scrollTo(0, 0);
            content.setAlpha(0f); content.setTranslationY(px(12));
            content.animate().alpha(1f).translationY(0f).setDuration(220).start();
        }
    }

    // ---------- вкладка «Начало» ----------
    private void tabHome() {
        content.addView(title(t("Начало")));
        content.addView(section(t("Подключение")));
        LinearLayout c1 = card();
        row(c1, step(1, t("Включить в настройках"), new View.OnClickListener() { @Override public void onClick(View v) { startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)); } }));
        row(c1, step(2, t("Выбрать клавиатуру"), new View.OnClickListener() { @Override public void onClick(View v) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        } }));
        row(c1, step(3, t("Многоязычный ввод") + ": " + SysLangs.names(this), new View.OnClickListener() { @Override public void onClick(View v) { openSystemLanguages(); } }));
        content.addView(c1);
        status = label("", 13, muted, 0);
        status.setPadding(px(20), px(12), px(20), 0);
        content.addView(status);

        content.addView(section(t("Проверка ввода")));
        LinearLayout c2 = card();
        EditText test = new EditText(this);
        test.setHint(t("Нажмите и наберите текст"));
        test.setHintTextColor(muted); test.setTextColor(text);
        test.setBackground(null);
        test.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        test.setMinLines(3);
        test.setGravity(Gravity.TOP);
        test.setPadding(px(20), px(16), px(20), px(16));
        c2.addView(test);
        content.addView(c2);

        if (can(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/*"))) {
            content.addView(section(t("Словари")));
            LinearLayout cl = card();
            row(cl, action(t("Импорт словаря из файла"), null, new View.OnClickListener() { @Override public void onClick(View v) { importDict(); } }));
            String di = dictInfo();
            if (!di.isEmpty()) { TextView dv = rowText(); dv.setText(di); dv.setTextColor(muted); dv.setTextSize(13); row(cl, dv); }
            row(cl, donateRow(t("Где взять словари"), "https://github.com/hermitdave/FrequencyWords", "https://github.com/hermitdave/FrequencyWords", null, true));
            content.addView(cl);
        }
    }

    // ---------- вкладка «Вид» ----------
    private void tabLook() {
        syncFromKeyboard();
        final boolean material = isMaterial();
        final String p = material ? "g_" : "i_";
        final Runnable again = new Runnable() { @Override public void run() { showTab(false); } };
        content.addView(title(t("Вид")));

        content.addView(section(t("Клавиатура")));
        LinearLayout c1 = card();
        row(c1, segment(t("Дизайн"), "design", "gboard", again, "gboard", "Material", "ios", "Cupertino"));
        content.addView(c1);

        content.addView(section(material ? t("Оформление Material") : t("Оформление Cupertino")));
        LinearLayout c2 = card();
        row(c2, segment(t("Тема"), p + "theme", "auto", null, "auto", t("Авто"), "light", t("Светлая"), "dark", t("Тёмная")));
        if (material) {
            row(c2, colorRow(t("Цвета клавиатуры"), "g_material", "auto", again));
        } else {
            if (Build.VERSION.SDK_INT >= 31) { // на более старых Android размытия окна нет: пункты не показываем
                row(c2, toggle(t("Размытие подложки"), null, "i_blur", true, null));
                row(c2, slider(t("Сила размытия"), "i_blurDp", 20, 0, 40, " dp"));
            }
            row(c2, slider(t("Непрозрачность подложки"), "i_panelPct", 80, 30, 100, " %"));
        }
        row(c2, slider(t("Высота клавиатуры"), p + "height", 100, 80, 160, " %"));
        row(c2, slider(t("Закругление клавиш"), p + "round", Ui.defRound(this, !material), 0, 100, " %"));
        row(c2, slider(t("Нижний отступ"), p + "botpad", 0, 0, 40, " dp"));
        row(c2, slider(t("Размер шрифта"), p + "font", 100, 70, 140, " %"));
        content.addView(c2);

        content.addView(section(t("Приложения")));
        LinearLayout cApps = card();
        appsSection(cApps);
        content.addView(cApps);

        content.addView(section(t("Клавиши")));
        LinearLayout c3 = card();
        row(c3, toggle(t("Подложки клавиш"), null, p + "keybg", true, null));
        row(c3, toggle(t("Показывать ряд цифр"), null, p + "digits", false, null));
        row(c3, toggle(t("Показывать подсказки"), t("Если выключить, пропадёт и меню над клавишами (кнопка слева на верхней панели)"), p + "words", true, null));
        row(c3, toggle(t("Отдельная клавиша языка"), t("Рядом с эмодзи, забирает часть пробела"), p + "langkey", false, null));
        row(c3, toggle(t("Глобус вместо смайла"), t("Смайлы — долгим нажатием"), p + "globe", false, null));
        if (!prefs.getBoolean(p + "digits", true)) row(c3, toggle(t("Цифры на верхнем ряду букв"), null, p + "hintnum", false, null));
        row(c3, toggle(t("Символы на клавишах с буквами"), null, p + "hintsym", false, null));
        row(c3, toggle(t("Увеличивать при нажатии"), t("Миниатюра буквы над нажатой клавишей"), p + "popup", true, null));
        if (material) row(c3, toggle(t("Значок L на пробеле"), null, "g_logo", false, null));
        if (micInstalled()) row(c3, toggle(t("Показывать микрофон"), null, p + "mic", true, again));
        row(c3, toggle(t("Нижняя панель"), t("Смайл/глобус и микрофон под клавишами"), p + "bottombar", false, again));
        if (prefs.getBoolean(p + "bottombar", false)) {
            if (micInstalled() && prefs.getBoolean(p + "mic", true)) row(c3, segment(t("Кнопка микрофона"), "g_micpos", "top", null, "top", t("Сверху"), "bottom", t("Снизу"), "both", t("Везде")));
            row(c3, toggle(t("Поменять местами значки"), null, p + "swapicons", false, null));
        }
        content.addView(c3);

        content.addView(section(t("Режимы")));
        LinearLayout c4 = card();
        row(c4, segment(t("Режим одной руки"), "onehand", "off", null, "off", t("Выкл"), "left", t("Слева"), "right", t("Справа")));
        row(c4, toggle(t("Разделённая клавиатура"), null, "split", false, null));
        content.addView(c4);
    }

    // ---------- вкладка «Ввод» ----------
    private void tabInput() {
        final String p = isMaterial() ? "g_" : "i_";
        content.addView(title(t("Ввод")));

        content.addView(section(t("Текст")));
        LinearLayout c2 = card();
        row(c2, toggle(t("Непрерывный ввод"), null, p + "swipe", true, null));
        row(c2, toggle(t("Рисовать линию"), null, p + "trail", true, null));
        row(c2, toggle(t("Автоисправление"), null, p + "autocorrect", true, null));
        row(c2, toggle(t("Заглавные автоматически"), null, p + "autocaps", true, null));
        row(c2, toggle(t("Точки автоматически"), t("Двойной пробел ставит точку"), p + "autoperiod", true, null));
        row(c2, toggle(t("Пробел после . и ,"), null, p + "autospace", true, null));
        row(c2, toggle(t("Умное тире"), null, "smartdash", false, null));
        row(c2, toggle(t("Режим инкогнито"), t("Включается удержанием пробела"), "incognito", true, null));
        if (autofillOk()) // нужен Android 11+ и включённая служба автозаполнения
            row(c2, toggle(t("Автозаполнение Google"), t("Данные из автозаполнения появляются на панели подсказок"), "autofill", true, null));
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) // без телефонного модуля (Wi-Fi планшет) СМС не приходят
            row(c2, toggle(t("Коды из СМС"), t("Код подтверждения из СМС появляется на панели подсказок. Нужен доступ к СМС"), "sms", false, new Runnable() { @Override public void run() { smsToggled(); } }));
        row(c2, toggle(t("Недавние скриншоты в подсказках"), t("Нужен доступ к фото"), "shots", false, new Runnable() { @Override public void run() { if (prefs.getBoolean("shots", false)) askShots(); } }));
        row(c2, toggle(t("Калькулятор"), t("Наберите выражение и «=»: ответ появится сразу, например 12+3*4="), "calc", true, null));
        row(c2, toggle(t("Буфер обмена в подсказках"), null, p + "clip", true, null));
        row(c2, toggle(t("Курсор свайпом по пробелу"), t("Вправо-влево по строке, вверх-вниз по строкам"), p + "spacecursor", true, null));
        row(c2, toggle(t("Стереть всё"), t("Свайп вверх по «стереть» и удержание"), p + "clearall", true, null));
        row(c2, slider(t("Время удержания"), p + "clearhold", 600, 300, 1500, t(" мс")));
        content.addView(c2);

        content.addView(section(t("Отклик")));
        LinearLayout c3 = card();
        row(c3, toggle(t("Вибрация"), null, p + "haptic", true, null));
        row(c3, toggle(t("Звук нажатий"), t("Не звучат в беззвучном режиме"), p + "sound", false, null));
        row(c3, slider(t("Громкость звука нажатий"), p + "soundvol2", 30, 0, 100, " %"));
        content.addView(c3);
    }

    // ---------- возможности системы: показываем только то, что на этом телефоне работает ----------
    private boolean micInstalled() { return android.speech.SpeechRecognizer.isRecognitionAvailable(this); }

    private boolean autofillOk() {
        if (Build.VERSION.SDK_INT < 30) return false;
        android.view.autofill.AutofillManager am = getSystemService(android.view.autofill.AutofillManager.class);
        return am != null && am.isAutofillSupported();
    }

    // ---------- язык самого приложения ----------
    private static final String[] LANG_IDS = {"auto", "ru", "en", "uk", "de", "es", "pt", "fr", "it", "tr", "pl", "id", "vi"};
    private static final String[] LANG_NAMES = {null, "Русский", "English", "Українська", "Deutsch", "Español", "Português", "Français", "Italiano", "Türkçe", "Polski", "Bahasa Indonesia", "Tiếng Việt"};

    private String langName(String id) {
        for (int i = 1; i < LANG_IDS.length; i++) if (LANG_IDS[i].equals(id)) return LANG_NAMES[i];
        return t("Авто");
    }

    private void appLangDialog() {
        String[] names = new String[LANG_IDS.length];
        int cur = 0;
        String c = prefs.getString("app_lang", "auto");
        for (int i = 0; i < names.length; i++) { names[i] = i == 0 ? t("Авто") : LANG_NAMES[i]; if (LANG_IDS[i].equals(c)) cur = i; }
        dlg().setTitle(t("Язык приложения")).setSingleChoice(names, cur, new Pick() { @Override public void on(int i) { prefs.edit().putString("app_lang", LANG_IDS[i]).apply(); buildShell(); } })
                .setNegativeButton(t("Отмена"), null).show();
    }

    // ---------- настройки отдельных приложений (дизайн, тема, цвета); пока ничего не выбрано, всё подчиняется общим ----------
    private HashMap<String, String[]> readApps() {
        HashMap<String, String[]> m = new HashMap<>();
        String raw = prefs.getString("apps", "");
        if (!raw.isEmpty()) for (String e : raw.split(";")) { String[] f = e.split(":"); if (f.length == 4) m.put(f[0], new String[]{f[1], f[2], f[3]}); }
        return m;
    }

    private void writeApps(HashMap<String, String[]> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String[]> e : m.entrySet()) {
            String[] f = e.getValue();
            if (f[0].equals("-") && f[1].equals("-") && f[2].equals("-")) continue;
            sb.append(e.getKey()).append(':').append(f[0]).append(':').append(f[1]).append(':').append(f[2]).append(';');
        }
        prefs.edit().putString("apps", sb.toString()).apply();
    }

    /** Плавно раскрывает или сворачивает блок: высота и прозрачность. */
    private void animateOpen(final View v, final boolean open) {
        final ViewGroup.LayoutParams lp = v.getLayoutParams();
        int h0 = v.getHeight();
        if (open) {
            v.setVisibility(View.VISIBLE);
            v.measure(View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().widthPixels, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            h0 = v.getMeasuredHeight();
        }
        final int h = h0;
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(open ? 0f : 1f, open ? 1f : 0f);
        a.setDuration(260);
        a.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator va) {
                float f = (Float) va.getAnimatedValue();
                lp.height = (int) (h * f); v.setLayoutParams(lp); v.setAlpha(f);
            }
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator an) { lp.height = WRAP; v.setLayoutParams(lp); v.setAlpha(1f); if (!open) v.setVisibility(View.GONE); }
        });
        a.start();
    }

    // ---------- настройки отдельных приложений (дизайн, тема, цвета); пока ничего не выбрано, всё подчиняется общим ----------
    private void appsSection(final LinearLayout card) {
        final LinearLayout head = (LinearLayout) action(t("Для отдельных приложений") + (appsOpen ? "  ▴" : "  ▾"), null, null);
        final TextView ht = lastTitle;
        row(card, head);
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        card.addView(body);
        body.setVisibility(appsOpen ? View.VISIBLE : View.GONE);
        if (appsOpen) buildApps(body);
        head.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            appsOpen = !appsOpen;
            ht.setText(t("Для отдельных приложений") + (appsOpen ? "  ▴" : "  ▾"));
            if (appsOpen && body.getChildCount() == 0) buildApps(body);
            animateOpen(body, appsOpen);
        } });
    }

    private void buildApps(final LinearLayout body) {
        body.removeAllViews();
        final HashMap<String, String[]> m = readApps();
        body.addView(action(t("Сброс для всех приложений"), null, new View.OnClickListener() { @Override public void onClick(View v) {
            dlg().setTitle(t("Сбросить настройки всех приложений?"))
                .setPositiveButton(t("Сбросить"), new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) { prefs.edit().remove("apps").apply(); buildApps(body); } })
                .setNegativeButton(t("Отмена"), null).show();
        } }));
        PackageManager pm = getPackageManager();
        ArrayList<String[]> apps = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            apps.add(new String[]{String.valueOf(r.loadLabel(pm)), pkg});
        }
        Collections.sort(apps, new java.util.Comparator<String[]>() { @Override public int compare(String[] a, String[] b) { return a[0].compareToIgnoreCase(b[0]); } });
        for (String[] a : apps) body.addView(appItem(a[1], a[0], m));
    }

    private String appSummary(String[] f) {
        StringBuilder sb = new StringBuilder();
        if (!f[0].equals("-")) sb.append(f[0].equals("ios") ? "Cupertino" : "Material");
        if (!f[1].equals("-")) sb.append(sb.length() > 0 ? " · " : "").append(f[1].equals("dark") ? t("Тёмная") : t("Светлая"));
        if (!f[2].equals("-")) sb.append(sb.length() > 0 ? " · " : "").append("●");
        return sb.toString();
    }

    private View appItem(final String pkg, String name, final HashMap<String, String[]> m) {
        final String[] f = m.containsKey(pkg) ? m.get(pkg) : new String[]{"-", "-", "-"};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(20), px(12), px(20), px(12));
        r.addView(label(name, 15, text, 0), new LinearLayout.LayoutParams(0, WRAP, 1f));
        final TextView sum = label(appSummary(f), 12, muted, 0);
        r.addView(sum);
        box.addView(r);
        final LinearLayout opts = new LinearLayout(this);
        opts.setOrientation(LinearLayout.VERTICAL);
        opts.setPadding(px(20), 0, px(20), px(10));
        opts.setVisibility(View.GONE);
        box.addView(opts);
        pressable(r, new View.OnClickListener() { @Override public void onClick(View v) {
            boolean open = opts.getVisibility() != View.VISIBLE;
            if (open && opts.getChildCount() == 0) appOptions(opts, pkg, f, m, sum);
            animateOpen(opts, open);
        } });
        return box;
    }

    private void saveApp(String pkg, String[] f, HashMap<String, String[]> m, TextView sum) {
        m.put(pkg, f);
        writeApps(m);
        sum.setText(appSummary(f));
    }

    private void appOptions(LinearLayout opts, final String pkg, final String[] f, final HashMap<String, String[]> m, final TextView sum) {
        final String[] dIds = {"-", "gboard", "ios"}, tIds = {"-", "light", "dark"};
        opts.addView(label(t("Дизайн"), 13, muted, 0));
        SegmentView sd = new SegmentView(this, new String[]{t("Общие"), "Material", "Cupertino"}, Math.max(0, java.util.Arrays.asList(dIds).indexOf(f[0])), accent, onAccent, tonal, text);
        sd.setListener(new SegmentView.Listener() { @Override public void onSelect(int i) { f[0] = dIds[i]; saveApp(pkg, f, m, sum); } });
        opts.addView(sd, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));
        TextView t2 = label(t("Тема"), 13, muted, 0);
        t2.setPadding(0, px(10), 0, 0);
        opts.addView(t2);
        SegmentView st = new SegmentView(this, new String[]{t("Общие"), t("Светлая"), t("Тёмная")}, Math.max(0, java.util.Arrays.asList(tIds).indexOf(f[1])), accent, onAccent, tonal, text);
        st.setListener(new SegmentView.Listener() { @Override public void onSelect(int i) { f[1] = tIds[i]; saveApp(pkg, f, m, sum); } });
        opts.addView(st, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));
        TextView t3 = label(t("Цвета клавиатуры"), 13, muted, 0);
        t3.setPadding(0, px(10), 0, px(4));
        opts.addView(t3);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        final LinearLayout chips = new LinearLayout(this);
        String[] all = new String[Theme.SCHEMES.length + 1];
        all[0] = "-";
        System.arraycopy(Theme.SCHEMES, 0, all, 1, Theme.SCHEMES.length);
        for (final String sch : all) {
            TextView c = label(sch.equals("-") ? "\u2715" : sch.equals("auto") ? "A" : "", 14, Color.WHITE, 2);
            c.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            int seed = sch.equals("-") ? 0 : Theme.seedOf(sch);
            g.setColor(seed != 0 ? seed : Color.rgb(120, 124, 134));
            if (sch.equals(f[2])) g.setStroke(px(3), text);
            c.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(38), px(38));
            lp.rightMargin = px(10);
            c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                f[2] = sch; saveApp(pkg, f, m, sum);
                for (int i = 0; i < chips.getChildCount(); i++) {
                    GradientDrawable gd = new GradientDrawable();
                    gd.setShape(GradientDrawable.OVAL);
                    String s2 = i == 0 ? "-" : Theme.SCHEMES[i - 1];
                    int sd2 = s2.equals("-") ? 0 : Theme.seedOf(s2);
                    gd.setColor(sd2 != 0 ? sd2 : Color.rgb(120, 124, 134));
                    if (s2.equals(f[2])) gd.setStroke(px(3), text);
                    chips.getChildAt(i).setBackground(gd);
                }
            } });
            chips.addView(c, lp);
        }
        hs.addView(chips);
        opts.addView(hs);
    }

    /** Включить языки в системном переключателе может только пользователь: открываем нужную страницу системных настроек. */
    private void openSystemLanguages() {
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS);
            i.putExtra(android.provider.Settings.EXTRA_INPUT_METHOD_ID, new android.content.ComponentName(getPackageName(), LightBoardIme.class.getName()).flattenToShortString());
            startActivity(i);
        } catch (RuntimeException e) { toast(t("Не получилось открыть системные настройки")); }
    }

    private void askShots() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            picking = true;
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 8);
        }
    }

    private void smsToggled() {
        if (prefs.getBoolean("sms", false) && checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            picking = true;
            requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, 7);
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        picking = false;
        if ((code == 7 || code == 8) && !(res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED)) {
            prefs.edit().putBoolean(code == 7 ? "sms" : "shots", false).apply();
            toast(t(code == 7 ? "Без доступа к СМС функция не работает" : "Без доступа к фото функция не работает"));
            showTab(false);
        }
    }

    // ---------- вкладка «Данные» ----------
    private void tabData() {
        content.addView(title(t("Данные")));

        content.addView(section(t("Личные слова (самообучение)")));
        LinearLayout c2 = card();
        personalWords(c2);
        content.addView(c2);

        content.addView(section(t("Буфер обмена")));
        LinearLayout c3 = card();
        row(c3, toggle(t("История буфера обмена"), null, "clip_hist", true, null));
        row(c3, toggle(t("Закрывать буфер обмена после вставки"), null, "clip_close", true, null));
        row(c3, slider(t("Хранить обычные записи, дней"), "clip_days", ClipStore.DEFAULT_DAYS, 1, 30, ""));
        row(c3, action(t("Очистить историю буфера"), null, new View.OnClickListener() { @Override public void onClick(View v) {
            prefs.edit().putLong("clip_clear", System.currentTimeMillis()).apply();
            setStatus(t("История буфера будет очищена при следующем открытии клавиатуры"));
        } }));
        content.addView(c3);

        content.addView(section(t("Резервная копия")));
        LinearLayout c4 = card();
        row(c4, toggle(t("Настройки приложения"), null, "bk_settings", true, null));
        row(c4, toggle(t("Словарь самообучения"), null, "bk_learn", true, null));
        row(c4, toggle(t("История буфера обмена"), null, "bk_clips", false, null));
        row(c4, toggle(t("Защитить паролем"), t("Пароль спрашивается при экспорте; без него копию не восстановить"), "bk_enc", false, null));
        if (can(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")))
            row(c4, action(t("Экспорт в файл"), null, new View.OnClickListener() { @Override public void onClick(View v) { doExport(); } }));
        if (can(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")))
            row(c4, action(t("Импорт из файла"), null, new View.OnClickListener() { @Override public void onClick(View v) { doImport(); } }));
        content.addView(c4);
    }

    // ---------- вкладка «О приложении» ----------
    private void tabAbout() {
        final Runnable shell = new Runnable() { @Override public void run() { buildShell(); } };
        content.addView(title(t("Ещё")));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(px(4), px(8), 0, px(8));
        head.addView(logo(72));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(px(18), 0, 0, 0);
        tt.addView(label("LightBoard", 28, text, 2));
        tt.addView(label("anlocky · Claude", 14, muted, 0));
        head.addView(tt);
        content.addView(head);

        content.addView(section(t("Приложение")));
        LinearLayout c1 = card();
        row(c1, action(t("Язык приложения") + ": " + langName(prefs.getString("app_lang", "auto")), null, new View.OnClickListener() { @Override public void onClick(View v) { appLangDialog(); } }));
        row(c1, segment(t("Тема"), "app_theme", "auto", shell, "auto", t("Авто"), "light", t("Светлая"), "dark", t("Тёмная")));
        row(c1, colorRow(t("Цвета приложения"), "app_palette", "auto", shell));
        content.addView(c1);
        TextView cap = label(t("Это касается только настроек"), 12, muted, 0);
        cap.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(cap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));

        content.addView(section(t("Значок")));
        LinearLayout c2 = card();
        final ComponentName alias = new ComponentName(getPackageName(), "com.lightboard.keyboard.LauncherAlias");
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(20), px(12), px(20), px(12));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(label(t("Иконка на рабочем столе"), 16, text, 0));
        r.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1f));
        boolean on = getPackageManager().getComponentEnabledSetting(alias) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        final SwitchView sw = new SwitchView(this, on, accent, onAccent, tonal, muted);
        sw.setListener(new SwitchView.Listener() { @Override public void onChanged(boolean enable) {
            getPackageManager().setComponentEnabledSetting(alias, enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.leftMargin = px(12);
        r.addView(sw, lp);
        pressable(r, new View.OnClickListener() { @Override public void onClick(View v) { sw.toggle(); } });
        c2.addView(r);
        content.addView(c2);

        content.addView(section(t("Поддержать проект")));
        LinearLayout c3 = card();
        row(c3, donateRow("Boosty", "https://boosty.to/anlocky/donate", "https://boosty.to/anlocky/donate", null, true));
        row(c3, donateRow(t("USDT (сеть TRC-20)"), "TVTsixvbheZMNhD46SJELECFEw1ba5xknh", "TVTsixvbheZMNhD46SJELECFEw1ba5xknh", QrView.TRC20, false));
        row(c3, donateRow(t("TON / USDT (сеть TON)"), "UQBKbiajQEHpBDEnwa9KRLcUs8mcl9fQpMA4xukmCCLqSXgn", "UQBKbiajQEHpBDEnwa9KRLcUs8mcl9fQpMA4xukmCCLqSXgn", QrView.TON, false));
        content.addView(c3);

        content.addView(section(t("Правовая информация")));
        LinearLayout cLaw = card();
        String lg = prefs.getString("app_lang", "auto");
        if (lg.equals("auto")) lg = java.util.Locale.getDefault().getLanguage();
        int li = lg.equals("ru") || lg.equals("uk") || lg.equals("be") ? 0 : 1; // русский и английский тексты
        legalItem(cLaw, t("Лицензионное соглашение"), Legal.EULA[li]);
        legalItem(cLaw, t("Политика конфиденциальности"), Legal.PRIVACY[li]);
        legalItem(cLaw, t("Сторонние компоненты и словари"), Legal.THIRD[li]);
        content.addView(cLaw);
    }

    /** Раскрывающийся документ: заголовок и текст, который плавно разворачивается. */
    private void legalItem(LinearLayout card, final String title, String body) {
        final LinearLayout head = (LinearLayout) action(title + "  ▾", null, null);
        final TextView ht = lastTitle;
        row(card, head);
        final TextView tv = label(body, 12, muted, 0);
        tv.setPadding(px(20), px(4), px(20), px(14));
        tv.setTextIsSelectable(true);
        tv.setVisibility(View.GONE);
        card.addView(tv);
        head.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            boolean open = tv.getVisibility() != View.VISIBLE;
            ht.setText(title + (open ? "  ▴" : "  ▾"));
            animateOpen(tv, open);
        } });
    }

    /** Строка «подпись, значение, кнопка копирования»; у кошельков ещё свёрнутый по умолчанию QR-код. */
    private View donateRow(String title, String shown, final String copy, String[] qr, boolean link) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(20), px(14), px(20), px(14));
        box.addView(label(title, 13, muted, 0));
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView v = label(shown, 14, text, 0);
        v.setTypeface(android.graphics.Typeface.MONOSPACE);
        v.setPadding(0, px(4), px(10), px(4));
        line.addView(v, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView btn = label(t("Копировать"), 14, onAccent, 1);
        btn.setPadding(px(14), px(8), px(14), px(8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(accent); g.setCornerRadius(px(18));
        btn.setBackground(g);
        btn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("LightBoard", copy));
            toast(t("Скопировано"));
        } });
        if (link) { // ссылка: рядом кнопка «Открыть», если на устройстве есть браузер
            final Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(copy));
            if (can(open)) {
                TextView ob = label(t("Открыть"), 14, accent, 1);
                ob.setPadding(px(10), px(8), px(10), px(8));
                ob.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) { try { startActivity(open); } catch (RuntimeException e) { toast(t("Не получилось открыть системные настройки")); } } });
                line.addView(ob);
            }
        }
        line.addView(btn);
        box.addView(line);
        if (qr != null) {
            final QrView q = new QrView(this, qr);
            q.setVisibility(View.GONE);
            final TextView tgl = label(t("Показать QR-код"), 14, accent, 1);
            tgl.setPadding(0, px(8), 0, px(4));
            tgl.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) {
                boolean show = q.getVisibility() != View.VISIBLE;
                q.setVisibility(show ? View.VISIBLE : View.GONE);
                tgl.setText(show ? t("Скрыть QR-код") : t("Показать QR-код"));
            } });
            box.addView(tgl);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.topMargin = px(6);
            box.addView(q, lp);
        }
        return box;
    }

    /** Логотип: квадрат с закруглёнными углами и буква L. */
    private View logo(int sizeDp) {
        View v = new View(this) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path path = new Path();
            @Override protected void onDraw(Canvas c) {
                float s = getWidth();
                p.setStyle(Paint.Style.FILL); p.setColor(accent);
                c.drawRoundRect(0, 0, s, s, s * 0.26f, s * 0.26f, p);
                p.setStyle(Paint.Style.STROKE); p.setColor(onAccent); p.setStrokeWidth(s * 0.13f);
                p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
                path.reset();
                path.moveTo(s * 0.37f, s * 0.24f); path.lineTo(s * 0.37f, s * 0.70f); path.lineTo(s * 0.69f, s * 0.70f);
                c.drawPath(path, p);
            }
        };
        v.setLayoutParams(new LinearLayout.LayoutParams(px(sizeDp), px(sizeDp)));
        return v;
    }

    // ---------- резервная копия: один файл ----------
    private void doExport() {
        if (!prefs.getBoolean("bk_settings", true) && !prefs.getBoolean("bk_learn", true) && !prefs.getBoolean("bk_clips", false)) { toast(t("Выберите, что сохранить")); return; }
        if (prefs.getBoolean("bk_enc", false)) askNewPassword(new PwCb() { @Override public void on(char[] pw) { pendingPw = pw; launchExport(true); } });
        else launchExport(false);
    }

    private void launchExport(boolean enc) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(enc ? "application/octet-stream" : "application/json")
                .putExtra(Intent.EXTRA_TITLE, enc ? "lightboard-backup.lbk" : "lightboard-backup.json");
        picking = true;
        startActivityForResult(i, REQ_EXPORT);
    }

    private char[] pendingPw; // пароль живёт только между вводом и записью файла

    private void wipePw() { if (pendingPw != null) { java.util.Arrays.fill(pendingPw, '\0'); pendingPw = null; } }

    interface PwCb { void on(char[] pw); }

    private EditText pwField(String hint) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setSingleLine(true);
        e.setHint(hint); e.setHintTextColor(muted); e.setTextColor(text);
        GradientDrawable g = new GradientDrawable();
        g.setColor(tonal); g.setCornerRadius(px(14));
        e.setBackground(g);
        e.setPadding(px(16), px(12), px(16), px(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(px(16), px(4), px(16), px(8));
        e.setLayoutParams(lp);
        return e;
    }

    private static char[] chars(EditText e) { char[] c = new char[e.length()]; e.getText().getChars(0, c.length, c, 0); return c; }

    /** Новый пароль: два поля, минимум 6 знаков. Окно то же, что у остальных настроек (Dlg). */
    private void askNewPassword(final PwCb cb) {
        final EditText p1 = pwField(t("Пароль")), p2 = pwField(t("Повторите пароль"));
        dlg().setTitle(t("Пароль копии")).setMessage(t("Придумайте пароль. Он понадобится при импорте; забыв его, копию не восстановить."))
                .addView(p1).addView(p2)
                .setNegativeButton(t("Отмена"), null)
                .setPositiveButton(t("Сохранить"), new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) {
                    char[] a = chars(p1), b = chars(p2);
                    if (a.length < 6) { java.util.Arrays.fill(a, '\0'); java.util.Arrays.fill(b, '\0'); toast(t("Пароль слишком короткий: минимум 6 символов")); askNewPassword(cb); return; }
                    if (!java.util.Arrays.equals(a, b)) { java.util.Arrays.fill(a, '\0'); java.util.Arrays.fill(b, '\0'); toast(t("Пароли не совпадают")); askNewPassword(cb); return; }
                    java.util.Arrays.fill(b, '\0');
                    cb.on(a);
                } }).show();
    }

    /** Копия зашифрована: спрашиваем пароль, расшифровываем в фоне (подбор ключа занимает около секунды) и применяем. */
    private void askPassword(final byte[] raw) {
        final EditText p = pwField(t("Пароль"));
        dlg().setTitle(t("Пароль копии")).setMessage(t("Копия зашифрована. Введите пароль.")).addView(p)
                .setNegativeButton(t("Отмена"), null)
                .setPositiveButton(t("Расшифровать"), new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) {
                    final char[] pw = chars(p);
                    toast(t("Проверяю пароль…"));
                    new Thread(new Runnable() { @Override public void run() {
                        try {
                            final byte[] plain = BackupCrypt.decrypt(raw, pw);
                            runOnUiThread(new Runnable() { @Override public void run() {
                                try { applyBackup(plain); } catch (IOException | JSONException | RuntimeException e) { toast(t("Ошибка: ") + e.getMessage()); }
                            } });
                        } catch (java.security.GeneralSecurityException e) {
                            runOnUiThread(new Runnable() { @Override public void run() { toast(t("Неверный пароль или файл повреждён")); } });
                        } finally { java.util.Arrays.fill(pw, '\0'); }
                    } }, "backup-dec").start();
                } }).show();
    }

    private void doImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        picking = true;
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        picking = false;
        if (res != RESULT_OK || data == null || data.getData() == null) { if (req == REQ_EXPORT) wipePw(); return; }
        try {
            if (req == REQ_EXPORT) writeBackup(data.getData()); else if (req == REQ_IMPORT) readBackup(data.getData()); else if (req == REQ_DICT) buildDict(data.getData());
        } catch (IOException | JSONException | RuntimeException e) { toast(t("Ошибка: ") + e.getMessage()); }
    }

    private static boolean transientKey(String k) {
        return k.startsWith("import_") || k.startsWith("forget") || k.startsWith("bk_") || k.equals("reset_words") || k.equals("clip_clear");
    }

    private void writeBackup(Uri uri) throws IOException, JSONException {
        JSONObject o = new JSONObject();
        o.put("app", "LightBoard"); o.put("v", 1);
        SharedPreferences st = keyboardState();
        if (prefs.getBoolean("bk_settings", true)) {
            JSONObject s = new JSONObject();
            for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
                if (transientKey(e.getKey())) continue;
                Object v = e.getValue();
                JSONObject x = new JSONObject();
                x.put("t", v instanceof Boolean ? "b" : v instanceof Integer ? "i" : v instanceof Long ? "l" : v instanceof Float ? "f" : "s");
                x.put("v", v instanceof Float ? (double) (Float) v : v);
                s.put(e.getKey(), x);
            }
            o.put("settings", s);
        }
        if (prefs.getBoolean("bk_learn", true)) {
            JSONObject l = new JSONObject();
            for (String k : new String[]{"learned", "pairs", "own", "user_words"}) l.put(k, st.getString(k, ""));
            o.put("learn", l);
        }
        if (prefs.getBoolean("bk_clips", false)) {
            ClipStore cs = new ClipStore();
            cs.loadFrom(new java.io.File(getFilesDir(), "clips.dat"));
            o.put("clips", cs.save());
        }
        final byte[] plain = o.toString().getBytes(StandardCharsets.UTF_8);
        final char[] pw = pendingPw;
        pendingPw = null;
        if (pw == null) { putBytes(uri, plain); toast(t("Сохранено")); return; }
        toast(t("Шифрую…"));
        final Uri target = uri;
        new Thread(new Runnable() { @Override public void run() {
            try {
                putBytes(target, BackupCrypt.encrypt(plain, pw));
                runOnUiThread(new Runnable() { @Override public void run() { toast(t("Сохранено")); } });
            } catch (final Exception e) {
                runOnUiThread(new Runnable() { @Override public void run() { toast(t("Ошибка: ") + e.getMessage()); } });
            } finally { java.util.Arrays.fill(pw, '\0'); }
        } }, "backup-enc").start();
    }

    private void putBytes(Uri uri, byte[] b) throws IOException {
        OutputStream os = getContentResolver().openOutputStream(uri, "wt"); // «wt»: на Android 10+ без него старый хвост файла может остаться
        if (os == null) throw new IOException(t("нет доступа к файлу"));
        try { os.write(b); } finally { os.close(); }
    }

    private void readBackup(Uri uri) throws IOException, JSONException {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException(t("нет доступа к файлу"));
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > 4_000_000) throw new IOException(t("файл слишком большой"));
            }
        } finally { in.close(); }
        byte[] raw = bo.toByteArray();
        if (BackupCrypt.isEncrypted(raw)) { askPassword(raw); return; }
        applyBackup(raw);
    }

    private void applyBackup(byte[] json) throws IOException, JSONException {
        JSONObject o = new JSONObject(new String(json, StandardCharsets.UTF_8));
        if (!"LightBoard".equals(o.optString("app"))) throw new IOException(t("это не копия LightBoard"));
        SharedPreferences.Editor ed = prefs.edit();
        StringBuilder got = new StringBuilder();
        if (prefs.getBoolean("bk_settings", true) && o.has("settings")) {
            JSONObject s = o.getJSONObject("settings");
            java.util.Iterator<String> it = s.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (transientKey(k)) continue;
                JSONObject x = s.optJSONObject(k);
                if (x != null) putChecked(ed, k, x.optString("t"), x);
            }
            got.append(t("настройки")).append(' ');
        }
        boolean later = false;
        JSONObject imp = new JSONObject(); // словарь и буфер принадлежат клавиатуре: кладём файл, она применит его и удалит
        if (prefs.getBoolean("bk_learn", true) && o.optJSONObject("learn") != null) { imp.put("learn", o.getJSONObject("learn")); got.append(t("словарь")).append(' '); later = true; }
        if (prefs.getBoolean("bk_clips", false) && o.has("clips")) { imp.put("clips", o.getString("clips")); got.append(t("буфер")).append(' '); later = true; }
        if (later) writeImport(imp.toString());
        ed.apply();
        toast(got.length() == 0 ? t("В файле нет выбранного") : t("Импортировано: ") + got.toString().trim() + (later ? t(". Словарь и буфер применятся при следующем открытии клавиатуры") : ""));
        buildShell();
    }

    private void writeImport(String json) throws IOException {
        java.io.File tmp = new java.io.File(getFilesDir(), "import.json.tmp"), f = new java.io.File(getFilesDir(), "import.json");
        try (OutputStream os = new java.io.FileOutputStream(tmp)) { os.write(json.getBytes(StandardCharsets.UTF_8)); }
        if (!tmp.renameTo(f)) throw new IOException("rename");
    }

    private static boolean in(String v, String... ok) { for (String s : ok) if (s.equals(v)) return true; return false; }

    private static int clampInt(JSONObject x, int lo, int hi) throws JSONException { return Math.max(lo, Math.min(hi, x.getInt("v"))); }

    /** В настройки попадает только известный ключ нужного типа и в допустимых границах: файл копии не может ни сломать клавиатуру, ни занести лишнее. */
    private static void putChecked(SharedPreferences.Editor ed, String k, String type, JSONObject x) throws JSONException {
        boolean b = type.equals("b"), i = type.equals("i"), s = type.equals("s");
        switch (k) {
            case "autofill": case "calc": case "clip_hist": case "split": case "sms": case "shots": case "incognito": case "smartdash": case "clip_close": case "i_blur": case "g_logo":
                if (b) ed.putBoolean(k, x.getBoolean("v")); return;
            case "clip_days": if (i) ed.putInt(k, clampInt(x, 1, 30)); return;
            case "i_blurDp": if (i) ed.putInt(k, clampInt(x, 0, 40)); return;
            case "i_panelPct": if (i) ed.putInt(k, clampInt(x, 30, 100)); return;
            case "g_micpos": if (s && in(x.getString("v"), "top", "bottom", "both")) ed.putString(k, x.getString("v")); return;
            case "design": if (s && in(x.getString("v"), "gboard", "ios")) ed.putString(k, x.getString("v")); return;
            case "onehand": if (s && in(x.getString("v"), "off", "left", "right")) ed.putString(k, x.getString("v")); return;
            case "app_lang": if (s && in(x.getString("v"), LANG_IDS)) ed.putString(k, x.getString("v")); return;
            case "apps": if (s && x.getString("v").matches("[A-Za-z0-9_.:;-]{0,8000}")) ed.putString(k, x.getString("v")); return;
            case "app_theme": if (s && in(x.getString("v"), "auto", "light", "dark")) ed.putString(k, x.getString("v")); return;
            case "app_palette": case "g_material": if (s && in(x.getString("v"), Theme.SCHEMES)) ed.putString(k, x.getString("v")); return;
            default: break;
        }
        if (!(k.startsWith("g_") || k.startsWith("i_"))) return;
        switch (k.substring(2)) {
            case "keybg": case "digits": case "words": case "langkey": case "globe": case "swipe": case "autocorrect": case "autospace":
            case "clip": case "spacecursor": case "clearall": case "haptic": case "sound":
            case "popup": case "trail": case "hintnum": case "hintsym": case "autocaps": case "autoperiod": case "bottombar": case "swapicons": case "mic":
                if (b) ed.putBoolean(k, x.getBoolean("v")); break;
            case "height": if (i) ed.putInt(k, clampInt(x, 80, 160)); break;
            case "round": case "soundvol2": if (i) ed.putInt(k, clampInt(x, 0, 100)); break;
            case "botpad": if (i) ed.putInt(k, clampInt(x, 0, 40)); break;
            case "font": if (i) ed.putInt(k, clampInt(x, 70, 140)); break;
            case "clearhold": if (i) ed.putInt(k, clampInt(x, 300, 1500)); break;
            case "theme": if (s && in(x.getString("v"), "auto", "light", "dark")) ed.putString(k, x.getString("v")); break;
            default: break;
        }
    }

    private void toast(String s) { Toast.makeText(this, t(s), Toast.LENGTH_LONG).show(); }

    // ---------- строительные блоки ----------
    private int px(float dp) { return Math.round(dp * d); }

    /** weight: 0 обычный, 1 средний, 2 жирный. */
    private TextView label(String s, float sp, int color, int weight) {
        TextView tv = new TextView(this);
        tv.setText(s); tv.setTextSize(sp); tv.setTextColor(color);
        if (weight == 2) tv.setTypeface(Typeface.DEFAULT_BOLD);
        else if (weight == 1) tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return tv;
    }

    private TextView title(String s) {
        TextView tv = label(s, 32, text, 2);
        tv.setPadding(px(4), px(8), 0, px(4));
        return tv;
    }

    private TextView section(String s) {
        TextView tv = label(s, 14, accent, 1);
        tv.setPadding(px(20), px(22), 0, px(8));
        return tv;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(card); g.setCornerRadius(px(28));
        l.setBackground(g);
        return l;
    }

    private void row(LinearLayout c, View v) {
        if (c.getChildCount() > 0) {
            View dv = new View(this);
            dv.setBackgroundColor(Theme.fade(line, 0.6f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, 1);
            lp.leftMargin = px(20); lp.rightMargin = px(20);
            c.addView(dv, lp);
        }
        c.addView(v);
    }

    private TextView rowText() {
        TextView tv = label("", 15, text, 0);
        tv.setPadding(px(20), px(14), px(20), px(14));
        return tv;
    }

    /** Нажатие подсвечивается мягким цветом гаммы. */
    private void pressable(View v, View.OnClickListener l) {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(Theme.fade(accent, 0.14f)));
        sl.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        v.setBackground(sl);
        v.setClickable(true);
        v.setOnClickListener(l);
    }

    /** Строка-действие: заголовок, пояснение и стрелка. Заголовок доступен через lastTitle. */
    private View action(String title, String sub, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(20), px(14), px(16), px(14));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView tv = label(title, 16, text, 0);
        col.addView(tv);
        if (sub != null) col.addView(label(sub, 12, muted, 0));
        r.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1f));
        r.addView(label("›", 22, muted, 0));
        lastTitle = tv;
        pressable(r, l);
        return r;
    }

    /** Шаг подключения: номер в кружке цвета гаммы и название. */
    private View step(int n, String title, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(16), px(12), px(16), px(12));
        TextView b = label(String.valueOf(n), 15, onAccent, 2);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL); g.setColor(accent);
        b.setBackground(g);
        r.addView(b, new LinearLayout.LayoutParams(px(32), px(32)));
        TextView tv = label(title, 16, text, 0);
        tv.setPadding(px(14), 0, 0, 0);
        r.addView(tv, new LinearLayout.LayoutParams(0, WRAP, 1f));
        r.addView(label("›", 22, muted, 0));
        pressable(r, l);
        return r;
    }

    private View toggle(String title, String sub, final String key, boolean def, final Runnable after) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(20), px(12), px(20), px(12));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(label(title, 16, text, 0));
        if (sub != null) col.addView(label(sub, 12, muted, 0));
        r.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1f));
        final SwitchView sw = new SwitchView(this, prefs.getBoolean(key, def), accent, onAccent, tonal, muted);
        sw.setListener(new SwitchView.Listener() { @Override public void onChanged(boolean on) { prefs.edit().putBoolean(key, on).apply(); if (after != null) hd.postDelayed(after, 230); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.leftMargin = px(12);
        r.addView(sw, lp);
        pressable(r, new View.OnClickListener() { @Override public void onClick(View v) { sw.toggle(); } });
        return r;
    }

    private View slider(String title, final String key, int def, final int min, int max, final String unit) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(20), px(12), px(20), px(6));
        LinearLayout top = new LinearLayout(this);
        final TextView val = label("", 15, muted, 0);
        top.addView(label(title, 16, text, 0), new LinearLayout.LayoutParams(0, WRAP, 1f));
        top.addView(val);
        col.addView(top);
        int cur = prefs.getInt(key, def);
        val.setText(cur + t(unit));
        SliderView sb = new SliderView(this, min, max, cur, accent, tonal);
        sb.setListener(new SliderView.Listener() {
            @Override public void onValue(int v) { val.setText(v + t(unit)); }
            @Override public void onStop(int v) { prefs.edit().putInt(key, v).apply(); }
        });
        col.addView(sb, new LinearLayout.LayoutParams(MATCH, WRAP));
        return col;
    }

    /** Группа кнопок-таблеток с плавным переездом: пары «значение, подпись»; after (если не null) вызывается после выбора. */
    private View segment(String title, final String key, final String def, final Runnable after, final String... pairs) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(20), px(12), px(20), px(14));
        col.addView(label(title, 16, text, 0));
        int n = pairs.length / 2, sel = 0;
        String cur = prefs.getString(key, def);
        String[] labs = new String[n];
        for (int i = 0; i < n; i++) { labs[i] = pairs[2 * i + 1]; if (pairs[2 * i].equals(cur)) sel = i; }
        SegmentView seg = new SegmentView(this, labs, sel, accent, onAccent, tonal, text);
        seg.setListener(new SegmentView.Listener() { @Override public void onSelect(int i) { prefs.edit().putString(key, pairs[2 * i]).apply(); if (after != null) hd.postDelayed(after, 280); } }); // пересборка экрана — когда таблетка уже доехала
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = px(10);
        col.addView(seg, lp);
        return col;
    }

    /** Цветовые кружки схем: если не помещаются — прокручиваются вбок; «авто» берёт цвета системы. */
    private View colorRow(String title, final String key, String def, final Runnable after) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(20), px(12), 0, px(14));
        col.addView(label(title, 16, text, 0));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, px(10), px(20), 0);
        String cur = prefs.getString(key, def);
        for (final String sch : Theme.SCHEMES) {
            TextView c = label(sch.equals("auto") ? "A" : "", 14, Color.WHITE, 2);
            c.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            int seed = Theme.seedOf(sch);
            g.setColor(seed != 0 ? seed : Color.rgb(120, 124, 134));
            if (sch.equals(cur)) g.setStroke(px(3), text);
            c.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(40), px(40));
            lp.rightMargin = px(10);
            c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { prefs.edit().putString(key, sch).apply(); after.run(); } });
            chips.addView(c, lp);
        }
        hs.addView(chips);
        col.addView(hs);
        return col;
    }

    // ---------- личные слова ----------
    @SuppressWarnings("deprecation")
    private SharedPreferences keyboardState() { return getSharedPreferences("lbs", MODE_PRIVATE | MODE_MULTI_PROCESS); } // файл, который ведёт клавиатура

    /** Свёрнутый список «своих» слов с поиском: слова, которых нет в словаре языка и которые набраны не меньше двух раз, плюс «не исправлять». */
    private void personalWords(final LinearLayout c2) {
        SharedPreferences st = keyboardState();
        long done = st.getLong("forget_done", 0L), stamp = prefs.getLong("forget_stamp", 0L);
        HashSet<String> pending = new HashSet<>(); // слова, которые уже попросили забыть, но клавиатура ещё не открывалась
        if (stamp > done) { for (String w : prefs.getString("forget", "").split(" ")) if (!w.isEmpty()) pending.add(w); }
        else if (!prefs.getString("forget", "").isEmpty()) prefs.edit().remove("forget").apply();
        HashSet<String> own = new HashSet<>();
        for (String w : st.getString("own", "").split(" ")) if (!w.isEmpty()) own.add(w);
        final ArrayList<String[]> items = new ArrayList<>();
        ArrayList<String[]> counted = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (String w : st.getString("user_words", "").split(" ")) if (!w.isEmpty() && !pending.contains(w) && seen.add(w)) items.add(new String[]{w, "0"}); // «0» — слово из «не исправлять»
        for (String e : st.getString("learned", "").split(" ")) {
            int i = e.lastIndexOf(':');
            if (i < 1) continue;
            String w = e.substring(0, i);
            if (pending.contains(w) || !own.contains(w)) continue;
            int n;
            try { n = Integer.parseInt(e.substring(i + 1)); } catch (NumberFormatException ex) { continue; }
            if (n >= 2 && seen.add(w)) counted.add(new String[]{w, String.valueOf(n)});
        }
        Collections.sort(counted, new java.util.Comparator<String[]>() { @Override public int compare(String[] a, String[] b) { return Integer.parseInt(b[1]) - Integer.parseInt(a[1]); } });
        items.addAll(counted);

        final LinearLayout head = (LinearLayout) action(t("Личные слова") + ": " + items.size() + (wordsOpen ? "  ▴" : "  ▾"), null, null);
        final TextView ht = lastTitle;
        final int total = items.size();
        row(c2, head);
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        c2.addView(body);
        body.setVisibility(wordsOpen ? View.VISIBLE : View.GONE);
        if (wordsOpen) buildWords(body, items);
        head.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            wordsOpen = !wordsOpen;
            ht.setText(t("Личные слова") + ": " + total + (wordsOpen ? "  ▴" : "  ▾"));
            if (wordsOpen && body.getChildCount() == 0) buildWords(body, items);
            animateOpen(body, wordsOpen);
        } });
    }

    private void buildWords(LinearLayout body, final ArrayList<String[]> items) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText q = new EditText(this);
        q.setHint(t("Поиск слова")); q.setHintTextColor(muted); q.setTextColor(text);
        q.setBackground(null); q.setSingleLine(true); q.setText(wordsFilter);
        q.setPadding(px(20), px(12), px(20), px(12));
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { wordsFilter = e.toString(); fillWords(box, items); }
        });
        row(body, q);
        body.addView(box);
        fillWords(box, items);
        row(body, action(t("Сбросить все личные слова"), null, new View.OnClickListener() { @Override public void onClick(View v) {
            dlg().setTitle(t("Сбросить все личные слова?"))
                .setMessage(t("Будут удалены все запомненные слова и сочетания. Это произойдёт при следующем открытии клавиатуры."))
                .setPositiveButton(t("Сбросить"), new android.content.DialogInterface.OnClickListener() { @Override public void onClick(android.content.DialogInterface dd, int x) {
                    prefs.edit().putLong("reset_words", System.currentTimeMillis()).apply();
                    setStatus(t("Личные слова будут сброшены при следующем открытии клавиатуры"));
                } })
                .setNegativeButton(t("Отмена"), null).show();
        } }));
    }

    private void fillWords(LinearLayout box, ArrayList<String[]> items) {
        box.removeAllViews();
        String f = wordsFilter.trim().toLowerCase(java.util.Locale.ROOT);
        int shown = 0, total = 0;
        for (String[] it : items) {
            if (!f.isEmpty() && !it[0].contains(f)) continue;
            total++;
            if (shown >= 200) continue;
            shown++;
            final String w = it[0];
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(px(20), px(12), px(20), px(12));
            r.addView(label(w, 16, text, 0), new LinearLayout.LayoutParams(0, WRAP, 1f));
            r.addView(label(it[1].equals("0") ? t("не исправлять") : "×" + it[1], 13, muted, 0));
            pressable(r, new View.OnClickListener() { @Override public void onClick(View v) {
                dlg().setTitle(t("Забыть «") + w + "»?")
                    .setMessage(t("Слово перестанет считаться вашим, пропадёт из запомненных сочетаний."))
                    .setPositiveButton(t("Забыть"), new android.content.DialogInterface.OnClickListener() { @Override public void onClick(android.content.DialogInterface dd, int x) { forget(w); } })
                    .setNegativeButton(t("Отмена"), null).show();
            } });
            box.addView(r);
        }
        if (total > shown) { TextView more = rowText(); more.setText(t("…и ещё ") + (total - shown)); more.setTextColor(muted); box.addView(more); }
    }

    /** Просит клавиатуру забыть слово (она применит это при следующем открытии: личные данные ведёт её процесс). */
    private void forget(String w) {
        SharedPreferences st = keyboardState();
        String cur = prefs.getLong("forget_stamp", 0L) > st.getLong("forget_done", 0L) ? prefs.getString("forget", "") : "";
        if (cur.length() > 3000) cur = "";
        prefs.edit().putString("forget", (cur + " " + w).trim()).putLong("forget_stamp", System.currentTimeMillis()).apply();
        showTab(false);
    }

    private void setStatus(final String s) {
        runOnUiThread(() -> { if (status != null && status.isShown()) status.setText(s); else Toast.makeText(this, s, Toast.LENGTH_LONG).show(); });
    }

    /** Словарь из текстового файла (список «слово частота», как в репозитории FrequencyWords). Язык берётся из имени (ru_full.txt), иначе спрашиваем. */
    private void importDict() {
        if (busy) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/*");
        picking = true;
        startActivityForResult(i, REQ_DICT);
    }

    private void buildDict(final Uri uri) {
        String name = "";
        try (android.database.Cursor c = getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (RuntimeException ignored) { }
        java.util.regex.Matcher mm = java.util.regex.Pattern.compile("^([a-z]{2,3}(?:_[a-z]{2})?)[_-]").matcher(name.toLowerCase(java.util.Locale.ROOT));
        String code = mm.find() ? dictCode(mm.group(1)) : null;
        if (code != null) {
            Layouts.L l = Layouts.byLang(code);
            if (l != null) { startBuild(uri, l); return; }
            if (code.equals("ja") || code.equals("ko") || code.equals("zh")) { setStatus(t("Японский, корейский и китайский требуют особого способа ввода: такие словари пока не поддерживаются")); return; }
        }
        detectAndAsk(uri); // кода в имени нет или язык незнакомый: смотрим на сами слова
    }

    /** Код языка из имени файла FrequencyWords: pt_br -> pt, zh_cn -> zh, ze_en (субтитры) -> en. */
    private static String dictCode(String c) {
        if (c.equals("ze_en")) return "en";
        if (c.equals("ze_zh")) return "zh";
        int u = c.indexOf('_');
        return u > 0 ? c.substring(0, u) : c;
    }

    /** Читает начало файла в фоне, определяет язык и просит подтвердить; не уверены — показывает весь список языков. */
    private void detectAndAsk(final Uri uri) {
        busy = true;
        setStatus(t("Определяю язык словаря…"));
        new Thread(new Runnable() { @Override public void run() {
            final java.util.ArrayList<String> w = new java.util.ArrayList<>(400);
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(getContentResolver().openInputStream(uri), StandardCharsets.UTF_8), 1 << 14)) {
                String line;
                while (w.size() < 400 && (line = br.readLine()) != null) {
                    int sp = line.indexOf(' ');
                    String x = (sp < 0 ? line : line.substring(0, sp)).trim().toLowerCase(java.util.Locale.ROOT);
                    if (!x.isEmpty() && x.length() <= 40) w.add(x);
                }
            } catch (Exception ignored) { }
            final String g = LangDetect.detect(w);
            runOnUiThread(new Runnable() { @Override public void run() {
                busy = false;
                setStatus("");
                final Layouts.L l = g == null ? null : Layouts.byLang(g);
                if (l == null) { pickLang(uri); return; }
                dlg().setTitle(l.name).setMessage(t("Похоже, это словарь этого языка. Верно?"))
                        .setPositiveButton(t("Да"), new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int x) { startBuild(uri, l); } })
                        .setNegativeButton(t("Другой язык"), new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int x) { pickLang(uri); } }).show();
            } });
        } }, "lex-detect").start();
    }

    /** Все языки, для которых есть раскладка, по алфавиту названий. */
    private void pickLang(final Uri uri) {
        final String[] ids = Layouts.supportedIds(), nm = new String[ids.length];
        Integer[] ord = new Integer[ids.length];
        for (int k = 0; k < ids.length; k++) { Layouts.L x = Layouts.byLang(ids[k]); nm[k] = x != null ? x.name : ids[k]; ord[k] = k; }
        java.util.Arrays.sort(ord, new java.util.Comparator<Integer>() { @Override public int compare(Integer a, Integer b) { return nm[a].compareToIgnoreCase(nm[b]); } });
        final String[] names = new String[ids.length], sid = new String[ids.length];
        for (int k = 0; k < ord.length; k++) { names[k] = nm[ord[k]]; sid[k] = ids[ord[k]]; }
        dlg().setTitle(t("Для какого языка этот словарь?")).setSingleChoice(names, -1, new Pick() { @Override public void on(int k) { Layouts.L l = Layouts.byLang(sid[k]); if (l != null) startBuild(uri, l); } })
                .setNegativeButton(t("Отмена"), null).show();
    }

    private void startBuild(final Uri uri, final Layouts.L l) {
        busy = true;
        setStatus(t("Строю словарь…"));
        new Thread(new Runnable() { @Override public void run() {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                final int cnt = LexBuilder.build(l.id, in, 150000, Lexicon.file(SettingsActivity.this, l.id)); // один файл на язык: словарь одного языка не попадёт в другой
                prefs.edit().putLong("lex_ver", System.currentTimeMillis()).apply(); // клавиатура по этому числу узнаёт, что словари изменились
                runOnUiThread(new Runnable() { @Override public void run() {
                    setStatus(l.name + ": " + cnt + " " + t("слов"));
                    showTab(false);
                    if (!SysLangs.enabled(SettingsActivity.this, null).contains(l)) offerEnable(l);
                } });
            } catch (Exception e) {
                setStatus(t("Не получилось: ") + e.getMessage());
            } finally {
                busy = false;
            }
        } }, "lex-import").start();
    }

    /** Словарь есть, а раскладки этого языка нет в списке включённых: предлагаем включить. */
    private void offerEnable(Layouts.L l) {
        dlg().setTitle(l.name).setMessage(t("Раскладка не включена в системе. Включить?"))
            .setPositiveButton(t("Включить"), new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) { openSystemLanguages(); } })
            .setNegativeButton(t("Позже"), null).show();
    }

    /** Какие словари импортированы и сколько в них слов. */
    private String dictInfo() {
        StringBuilder sb = new StringBuilder();
        String[] fl = getFilesDir().list();
        if (fl == null) return "";
        java.util.Arrays.sort(fl);
        for (String f : fl) {
            if (!f.startsWith("lex_") || !f.endsWith(".lex")) continue;
            String id = f.substring(4, f.length() - 4);
            Layouts.L l = Layouts.byLang(id);
            sb.append(sb.length() > 0 ? "\n" : "").append(l != null ? l.name : id).append(" — ").append(Lexicon.count(this, id)).append(' ').append(t("слов"));
        }
        return sb.toString();
    }
}

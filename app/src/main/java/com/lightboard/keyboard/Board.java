package com.lightboard.keyboard;

import android.view.View;

/** То, что нужно сервису клавиатуры от любого дизайна. Логика ввода общая, различается только отрисовка. */
interface Board {
    /** Пункты меню над клавишами. */
    int MENU_SETTINGS = 0, MENU_ONEHAND = 1, MENU_SPLIT = 2, MENU_CLIP = 3;

    View view();
    void setListener(KeyboardView.Listener l);
    void setSwipe(SwipeDecoder s);
    void setSwipeEnabled(boolean on);
    void setHaptic(boolean on);
    void setLayout(Layouts.L lay);
    void setPage(int p);
    int getPage();
    void setShift(int s);
    void setSuggestions(String a, String b, String c);
    void setClip(String s);
    void setClipImage(android.graphics.Bitmap b); // миниатюра на чипе (скриншот, картинка), null — обычный чип
    void setEnterLabel(String s);
    void setNumRow(boolean v);
    void setGlobeMode(boolean g);
    void setShowWords(boolean v);
    void setHeightScale(float s);
    void setSpaceCursor(boolean on);
    void setClearAll(boolean on, int holdMs);
    void setListening(boolean on);
    void setBottomBar(boolean v);
    void setNumpad(int mode);         // 0 обычная клавиатура, 1 число, 2 телефон, 3 дата/время: только цифровая клавиатура
    void setLangKey(boolean on);      // отдельная клавиша языка рядом с эмодзи (забирает часть пробела)
    void setTablet(boolean on);       // планшетная раскладка (отдельно от телефонной)
    void setRound(int v);             // закругление клавиш 0..100, 50 — как раньше
    void setStripHidden(boolean h);   // чипы автозаполнения закрыли полосу подсказок
    int[] strip();                    // {слева, справа, высота} полосы подсказок в пикселях (для автозаполнения)
    void setKeyBg(boolean on);        // подложки клавиш (выключены — клавиша видна только при нажатии)
    void setSwapIcons(boolean on);    // Cupertino с нижней панелью: поменять местами значки языка/эмодзи и микрофона
    boolean healthy();                // вид собран и готов к отрисовке
    void setTextScale(float s);       // планшет: буквы, цифры и символы крупнее и плотнее
    void setBottomPad(int dp);        // дополнительный отступ под клавишами
    void setFontScale(float f);       // размер знаков на клавишах (1 — как было)
    void setMic(boolean on);          // кнопка микрофона вообще (на панели подсказок и на нижней панели)
    void setIncognito(boolean allowed, boolean active); // удержание пробела включает инкогнито; значок на пробеле, пока оно включено
    void setHints(boolean num, boolean sym); // мелкие подсказки (цифры, символы) в углу букв и в меню по удержанию
    void setToolsOpen(boolean open);  // меню над клавишами: открыто, пока ничего не набирают
    void setPopup(boolean on);        // увеличенная буква над нажатой клавишей
    void setLine(boolean on);         // линия при непрерывном вводе
    void setLogo(boolean on);         // значок L на пробеле (Material)
    void setMicPos(int p);            // Material с нижней панелью: микрофон 0 сверху, 1 снизу, 2 везде
    void setSplit(boolean on);        // разделённая клавиатура
    void setOneHand(int mode);        // 0 выкл, 1 слева, 2 справа: только для подсветки пункта меню
    void playEntrance();
    void themeChanged();
    void cancelTouches();
}

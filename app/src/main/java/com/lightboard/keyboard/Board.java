package com.lightboard.keyboard;

import android.view.View;

interface Board {

    int MENU_ONEHAND = 1, MENU_SPLIT = 2, MENU_CLIP = 3;

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
    void setUndo(String label, boolean redo);
    void setMenuSwap(boolean on);
    void setClipImage(android.graphics.Bitmap b);
    void setEnterLabel(String s);
    void setNumRow(boolean v);
    void setGlobeMode(boolean g);
    void setShowWords(boolean v);
    void setHeightScale(float s);
    void setSpaceCursor(boolean on);
    void setSpaceLang(boolean on, boolean vertical);
    void setClearAll(boolean on, int holdMs);
    void setListening(boolean on);
    void setBottomBar(boolean v);
    void setNumpad(int mode);
    void setLangKey(boolean on);
    void setTablet(boolean on);
    void setRound(int v);
    void setStripHidden(boolean h);
    int[] strip();
    int lastTouch(char[] ch, float[] d2);
    void setKeyBg(boolean on);
    void setSwapIcons(boolean on);
    boolean healthy();
    void setTextScale(float s);
    void setBottomPad(int dp);
    void setFontScale(float f);
    void setBold(boolean on);
    void setMic(boolean on);
    void setIncognito(boolean allowed, boolean active);
    void setHints(boolean num, boolean sym);
    void setToolsOpen(boolean open);
    void setPopup(boolean on);
    void setLine(boolean on);
    void setLogo(boolean on);
    void setMicPos(int p);
    void setSplit(boolean on);
    void setOneHand(int mode);
    void playEntrance();
    void themeChanged();
    void cancelTouches();
}

package com.lightboard.keyboard;

/**
 * Анимация меню по удержанию (знаки у точки, варианты буквы): тело «выпрыгивает» с лёгким перелётом от клавиши,
 * пункты появляются волной от якоря, подсветка выбранного пункта плавно переезжает от пункта к пункту и «пульсирует».
 * Общая для обоих дизайнов; состояния только числа, на кадр ничего не создаётся.
 */
final class MenuFx {
    /** Длительность появления и скрытия меню, мс (menuP растёт 0..1 за OPEN мс). */
    static final float OPEN = 260f, CLOSE = 120f;

    float hx, hy;    // центр подсветки
    float pulse;     // 1 -> 0 после смены выбранного пункта
    private int last = -2;

    void reset() { last = -2; pulse = 0f; }

    /** Шаг подсветки за dt мс; tx, ty — центр выбранной ячейки. true — нужна ещё перерисовка. */
    boolean step(float dt, boolean open, int sel, float tx, float ty) {
        if (!open) { last = -2; pulse = 0f; return false; }
        boolean a = false;
        if (sel != last) {
            if (sel >= 0) { if (last < 0) { hx = tx; hy = ty; } pulse = 1f; }
            last = sel;
        }
        if (sel >= 0) {
            float k = Math.min(1f, dt / 55f);
            hx += (tx - hx) * k; hy += (ty - hy) * k;
            if (Math.abs(tx - hx) > 0.4f || Math.abs(ty - hy) > 0.4f) a = true; else { hx = tx; hy = ty; }
        }
        if (pulse > 0f) { pulse = Math.max(0f, pulse - dt / 190f); a = true; }
        return a;
    }

    /** Масштаб тела меню: при открытии — с перелётом, при закрытии — быстро вниз. */
    static float body(float menuP, boolean open) { return open ? Anim.back(Math.min(1f, menuP * 1.6f)) : Anim.ease(menuP); }

    /** Прогресс пункта 0..1: при открытии волной от якоря (order — расстояние от него в ячейках), при закрытии все вместе. */
    static float item(float menuP, boolean open, int order) {
        if (!open) return 1f;
        return Anim.clamp((menuP - 0.18f - 0.07f * order) / 0.42f, 0f, 1f);
    }

    /** Размер подсветки: короткий «толчок» при смене пункта. */
    float hlScale() { float p = pulse; return 1f + 0.18f * p * (2f - p); }
}

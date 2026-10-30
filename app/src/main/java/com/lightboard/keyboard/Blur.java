package com.lightboard.keyboard;

import android.annotation.TargetApi;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.view.Window;
import android.view.WindowManager;
import java.util.function.Consumer;

/**
 * Настоящее размытие содержимого за клавиатурой: его делает системный композитор (SurfaceFlinger),
 * поэтому в памяти процесса клавиатуры это ничего не стоит. Работает на Android 12+ (API 31),
 * если на устройстве включено «размытие окон» (не работает в режиме экономии, на слабых девайсах).
 * Класс грузится только на API >= 31.
 */
@TargetApi(31)
final class Blur {
    interface Callback { void onBlur(boolean on); }

    private final InputMethodService svc;
    private final WindowManager wm;
    private final Callback cb;
    private boolean wanted = true;
    private int radiusDp = 20;
    private boolean attached;
    private int lastOn = -1, lastDp = -1;
    private final Consumer<Boolean> listener = new Consumer<Boolean>() {
        @Override public void accept(Boolean on) { apply(); }
    };

    Blur(InputMethodService s, Callback cb) {
        this.svc = s; this.cb = cb;
        this.wm = (WindowManager) s.getSystemService(android.content.Context.WINDOW_SERVICE);
    }

    void setWanted(boolean w, int radiusDp) { wanted = w; this.radiusDp = radiusDp; apply(); }

    void attach() {
        if (attached || wm == null) return;
        wm.addCrossWindowBlurEnabledListener(svc.getMainExecutor(), listener);
        attached = true;
        apply();
    }

    void detach() {
        if (!attached || wm == null) return;
        wm.removeCrossWindowBlurEnabledListener(listener);
        attached = false;
    }

    private void apply() {
        Window w = svc.getWindow().getWindow();
        if (w == null || wm == null) return;
        boolean on = wanted && wm.isCrossWindowBlurEnabled();
        int key = on ? 1 : 0, dp = on ? radiusDp : 0;
        if (key == lastOn && dp == lastDp) { cb.onBlur(on); return; }
        lastOn = key; lastDp = dp;
        float den = svc.getResources().getDisplayMetrics().density;
        if (on) {
            // почти прозрачный фон со скруглённым верхом: система обрезает размытие по его форме
            float r = 16 * den;
            GradientDrawable g = new GradientDrawable();
            g.setColor(Color.argb(1, 255, 255, 255));
            g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            w.setBackgroundDrawable(g);
        } else {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
        w.setBackgroundBlurRadius(on && radiusDp > 0 ? (int) (radiusDp * den) : 0);
        cb.onBlur(on);
    }
}

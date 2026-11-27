package com.lightboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

final class PopOverlay extends View {
    private Board owner;
    private float dy;

    PopOverlay(Context c) { super(c); }

    void set(Board b, int heightPx) { owner = b; dy = heightPx; }

    @Override protected void onDraw(Canvas c) {
        if (owner == null) return;
        c.translate(0f, dy);
        owner.drawPops(c);
    }
}

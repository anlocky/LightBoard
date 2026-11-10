package com.lightboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** A chevron that points down when closed and turns up (animated) when open. */
final class ChevronView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private boolean open, inited;

    ChevronView(Context c, int color, float dp) {
        super(c);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(2f * dp);
        p.setColor(color);
    }

    /** The first call sets the state at once; later calls rotate the chevron with the same easing as the list. */
    void setOpen(boolean o) {
        if (!inited) { inited = true; open = o; setRotation(o ? 180f : 0f); return; }
        if (open == o) return;
        open = o;
        animate().cancel();
        animate().rotation(o ? 180f : 0f).setDuration(260).setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h) * 0.2f, cx = w / 2f, cy = h / 2f;
        path.reset();
        path.moveTo(cx - s * 1.5f, cy - s * 0.75f);
        path.lineTo(cx, cy + s * 0.75f);
        path.lineTo(cx + s * 1.5f, cy - s * 0.75f);
        c.drawPath(path, p);
    }
}

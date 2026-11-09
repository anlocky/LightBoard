package com.lightboard.keyboard;

import android.content.Context;
import android.os.Bundle;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InlineSuggestion;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.inline.InlineContentView;
import android.widget.inline.InlinePresentationSpec;

import androidx.autofill.inline.UiVersions;
import androidx.autofill.inline.common.TextViewStyle;
import androidx.autofill.inline.common.ViewStyle;
import androidx.autofill.inline.v1.InlineSuggestionUi;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

final class AutofillBar {
    interface Cb { void shown(boolean on); }

    private final Context ctx;
    private final FrameLayout root;
    private final Cb cb;
    private HorizontalScrollView bar;
    private LinearLayout row;
    private boolean has;
    private int gen;

    AutofillBar(Context c, FrameLayout root, Cb cb) { ctx = c; this.root = root; this.cb = cb; }

    static InlineSuggestionsRequest request(int heightPx, float den, int textColor, int hintColor) {
        Size min = new Size((int) (60 * den), (int) (32 * den)), max = new Size((int) (260 * den), Math.max(heightPx, (int) (36 * den)));

        ViewStyle chip = new ViewStyle.Builder().setBackgroundColor(android.graphics.Color.TRANSPARENT).setPadding((int) (10 * den), 0, (int) (10 * den), 0).build();
        InlineSuggestionUi.Style style = InlineSuggestionUi.newStyleBuilder()
                .setChipStyle(chip).setSingleIconChipStyle(chip)
                .setTitleStyle(new TextViewStyle.Builder().setTextColor(textColor).setTextSize(15f).build())
                .setSubtitleStyle(new TextViewStyle.Builder().setTextColor(hintColor).setTextSize(12f).build())
                .build();
        Bundle styles = UiVersions.newStylesBuilder().addStyle(style).build();
        InlinePresentationSpec spec = new InlinePresentationSpec.Builder(min, max).setStyle(styles).build();
        return new InlineSuggestionsRequest.Builder(Collections.singletonList(spec)).setMaxSuggestionCount(6).build();
    }

    boolean has() { return has; }

    void show(List<InlineSuggestion> list, int[] strip, FrameLayout.LayoutParams board) {
        if (list == null || list.isEmpty()) { clear(); return; }
        if (bar == null) {
            bar = new HorizontalScrollView(ctx);
            bar.setHorizontalScrollBarEnabled(false);
            bar.setOverScrollMode(View.OVER_SCROLL_NEVER);
            row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            bar.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, strip[2]));
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bar.getLayoutParams();
        lp.width = board.width; lp.height = strip[2]; lp.leftMargin = strip[0]; lp.rightMargin = strip[1];
        lp.gravity = (board.gravity == -1 ? Gravity.START : board.gravity) | Gravity.TOP;
        bar.setLayoutParams(lp);
        row.removeAllViews();
        final int g = ++gen;
        final float den = ctx.getResources().getDisplayMetrics().density;
        Size wrap = new Size(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        for (InlineSuggestion s : list) {
            s.inflate(ctx, wrap, ctx.getMainExecutor(), new Consumer<InlineContentView>() {
                @Override public void accept(InlineContentView v) {
                    if (v == null || bar == null || g != gen) return;

                    ViewGroup.LayoutParams own = v.getLayoutParams();
                    LinearLayout.LayoutParams p = own != null ? new LinearLayout.LayoutParams(own.width, own.height)
                            : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    p.setMargins(0, 0, (int) (6 * den), 0);
                    row.addView(v, p);
                    if (!has) { has = true; bar.setVisibility(View.VISIBLE); cb.shown(true); }
                }
            });
        }
    }

    void setHidden(boolean hidden) {
        if (bar != null) bar.setVisibility(has && !hidden ? View.VISIBLE : View.INVISIBLE);
    }

    void clear() {
        gen++;
        if (bar != null) { root.removeView(bar); bar = null; row = null; }
        if (has) { has = false; cb.shown(false); }
    }
}

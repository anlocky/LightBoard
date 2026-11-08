package com.lightboard.keyboard;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class Legal {
    private Legal() { }

    static String text(Context c, int ru, int en, int li) {
        try (InputStream in = c.getResources().openRawResource(li == 0 ? ru : en)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream(4096);
            byte[] b = new byte[4096];
            for (int n; (n = in.read(b)) > 0; ) o.write(b, 0, n);
            return o.toString("UTF-8");
        } catch (IOException e) {
            return "";
        }
    }
}

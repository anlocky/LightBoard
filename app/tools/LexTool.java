package com.lightboard.keyboard;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class LexTool {
    private LexTool() { }

    public static void main(String[] a) throws IOException {
        if (a.length != 3) { System.err.println("usage: LexTool <lang> <words.txt> <outDir>"); System.exit(2); }
        System.out.println(pack(a[0], new File(a[1]), new File(a[2])));
    }

    public static String pack(String lang, File txt, File outDir) throws IOException {
        if (!lang.matches("[a-z]{2,3}(-[A-Za-z0-9]{2,8})?")) throw new IOException("bad language id: " + lang);
        int script = script(txt);
        if (script == 0) throw new IOException("no letters of a known script in " + txt.getName());
        if (!outDir.isDirectory() && !outDir.mkdirs()) throw new IOException("cannot create " + outDir);
        File out = new File(outDir, "lex_" + lang + ".lex");
        int n;
        try (InputStream in = new FileInputStream(txt)) { n = LexBuilder.build(script, in, 150000, out); }
        return lang + ": " + n + " words, " + out.length() / 1024 + " KB -> " + out.getPath();
    }

    private static int script(File txt) throws IOException {
        int[] cnt = new int[16];
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(txt), "UTF-8"))) {
            String line;
            for (int i = 0; i < 3000 && (line = br.readLine()) != null; i++) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int s = LexBuilder.sc(line.charAt(0));
                if (s > 0 && s < 15) cnt[s]++;
            }
        }
        int best = 0;
        for (int s = 1; s < 15; s++) if (cnt[s] > cnt[best]) best = s;
        return best;
    }
}

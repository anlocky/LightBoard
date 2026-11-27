package com.lightboard.keyboard;

import static org.junit.Assert.*;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;

public class LearnStoreTest {
    @Test public void packAndReadRoundTrip() throws Exception {
        String[] v = {"привет:5 hello:3", "hello>world:2", "мойслово", "привет hello"};
        File f = File.createTempFile("learn", ".dat");
        try {
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(LearnStore.pack(v)); }
            String[] r = LearnStore.read(f);
            assertNotNull(r);
            assertEquals(4L, (long) r.length);
            for (int i = 0; i < 4; i++) assertEquals(v[i], r[i]);
        } finally { f.delete(); }
    }

    @Test public void missingOrBrokenFileGivesNull() throws Exception {
        File f = File.createTempFile("learn", ".dat");
        try {
            assertNull(LearnStore.read(new File(f.getPath() + ".none")));
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}); }
            assertNull(LearnStore.read(f));
        } finally { f.delete(); }
    }
}

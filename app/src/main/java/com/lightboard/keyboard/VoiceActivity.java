package com.lightboard.keyboard;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

public final class VoiceActivity extends Activity {
    private static volatile boolean granted;

    static boolean takeGranted() { boolean g = granted; granted = false; return g; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { granted = true; finish(); return; }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        granted = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            Toast.makeText(this, KbText.t("Нужен доступ к микрофону для голосового ввода"), Toast.LENGTH_LONG).show();
            if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                try { startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName()))); } catch (RuntimeException ignored) { }
            }
        }
        finish();
        overridePendingTransition(0, 0);
    }
}

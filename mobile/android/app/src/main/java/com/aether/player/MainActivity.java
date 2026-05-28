package com.aether.player;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AetherCredentialPlugin.class);
        registerPlugin(AetherLocalLibraryPlugin.class);
        super.onCreate(savedInstanceState);

        // Edge-to-edge: WebView extends behind status + navigation bars.
        // env(safe-area-inset-*) CSS vars handle the punch-hole offset on S26 Ultra.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Transparent system bars over OLED-black content.
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        // Render content under the punch-hole / display cutout on all edges.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        // White icons on the (transparent) status bar — content underneath is black.
        WindowInsetsControllerCompat controller =
            new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);

        // Replace Capacitor's WebViewClient with ours that serves /_audio/ paths
        // at same-origin (http://localhost), fixing MediaElementAudioSource CORS.
        getBridge().getWebView().setWebViewClient(
            new AetherWebViewClient(getBridge())
        );
    }
}

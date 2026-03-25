package com.aether.player;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AetherCredentialPlugin.class);
        registerPlugin(AetherLocalLibraryPlugin.class);
        super.onCreate(savedInstanceState);

        // Replace Capacitor's WebViewClient with ours that serves /_audio/ paths
        // at same-origin (http://localhost), fixing MediaElementAudioSource CORS.
        getBridge().getWebView().setWebViewClient(
            new AetherWebViewClient(getBridge())
        );
    }
}

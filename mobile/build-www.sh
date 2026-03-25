#!/bin/bash
# Build script: copies player.html into www/index.html with mobile injections
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$SCRIPT_DIR/../player.html"
DEST="$SCRIPT_DIR/www/index.html"

if [ ! -f "$SRC" ]; then
  echo "ERROR: player.html not found at $SRC"
  exit 1
fi

# Copy player.html
cp "$SRC" "$DEST"

# Inject mobile-tweaks.css before </head>
sed -i 's|</head>|<link rel="stylesheet" href="mobile-tweaks.css">\n</head>|' "$DEST"

# Inject early mobile script to intercept requestAnimationFrame BEFORE player.html's
# inline viz code starts its 60fps loops. This MUST run before any <script> in <body>.
sed -i 's|</head>|<script>\n(function(){var r=window.requestAnimationFrame.bind(window);var dead=false;window.__killAnimations=function(){dead=true;};window.__reviveAnimations=function(){dead=false;};window.requestAnimationFrame=function(cb){if(dead)return 0;return r(cb);}})();\n</script>\n</head>|' "$DEST"

# Inject mobile-bridge.js and viewport meta before </body>
sed -i 's|</body>|<script src="mobile-bridge.js"></script>\n</body>|' "$DEST"

# Add mobile viewport meta (replace existing one)
sed -i 's|<meta name="viewport" content="width=device-width, initial-scale=1.0">|<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">|' "$DEST"

# Sync to Capacitor assets directory (what the WebView actually serves)
ASSETS_DIR="$SCRIPT_DIR/android/app/src/main/assets/public"
if [ -d "$ASSETS_DIR" ]; then
  cp "$DEST" "$ASSETS_DIR/index.html"
  cp "$SCRIPT_DIR/www/mobile-bridge.js" "$ASSETS_DIR/mobile-bridge.js"
  cp "$SCRIPT_DIR/www/mobile-tweaks.css" "$ASSETS_DIR/mobile-tweaks.css"
  echo "Synced to Capacitor assets"
fi

echo "Built www/index.html successfully"

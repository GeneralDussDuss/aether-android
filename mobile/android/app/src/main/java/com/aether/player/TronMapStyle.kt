package com.aether.player

/**
 * Google Maps JSON style — AETHER Tron aesthetic.
 * Derived from CSS vars in player.html :root (lines 56-95).
 *
 * Color mapping:
 *   bg-void (#000000)       → base geometry
 *   purple-dim (#362050)    → roads
 *   purple-core (#7B2FBE)   → highway stroke
 *   purple-vivid (#B76EFF)  → labels
 *   cyan-muted (#0E6377)    → water
 *   cyan-dim (#0A3D4A)      → parks
 *   cyan-bright (#00DCF5)   → water/park labels
 *   magenta-bright (#FF2D7B)→ POI labels
 */
object TronMapStyle {

    const val JSON = """[
  {
    "elementType": "geometry",
    "stylers": [{"color": "#000000"}]
  },
  {
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#B76EFF"}]
  },
  {
    "elementType": "labels.text.stroke",
    "stylers": [{"color": "#000000"}, {"weight": 3}]
  },
  {
    "featureType": "road",
    "elementType": "geometry",
    "stylers": [{"color": "#362050"}]
  },
  {
    "featureType": "road",
    "elementType": "geometry.stroke",
    "stylers": [{"color": "#4A2D6B"}, {"weight": 0.5}]
  },
  {
    "featureType": "road.highway",
    "elementType": "geometry",
    "stylers": [{"color": "#4A2D6B"}]
  },
  {
    "featureType": "road.highway",
    "elementType": "geometry.stroke",
    "stylers": [{"color": "#7B2FBE"}, {"weight": 1}]
  },
  {
    "featureType": "road.highway",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#B76EFF"}]
  },
  {
    "featureType": "water",
    "elementType": "geometry",
    "stylers": [{"color": "#0E6377"}]
  },
  {
    "featureType": "water",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#00DCF5"}]
  },
  {
    "featureType": "landscape.natural",
    "elementType": "geometry",
    "stylers": [{"color": "#09090F"}]
  },
  {
    "featureType": "poi.park",
    "elementType": "geometry",
    "stylers": [{"color": "#0A3D4A"}]
  },
  {
    "featureType": "poi.park",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#00DCF5"}]
  },
  {
    "featureType": "poi",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#FF2D7B"}]
  },
  {
    "featureType": "poi",
    "elementType": "geometry",
    "stylers": [{"color": "#0F0F1A"}]
  },
  {
    "featureType": "transit",
    "elementType": "geometry",
    "stylers": [{"color": "#1A0A2E"}]
  },
  {
    "featureType": "transit.station",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#9D4EDD"}]
  },
  {
    "featureType": "administrative",
    "elementType": "geometry.stroke",
    "stylers": [{"color": "#362050"}, {"weight": 1}]
  },
  {
    "featureType": "administrative",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#6B42A0"}]
  },
  {
    "featureType": "road.local",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#6B42A0"}]
  },
  {
    "featureType": "road.arterial",
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#9D4EDD"}]
  }
]"""
}

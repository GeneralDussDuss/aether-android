package com.aether.player

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Subsonic/Navidrome REST client.
 * Mirrors the JS SUBSONIC object from player.html (lines 2110-2139).
 */
class SubsonicClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    var baseUrl: String = "http://localhost:4533"
    var user: String = "phantom"
    var password: String = "your_password"

    /** Load credentials from SharedPreferences (written by AetherCredentialPlugin). */
    fun loadCredentials() {
        val prefs = context.getSharedPreferences("aether_credentials", Context.MODE_PRIVATE)
        baseUrl = prefs.getString("baseUrl", baseUrl) ?: baseUrl
        user = prefs.getString("user", user) ?: user
        password = prefs.getString("password", password) ?: password
    }

    // ── Auth ─────────────────────────────────────────────────────────────

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun auth(): String {
        val salt = (1..8).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        val token = md5(password + salt)
        return "u=$user&t=$token&s=$salt&v=1.16.1&c=Aether&f=json"
    }

    // ── Core request ─────────────────────────────────────────────────────

    fun get(endpoint: String, params: Map<String, String> = emptyMap()): JSONObject? {
        val extra = if (params.isNotEmpty()) {
            "&" + params.entries.joinToString("&") { "${it.key}=${it.value}" }
        } else ""
        val url = "$baseUrl/rest/$endpoint?${auth()}$extra"
        val request = Request.Builder().url(url).build()
        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            JSONObject(body).optJSONObject("subsonic-response")
        } catch (e: Exception) {
            null
        }
    }

    // ── URL builders ─────────────────────────────────────────────────────

    fun streamUrl(id: String): String {
        return "$baseUrl/rest/stream?id=$id&${auth()}"
    }

    fun coverUrl(id: String, size: Int = 300): String {
        val salt = "aether_cover"
        val token = md5(password + salt)
        return "$baseUrl/rest/getCoverArt?id=$id&size=$size&u=$user&t=$token&s=$salt&v=1.16.1&c=Aether&f=json"
    }

    // ── Library queries ──────────────────────────────────────────────────

    fun getAlbums(type: String = "alphabeticalByName", size: Int = 500): List<JSONObject> {
        val resp = get("getAlbumList2", mapOf("type" to type, "size" to size.toString()))
        val list = resp?.optJSONObject("albumList2")?.optJSONArray("album") ?: return emptyList()
        return list.toObjectList()
    }

    fun getArtists(): List<JSONObject> {
        val resp = get("getArtists") ?: return emptyList()
        val indexes = resp.optJSONObject("artists")?.optJSONArray("index") ?: return emptyList()
        val result = mutableListOf<JSONObject>()
        for (i in 0 until indexes.length()) {
            val artists = indexes.getJSONObject(i).optJSONArray("artist") ?: continue
            for (j in 0 until artists.length()) {
                result.add(artists.getJSONObject(j))
            }
        }
        return result
    }

    fun getPlaylists(): List<JSONObject> {
        val resp = get("getPlaylists") ?: return emptyList()
        return resp.optJSONObject("playlists")?.optJSONArray("playlist")?.toObjectList() ?: emptyList()
    }

    fun getPlaylist(id: String): List<JSONObject> {
        val resp = get("getPlaylist", mapOf("id" to id)) ?: return emptyList()
        return resp.optJSONObject("playlist")?.optJSONArray("entry")?.toObjectList() ?: emptyList()
    }

    fun getAlbumTracks(id: String): List<JSONObject> {
        val resp = get("getAlbum", mapOf("id" to id)) ?: return emptyList()
        return resp.optJSONObject("album")?.optJSONArray("song")?.toObjectList() ?: emptyList()
    }

    fun getGenres(): List<JSONObject> {
        val resp = get("getGenres") ?: return emptyList()
        return resp.optJSONObject("genres")?.optJSONArray("genre")?.toObjectList() ?: emptyList()
    }

    fun getRecentlyAdded(size: Int = 50): List<JSONObject> {
        return getAlbums("newest", size)
    }

    fun getRandomSongs(size: Int = 50): List<JSONObject> {
        val resp = get("getRandomSongs", mapOf("size" to size.toString())) ?: return emptyList()
        return resp.optJSONObject("randomSongs")?.optJSONArray("song")?.toObjectList() ?: emptyList()
    }

    fun getSongsByGenre(genre: String, size: Int = 100): List<JSONObject> {
        val resp = get("getSongsByGenre", mapOf("genre" to genre, "count" to size.toString())) ?: return emptyList()
        return resp.optJSONObject("songsByGenre")?.optJSONArray("song")?.toObjectList() ?: emptyList()
    }

    fun scrobble(id: String) {
        get("scrobble", mapOf("id" to id))
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun JSONArray.toObjectList(): List<JSONObject> {
        return (0 until length()).map { getJSONObject(it) }
    }
}

package com.aether.player

import android.Manifest
import android.content.ContentUris
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import kotlin.math.abs

/**
 * Capacitor plugin that queries Android MediaStore and returns Subsonic-shaped JSON
 * so all existing render functions in player.html work unchanged.
 *
 * Track IDs: "local_{mediaStoreId}"
 * Album IDs: "local_album_{albumId}"
 * Artist IDs: "local_artist_{hash}"
 */
@CapacitorPlugin(
    name = "AetherLocalLibrary",
    permissions = [
        Permission(
            alias = "audio",
            strings = [
                Manifest.permission.READ_MEDIA_AUDIO
            ]
        ),
        Permission(
            alias = "storage",
            strings = [
                Manifest.permission.READ_EXTERNAL_STORAGE
            ]
        )
    ]
)
class AetherLocalLibraryPlugin : Plugin() {

    companion object {
        private const val TAG = "AetherLocalLib"
    }

    private var fileServer: AetherFileServer? = null

    // ── Permissions ──

    @PluginMethod
    override fun requestPermissions(call: PluginCall) {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissionForAlias("audio", call, "permissionCallback")
        } else {
            requestPermissionForAlias("storage", call, "permissionCallback")
        }
    }

    @PermissionCallback
    private fun permissionCallback(call: PluginCall) {
        val granted = if (Build.VERSION.SDK_INT >= 33) {
            getPermissionState("audio") == com.getcapacitor.PermissionState.GRANTED
        } else {
            getPermissionState("storage") == com.getcapacitor.PermissionState.GRANTED
        }
        val result = JSObject()
        result.put("granted", granted)
        call.resolve(result)
    }

    @PluginMethod
    override fun checkPermissions(call: PluginCall) {
        val granted = if (Build.VERSION.SDK_INT >= 33) {
            getPermissionState("audio") == com.getcapacitor.PermissionState.GRANTED
        } else {
            getPermissionState("storage") == com.getcapacitor.PermissionState.GRANTED
        }
        val result = JSObject()
        result.put("granted", granted)
        call.resolve(result)
    }

    // ── File Server ──

    @PluginMethod
    fun startFileServer(call: PluginCall) {
        try {
            if (fileServer == null) {
                fileServer = AetherFileServer(context)
            }
            val port = fileServer!!.start()
            val result = JSObject()
            result.put("port", port)
            call.resolve(result)
        } catch (e: Exception) {
            call.reject("Failed to start file server: ${e.message}")
        }
    }

    // ── MediaStore Queries ──

    @PluginMethod
    fun getArtists(call: PluginCall) {
        try {
            val artists = queryArtists()
            // Group artists alphabetically into indexes (Subsonic format)
            val indexMap = mutableMapOf<String, MutableList<JSObject>>()
            artists.forEach { artist ->
                val name = artist.getString("name") ?: ""
                val letter = if (name.isNotEmpty() && name[0].isLetter()) name[0].uppercaseChar().toString() else "#"
                indexMap.getOrPut(letter) { mutableListOf() }.add(artist)
            }

            val indexArray = JSArray()
            indexMap.toSortedMap().forEach { (letter, list) ->
                val idx = JSObject()
                idx.put("name", letter)
                val artistArray = JSArray()
                list.forEach { artistArray.put(it) }
                idx.put("artist", artistArray)
                indexArray.put(idx)
            }

            val artistsObj = JSObject()
            artistsObj.put("index", indexArray)

            val result = JSObject()
            result.put("status", "ok")
            result.put("artists", artistsObj)
            call.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "getArtists error", e)
            call.reject("getArtists failed: ${e.message}")
        }
    }

    @PluginMethod
    fun getArtist(call: PluginCall) {
        try {
            val artistId = call.getString("id") ?: run {
                call.reject("Missing artist id")
                return
            }

            // artistId = "local_artist_HASH" — extract the hash
            val artistHash = artistId.removePrefix("local_artist_")

            // Find artist name from hash
            val artistName = findArtistNameByHash(artistHash) ?: run {
                call.reject("Artist not found")
                return
            }

            // Get all albums by this artist
            val albums = queryAlbumsByArtist(artistName)

            val artistObj = JSObject()
            artistObj.put("id", artistId)
            artistObj.put("name", artistName)
            val albumArray = JSArray()
            albums.forEach { albumArray.put(it) }
            artistObj.put("album", albumArray)
            artistObj.put("albumCount", albums.size)

            val result = JSObject()
            result.put("status", "ok")
            result.put("artist", artistObj)
            call.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "getArtist error", e)
            call.reject("getArtist failed: ${e.message}")
        }
    }

    @PluginMethod
    fun getAlbumList2(call: PluginCall) {
        try {
            val type = call.getString("type") ?: "alphabeticalByName"
            val size = call.getInt("size") ?: 20
            val offset = call.getInt("offset") ?: 0
            val genre = call.getString("genre")

            val albums = when (type) {
                "newest" -> queryAlbums("${MediaStore.Audio.Media.DATE_ADDED} DESC", size, offset)
                "frequent", "highest" -> queryAlbums("${MediaStore.Audio.Media.ALBUM} ASC", size, offset) // no play count in MediaStore
                "random" -> queryAlbums(null, size, offset, random = true)
                "alphabeticalByName" -> queryAlbums("${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE ASC", size, offset)
                "byGenre" -> {
                    if (genre != null) queryAlbumsByGenre(genre, size, offset)
                    else queryAlbums("${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE ASC", size, offset)
                }
                else -> queryAlbums("${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE ASC", size, offset)
            }

            val albumList = JSObject()
            val albumArray = JSArray()
            albums.forEach { albumArray.put(it) }
            albumList.put("album", albumArray)

            val result = JSObject()
            result.put("status", "ok")
            result.put("albumList2", albumList)
            call.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "getAlbumList2 error", e)
            call.reject("getAlbumList2 failed: ${e.message}")
        }
    }

    @PluginMethod
    fun getAlbum(call: PluginCall) {
        try {
            val albumId = call.getString("id") ?: run {
                call.reject("Missing album id")
                return
            }

            // albumId = "local_album_123"
            val mediaAlbumId = albumId.removePrefix("local_album_").toLongOrNull() ?: run {
                call.reject("Invalid album id")
                return
            }

            val songs = queryTracksByAlbumId(mediaAlbumId)
            val firstSong = songs.firstOrNull()

            val albumObj = JSObject()
            albumObj.put("id", albumId)
            albumObj.put("name", firstSong?.getString("album") ?: "Unknown Album")
            albumObj.put("artist", firstSong?.getString("artist") ?: "Unknown Artist")
            albumObj.put("coverArt", albumId)
            albumObj.put("songCount", songs.size)

            val songArray = JSArray()
            songs.forEach { songArray.put(it) }
            albumObj.put("song", songArray)

            val result = JSObject()
            result.put("status", "ok")
            result.put("album", albumObj)
            call.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "getAlbum error", e)
            call.reject("getAlbum failed: ${e.message}")
        }
    }

    @PluginMethod
    fun getRandomSongs(call: PluginCall) {
        try {
            val size = call.getInt("size") ?: 20
            val songs = queryRandomSongs(size)

            val randomSongs = JSObject()
            val songArray = JSArray()
            songs.forEach { songArray.put(it) }
            randomSongs.put("song", songArray)

            val result = JSObject()
            result.put("status", "ok")
            result.put("randomSongs", randomSongs)
            call.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "getRandomSongs error", e)
            call.reject("getRandomSongs failed: ${e.message}")
        }
    }

    @PluginMethod
    fun getSong(call: PluginCall) {
        try {
            val songId = call.getString("id") ?: run {
                call.reject("Missing song id")
                return
            }

            val mediaId = songId.removePrefix("local_").toLongOrNull() ?: run {
                call.reject("Invalid song id")
                return
            }

            val song = querySongById(mediaId)
            if (song == null) {
                call.reject("Song not found")
                return
            }

            val result = JSObject()
            result.put("status", "ok")
            result.put("song", song)
            call.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "getSong error", e)
            call.reject("getSong failed: ${e.message}")
        }
    }

    @PluginMethod
    fun getGenres(call: PluginCall) {
        try {
            val genres = queryGenres()

            val genresObj = JSObject()
            val genreArray = JSArray()
            genres.forEach { genreArray.put(it) }
            genresObj.put("genre", genreArray)

            val result = JSObject()
            result.put("status", "ok")
            result.put("genres", genresObj)
            call.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "getGenres error", e)
            call.reject("getGenres failed: ${e.message}")
        }
    }

    @PluginMethod
    fun getSongsByGenre(call: PluginCall) {
        try {
            val genre = call.getString("genre") ?: run {
                call.reject("Missing genre")
                return
            }
            val count = call.getInt("count") ?: 50

            val songs = querySongsByGenre(genre, count)

            val songsByGenre = JSObject()
            val songArray = JSArray()
            songs.forEach { songArray.put(it) }
            songsByGenre.put("song", songArray)

            val result = JSObject()
            result.put("status", "ok")
            result.put("songsByGenre", songsByGenre)
            call.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "getSongsByGenre error", e)
            call.reject("getSongsByGenre failed: ${e.message}")
        }
    }

    @PluginMethod
    fun search3(call: PluginCall) {
        try {
            val query = call.getString("query") ?: ""
            val songCount = call.getInt("songCount") ?: 10
            val albumCount = call.getInt("albumCount") ?: 6
            val artistCount = call.getInt("artistCount") ?: 4

            val searchResult = JSObject()

            // Search songs
            val songs = searchSongs(query, songCount)
            val songArray = JSArray()
            songs.forEach { songArray.put(it) }
            searchResult.put("song", songArray)

            // Search albums
            val albums = searchAlbums(query, albumCount)
            val albumArray = JSArray()
            albums.forEach { albumArray.put(it) }
            searchResult.put("album", albumArray)

            // Search artists
            val artists = searchArtists(query, artistCount)
            val artistArray = JSArray()
            artists.forEach { artistArray.put(it) }
            searchResult.put("artist", artistArray)

            val result = JSObject()
            result.put("status", "ok")
            result.put("searchResult3", searchResult)
            call.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "search3 error", e)
            call.reject("search3 failed: ${e.message}")
        }
    }

    // Subsonic ping equivalent — always succeeds for local
    @PluginMethod
    fun ping(call: PluginCall) {
        val result = JSObject()
        result.put("status", "ok")
        result.put("version", "1.16.1")
        call.resolve(result)
    }

    // ── Internal MediaStore Queries ──

    private val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    private val songProjection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.DISPLAY_NAME
    )

    private fun cursorToSong(cursor: Cursor): JSObject {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown"
        val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown"
        val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unknown"
        val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
        val durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
        val track = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
        val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE))
        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)) ?: ""

        val durationSec = (durationMs / 1000).toInt()
        val suffix = displayName.substringAfterLast('.', "flac").lowercase()
        val bitRate = if (durationSec > 0) ((size * 8) / durationSec / 1000).toInt() else 0

        val song = JSObject()
        song.put("id", "local_$id")
        song.put("title", title)
        song.put("artist", artist)
        song.put("album", album)
        song.put("albumId", "local_album_$albumId")
        song.put("coverArt", "local_album_$albumId")
        song.put("duration", durationSec)
        song.put("track", track % 1000) // MediaStore sometimes encodes disc in track
        song.put("size", size)
        song.put("suffix", suffix)
        song.put("bitRate", bitRate)
        song.put("contentType", cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)) ?: "audio/flac")
        song.put("isLocal", true)
        return song
    }

    private fun queryArtists(): List<JSObject> {
        val artistMap = mutableMapOf<String, MutableSet<Long>>() // artistName -> albumIds

        context.contentResolver.query(
            audioUri, arrayOf(MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM_ID),
            "${MediaStore.Audio.Media.IS_MUSIC} = 1",
            null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val artist = cursor.getString(0) ?: continue
                if (artist == "<unknown>") continue
                val albumId = cursor.getLong(1)
                artistMap.getOrPut(artist) { mutableSetOf() }.add(albumId)
            }
        }

        return artistMap.map { (name, albumIds) ->
            val obj = JSObject()
            val hash = abs(name.hashCode()).toString()
            obj.put("id", "local_artist_$hash")
            obj.put("name", name)
            obj.put("albumCount", albumIds.size)
            // Use first album for cover art
            obj.put("coverArt", "local_album_${albumIds.first()}")
            obj
        }.sortedBy { (it.getString("name") ?: "").lowercase() }
    }

    private fun findArtistNameByHash(hash: String): String? {
        context.contentResolver.query(
            audioUri, arrayOf(MediaStore.Audio.Media.ARTIST),
            "${MediaStore.Audio.Media.IS_MUSIC} = 1",
            null, null
        )?.use { cursor ->
            val seen = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                if (name == "<unknown>") continue
                if (seen.add(name) && abs(name.hashCode()).toString() == hash) {
                    return name
                }
            }
        }
        return null
    }

    private fun queryAlbumsByArtist(artistName: String): List<JSObject> {
        val albumMap = mutableMapOf<Long, JSObject>()

        context.contentResolver.query(
            audioUri,
            arrayOf(
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media._ID
            ),
            "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.ARTIST} = ?",
            arrayOf(artistName),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val albumId = cursor.getLong(0)
                val albumName = cursor.getString(1) ?: "Unknown"
                val artist = cursor.getString(2) ?: artistName

                if (!albumMap.containsKey(albumId)) {
                    val obj = JSObject()
                    obj.put("id", "local_album_$albumId")
                    obj.put("name", albumName)
                    obj.put("artist", artist)
                    obj.put("coverArt", "local_album_$albumId")
                    obj.put("songCount", 0)
                    albumMap[albumId] = obj
                }
                val current = albumMap[albumId]!!
                current.put("songCount", (current.getInt("songCount") ?: 0) + 1)
            }
        }

        return albumMap.values.toList()
    }

    private fun queryAlbums(sortOrder: String?, size: Int, offset: Int, random: Boolean = false): List<JSObject> {
        val albumMap = linkedMapOf<Long, JSObject>()
        val trackCounts = mutableMapOf<Long, Int>()

        // First pass: count tracks per album and gather metadata
        context.contentResolver.query(
            audioUri,
            arrayOf(
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATE_ADDED
            ),
            "${MediaStore.Audio.Media.IS_MUSIC} = 1",
            null,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val albumId = cursor.getLong(0)
                trackCounts[albumId] = (trackCounts[albumId] ?: 0) + 1

                if (!albumMap.containsKey(albumId)) {
                    val albumName = cursor.getString(1) ?: "Unknown"
                    val artist = cursor.getString(2) ?: "Unknown"

                    val obj = JSObject()
                    obj.put("id", "local_album_$albumId")
                    obj.put("name", albumName)
                    obj.put("artist", artist)
                    obj.put("coverArt", "local_album_$albumId")
                    albumMap[albumId] = obj
                }
            }
        }

        // Apply track counts
        albumMap.forEach { (id, obj) ->
            obj.put("songCount", trackCounts[id] ?: 0)
        }

        var albums = albumMap.values.toList()

        if (random) {
            albums = albums.shuffled()
        }

        return albums.drop(offset).take(size)
    }

    private fun queryAlbumsByGenre(genre: String, size: Int, offset: Int): List<JSObject> {
        val albumMap = linkedMapOf<Long, JSObject>()
        val trackCounts = mutableMapOf<Long, Int>()

        val genreSelection = "${MediaStore.Audio.Media.IS_MUSIC} = 1"

        context.contentResolver.query(
            audioUri,
            arrayOf(
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST
            ) + if (Build.VERSION.SDK_INT >= 30) arrayOf(MediaStore.Audio.Media.GENRE) else emptyArray(),
            genreSelection,
            null,
            null
        )?.use { cursor ->
            val hasGenreColumn = if (Build.VERSION.SDK_INT >= 30) {
                cursor.getColumnIndex(MediaStore.Audio.Media.GENRE) >= 0
            } else false

            while (cursor.moveToNext()) {
                if (hasGenreColumn) {
                    val trackGenre = cursor.getString(3) ?: ""
                    if (!trackGenre.equals(genre, ignoreCase = true)) continue
                }

                val albumId = cursor.getLong(0)
                trackCounts[albumId] = (trackCounts[albumId] ?: 0) + 1

                if (!albumMap.containsKey(albumId)) {
                    val obj = JSObject()
                    obj.put("id", "local_album_$albumId")
                    obj.put("name", cursor.getString(1) ?: "Unknown")
                    obj.put("artist", cursor.getString(2) ?: "Unknown")
                    obj.put("coverArt", "local_album_$albumId")
                    albumMap[albumId] = obj
                }
            }
        }

        albumMap.forEach { (id, obj) ->
            obj.put("songCount", trackCounts[id] ?: 0)
        }

        return albumMap.values.toList().drop(offset).take(size)
    }

    private fun queryTracksByAlbumId(albumId: Long): List<JSObject> {
        val songs = mutableListOf<JSObject>()
        context.contentResolver.query(
            audioUri, songProjection,
            "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.ALBUM_ID} = ?",
            arrayOf(albumId.toString()),
            "${MediaStore.Audio.Media.TRACK} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                songs.add(cursorToSong(cursor))
            }
        }
        return songs
    }

    private fun queryRandomSongs(count: Int): List<JSObject> {
        val songs = mutableListOf<JSObject>()
        context.contentResolver.query(
            audioUri, songProjection,
            "${MediaStore.Audio.Media.IS_MUSIC} = 1",
            null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                songs.add(cursorToSong(cursor))
            }
        }
        return songs.shuffled().take(count)
    }

    private fun querySongById(mediaId: Long): JSObject? {
        context.contentResolver.query(
            ContentUris.withAppendedId(audioUri, mediaId),
            songProjection, null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursorToSong(cursor)
            }
        }
        return null
    }

    private fun queryGenres(): List<JSObject> {
        if (Build.VERSION.SDK_INT >= 30) {
            // Android 11+: genre column available on audio table
            return queryGenresModern()
        }
        // Fallback: use genre content provider
        return queryGenresLegacy()
    }

    private fun queryGenresModern(): List<JSObject> {
        // Only called from queryGenres() when SDK >= 30
        val genreMap = mutableMapOf<String, Pair<Int, MutableSet<Long>>>() // genre -> (songCount, albumIds)

        context.contentResolver.query(
            audioUri,
            arrayOf(MediaStore.Audio.Media.GENRE, MediaStore.Audio.Media.ALBUM_ID),
            "${MediaStore.Audio.Media.IS_MUSIC} = 1",
            null, null
        )?.use { cursor ->
            val genreIdx = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
            if (genreIdx < 0) return queryGenresLegacy()

            while (cursor.moveToNext()) {
                val genre = cursor.getString(genreIdx)
                if (genre.isNullOrBlank()) continue
                val albumId = cursor.getLong(1)
                val entry = genreMap.getOrPut(genre) { Pair(0, mutableSetOf()) }
                genreMap[genre] = Pair(entry.first + 1, entry.second.also { it.add(albumId) })
            }
        }

        return genreMap.map { (genre, pair) ->
            val obj = JSObject()
            obj.put("value", genre)
            obj.put("songCount", pair.first)
            obj.put("albumCount", pair.second.size)
            obj
        }.sortedByDescending { it.getInt("songCount") }
    }

    private fun queryGenresLegacy(): List<JSObject> {
        val genres = mutableListOf<JSObject>()
        val genreUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI

        context.contentResolver.query(
            genreUri,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val genreId = cursor.getLong(0)
                val genreName = cursor.getString(1) ?: continue
                if (genreName.isBlank()) continue

                // Count songs in this genre
                val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                val songCount = context.contentResolver.query(
                    membersUri,
                    arrayOf(MediaStore.Audio.Genres.Members._ID),
                    null, null, null
                )?.use { it.count } ?: 0

                if (songCount > 0) {
                    val obj = JSObject()
                    obj.put("value", genreName)
                    obj.put("songCount", songCount)
                    obj.put("albumCount", 0) // Approximate
                    genres.add(obj)
                }
            }
        }

        return genres.sortedByDescending { it.getInt("songCount") }
    }

    private fun querySongsByGenre(genre: String, count: Int): List<JSObject> {
        val songs = mutableListOf<JSObject>()

        if (Build.VERSION.SDK_INT >= 30) {
            context.contentResolver.query(
                audioUri, songProjection,
                "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.GENRE} = ?",
                arrayOf(genre),
                null
            )?.use { cursor ->
                while (cursor.moveToNext() && songs.size < count) {
                    songs.add(cursorToSong(cursor))
                }
            }
            if (songs.isNotEmpty()) return songs
        }

        // Fallback: query genre members
        val genreUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            genreUri,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
            "${MediaStore.Audio.Genres.NAME} = ?",
            arrayOf(genre), null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val genreId = cursor.getLong(0)
                val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                context.contentResolver.query(
                    membersUri, songProjection, null, null, null
                )?.use { memberCursor ->
                    while (memberCursor.moveToNext() && songs.size < count) {
                        songs.add(cursorToSong(memberCursor))
                    }
                }
            }
        }

        return songs
    }

    private fun searchSongs(query: String, limit: Int): List<JSObject> {
        val songs = mutableListOf<JSObject>()
        val likeQuery = "%$query%"

        context.contentResolver.query(
            audioUri, songProjection,
            "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND (${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ?)",
            arrayOf(likeQuery, likeQuery),
            null
        )?.use { cursor ->
            while (cursor.moveToNext() && songs.size < limit) {
                songs.add(cursorToSong(cursor))
            }
        }
        return songs
    }

    private fun searchAlbums(query: String, limit: Int): List<JSObject> {
        val albumMap = linkedMapOf<Long, JSObject>()
        val likeQuery = "%$query%"

        context.contentResolver.query(
            audioUri,
            arrayOf(
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST
            ),
            "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.ALBUM} LIKE ?",
            arrayOf(likeQuery),
            null
        )?.use { cursor ->
            while (cursor.moveToNext() && albumMap.size < limit) {
                val albumId = cursor.getLong(0)
                if (!albumMap.containsKey(albumId)) {
                    val obj = JSObject()
                    obj.put("id", "local_album_$albumId")
                    obj.put("name", cursor.getString(1) ?: "Unknown")
                    obj.put("artist", cursor.getString(2) ?: "Unknown")
                    obj.put("coverArt", "local_album_$albumId")
                    albumMap[albumId] = obj
                }
            }
        }
        return albumMap.values.toList()
    }

    private fun searchArtists(query: String, limit: Int): List<JSObject> {
        val seen = mutableSetOf<String>()
        val artists = mutableListOf<JSObject>()
        val likeQuery = "%$query%"

        context.contentResolver.query(
            audioUri,
            arrayOf(MediaStore.Audio.Media.ARTIST),
            "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.ARTIST} LIKE ?",
            arrayOf(likeQuery),
            null
        )?.use { cursor ->
            while (cursor.moveToNext() && artists.size < limit) {
                val name = cursor.getString(0) ?: continue
                if (name == "<unknown>") continue
                if (seen.add(name)) {
                    val obj = JSObject()
                    obj.put("id", "local_artist_${abs(name.hashCode())}")
                    obj.put("name", name)
                    artists.add(obj)
                }
            }
        }
        return artists
    }
}

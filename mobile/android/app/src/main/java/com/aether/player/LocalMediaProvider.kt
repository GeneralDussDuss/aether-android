package com.aether.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONObject
import kotlin.math.abs

/**
 * Queries Android MediaStore and returns Subsonic-shaped JSONObjects
 * so AetherMediaService's browse tree builders work unchanged.
 *
 * Track IDs: "local_{mediaStoreId}"
 * Album IDs: "local_album_{albumId}"
 * Artist IDs: "local_artist_{hash}"
 *
 * For playback: use contentUri() to get a content:// URI that ExoPlayer plays directly.
 * For album art: use artworkUri() to get a content:// URI for the album art.
 */
class LocalMediaProvider(private val context: Context) {

    private val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    // ── Albums ──

    fun getAlbums(type: String = "alphabeticalByName", size: Int = 500): List<JSONObject> {
        val albumMap = linkedMapOf<Long, JSONObject>()
        val trackCounts = mutableMapOf<Long, Int>()

        val sortOrder = when (type) {
            "newest" -> "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            "alphabeticalByName" -> "${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE ASC"
            else -> "${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE ASC"
        }

        context.contentResolver.query(
            audioUri,
            arrayOf(
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST
            ),
            "${MediaStore.Audio.Media.IS_MUSIC} = 1",
            null, sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val albumId = cursor.getLong(0)
                trackCounts[albumId] = (trackCounts[albumId] ?: 0) + 1
                if (!albumMap.containsKey(albumId)) {
                    albumMap[albumId] = JSONObject().apply {
                        put("id", "local_album_$albumId")
                        put("name", cursor.getString(1) ?: "Unknown Album")
                        put("artist", cursor.getString(2) ?: "Unknown Artist")
                        put("coverArt", "local_album_$albumId")
                    }
                }
            }
        }

        albumMap.forEach { (id, obj) -> obj.put("songCount", trackCounts[id] ?: 0) }

        var albums = albumMap.values.toList()
        if (type == "random") albums = albums.shuffled()
        return albums.take(size)
    }

    // ── Artists ──

    fun getArtists(): List<JSONObject> {
        val artistMap = mutableMapOf<String, MutableSet<Long>>()

        context.contentResolver.query(
            audioUri,
            arrayOf(MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM_ID),
            "${MediaStore.Audio.Media.IS_MUSIC} = 1",
            null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val artist = cursor.getString(0) ?: return@use
                if (artist == "<unknown>") return@use
                val albumId = cursor.getLong(1)
                artistMap.getOrPut(artist) { mutableSetOf() }.add(albumId)
            }
        }

        return artistMap.map { (name, albumIds) ->
            JSONObject().apply {
                put("id", "local_artist_${abs(name.hashCode())}")
                put("name", name)
                put("albumCount", albumIds.size)
                put("coverArt", "local_album_${albumIds.first()}")
            }
        }.sortedBy { it.optString("name").lowercase() }
    }

    fun getArtistAlbums(artistHash: String): List<JSONObject> {
        val artistName = findArtistNameByHash(artistHash) ?: return emptyList()
        val albumMap = linkedMapOf<Long, JSONObject>()
        val trackCounts = mutableMapOf<Long, Int>()

        context.contentResolver.query(
            audioUri,
            arrayOf(
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST
            ),
            "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.ARTIST} = ?",
            arrayOf(artistName), null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val albumId = cursor.getLong(0)
                trackCounts[albumId] = (trackCounts[albumId] ?: 0) + 1
                if (!albumMap.containsKey(albumId)) {
                    albumMap[albumId] = JSONObject().apply {
                        put("id", "local_album_$albumId")
                        put("name", cursor.getString(1) ?: "Unknown")
                        put("artist", cursor.getString(2) ?: artistName)
                        put("coverArt", "local_album_$albumId")
                    }
                }
            }
        }

        albumMap.forEach { (id, obj) -> obj.put("songCount", trackCounts[id] ?: 0) }
        return albumMap.values.toList()
    }

    // ── Album tracks ──

    fun getAlbumTracks(albumId: String): List<JSONObject> {
        val mediaAlbumId = albumId.toLongOrNull() ?: return emptyList()
        return queryTracks(
            "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.ALBUM_ID} = ?",
            arrayOf(mediaAlbumId.toString()),
            "${MediaStore.Audio.Media.TRACK} ASC"
        )
    }

    // ── Random songs ──

    fun getRandomSongs(size: Int = 50): List<JSONObject> {
        return queryTracks(
            "${MediaStore.Audio.Media.IS_MUSIC} = 1", null, null
        ).shuffled().take(size)
    }

    // ── Genres ──

    fun getGenres(): List<JSONObject> {
        val genres = mutableListOf<JSONObject>()

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val genreMap = mutableMapOf<String, Int>()
            context.contentResolver.query(
                audioUri,
                arrayOf(MediaStore.Audio.Media.GENRE),
                "${MediaStore.Audio.Media.IS_MUSIC} = 1",
                null, null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                if (idx < 0) return@use
                while (cursor.moveToNext()) {
                    val genre = cursor.getString(idx)
                    if (genre.isNullOrBlank()) continue
                    genreMap[genre] = (genreMap[genre] ?: 0) + 1
                }
            }
            genreMap.forEach { (name, count) ->
                genres.add(JSONObject().apply {
                    put("value", name)
                    put("songCount", count)
                })
            }
        } else {
            val genreUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                genreUri,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val genreId = cursor.getLong(0)
                    val name = cursor.getString(1) ?: continue
                    if (name.isBlank()) continue
                    val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                    val count = context.contentResolver.query(
                        membersUri, arrayOf(MediaStore.Audio.Genres.Members._ID),
                        null, null, null
                    )?.use { it.count } ?: 0
                    if (count > 0) {
                        genres.add(JSONObject().apply {
                            put("value", name)
                            put("songCount", count)
                        })
                    }
                }
            }
        }

        return genres.sortedByDescending { it.optInt("songCount") }
    }

    fun getSongsByGenre(genre: String, size: Int = 100): List<JSONObject> {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val songs = queryTracks(
                "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.GENRE} = ?",
                arrayOf(genre), null
            )
            if (songs.isNotEmpty()) return songs.take(size)
        }
        // Fallback via genre members
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
                return queryTracksFromUri(membersUri, size)
            }
        }
        return emptyList()
    }

    // ── Recently added ──

    fun getRecentlyAdded(size: Int = 50): List<JSONObject> {
        return getAlbums("newest", size)
    }

    // ── URI builders ──

    /** Content URI for ExoPlayer playback */
    fun contentUri(mediaStoreId: Long): Uri {
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
    }

    /** Content URI for album artwork */
    fun artworkUri(albumId: Long): Uri {
        return ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"), albumId
        )
    }

    // ── Internal helpers ──

    private val trackProjection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.TRACK
    )

    private fun queryTracks(selection: String?, args: Array<String>?, sort: String?): List<JSONObject> {
        val songs = mutableListOf<JSONObject>()
        context.contentResolver.query(audioUri, trackProjection, selection, args, sort)?.use { cursor ->
            while (cursor.moveToNext()) {
                songs.add(cursorToTrack(cursor))
            }
        }
        return songs
    }

    private fun queryTracksFromUri(uri: Uri, limit: Int): List<JSONObject> {
        val songs = mutableListOf<JSONObject>()
        context.contentResolver.query(uri, trackProjection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext() && songs.size < limit) {
                songs.add(cursorToTrack(cursor))
            }
        }
        return songs
    }

    private fun cursorToTrack(cursor: android.database.Cursor): JSONObject {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
        return JSONObject().apply {
            put("id", "local_$id")
            put("title", cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown")
            put("artist", cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown")
            put("album", cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unknown")
            put("albumId", "local_album_$albumId")
            put("coverArt", "local_album_$albumId")
            put("duration", (cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)) / 1000).toInt())
            put("track", cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)) % 1000)
        }
    }

    private fun findArtistNameByHash(hash: String): String? {
        context.contentResolver.query(
            audioUri, arrayOf(MediaStore.Audio.Media.ARTIST),
            "${MediaStore.Audio.Media.IS_MUSIC} = 1", null, null
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
}

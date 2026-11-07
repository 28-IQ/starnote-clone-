package clone.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Google Drive v3 REST, official API, scope drive.appdata only: files live in the app's hidden data folder
 * and the app can see nothing else in the user's Drive. Uploads are resumable and chunked.
 * https://developers.google.com/workspace/drive/api/guides/manage-uploads#resumable
 */
class GoogleDriveProvider(
    private val tokens: suspend () -> String,
    private val sessions: SessionStore,
) : BackupProvider {

    /** Remembers an open upload session by content hash so a restarted job can resume. Sessions expire after a week. */
    interface SessionStore {
        fun get(contentHash: String): String?
        fun put(contentHash: String, sessionUrl: String)
        fun clear(contentHash: String)
    }

    override val id = "gdrive"

    private class Response(val code: Int, val location: String?, val range: String?, val body: String) {
        val reason: String? get() = try {
            JSONObject(body).getJSONObject("error").getJSONArray("errors").getJSONObject(0).getString("reason")
        } catch (_: Exception) { null }
    }

    override suspend fun find(sha256: String): RemoteBackup? = listWith(DriveProtocol.findQuery(sha256)).firstOrNull()

    override suspend fun list(): List<RemoteBackup> = listWith(null)

    private suspend fun listWith(query: String?): List<RemoteBackup> = withContext(Dispatchers.IO) {
        val token = tokens()
        val out = ArrayList<RemoteBackup>()
        var page: String? = null
        do {
            val url = StringBuilder("$API/files?spaces=appDataFolder&pageSize=100&orderBy=createdTime%20desc&fields=$LIST_FIELDS")
            if (query != null) url.append("&q=").append(enc(query))
            if (page != null) url.append("&pageToken=").append(enc(page))
            val r = call("GET", url.toString(), token)
            requireOk(r, 200)
            val json = JSONObject(r.body)
            val files = json.optJSONArray("files")
            if (files != null) for (i in 0 until files.length()) out.add(parse(files.getJSONObject(i)))
            page = json.optString("nextPageToken").ifEmpty { null }
        } while (page != null)
        out
    }

    override suspend fun upload(file: File, name: String, sha256: String, onProgress: (Long, Long) -> Unit): RemoteBackup =
        withContext(Dispatchers.IO) {
            val total = file.length()
            if (total <= 0) throw BackupException("The backup file is empty")
            var session = sessions.get(sha256)
            var offset = 0L
            if (session != null) {
                val q = queryProgress(session, total)
                if (q.done != null) { sessions.clear(sha256); return@withContext q.done }
                if (q.offset != null) offset = q.offset else { sessions.clear(sha256); session = null }   // session expired
            }
            if (session == null) {
                session = startSession(name, sha256, total)
                sessions.put(sha256, session)
                offset = 0
            }

            var finished: RemoteBackup? = null
            var attempts = 0
            while (finished == null) {
                val end = DriveProtocol.chunkEnd(offset, total)
                try {
                    val r = putChunk(session, file, offset, end, total)
                    when (r.code) {
                        200, 201 -> { sessions.clear(sha256); finished = parse(JSONObject(r.body)) }
                        308 -> { offset = DriveProtocol.nextOffsetFromRange(r.range); attempts = 0; onProgress(offset, total) }
                        else -> throw failure(r)
                    }
                } catch (e: IOException) {
                    if (++attempts >= DriveProtocol.MAX_CHUNK_ATTEMPTS) throw BackupException("The network dropped during the upload", retryable = true, cause = e)
                    delay(DriveProtocol.backoffMs(attempts))
                    val q = queryProgress(session, total)
                    if (q.done != null) { sessions.clear(sha256); finished = q.done }
                    else offset = q.offset ?: throw BackupException("The upload session ended", retryable = true)
                }
            }
            finished
        }

    override suspend fun download(remote: RemoteBackup, dest: File, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val token = tokens()
        val conn = open("GET", "$API/files/${enc(remote.id)}?alt=media", token)
        try {
            val code = conn.responseCode
            if (code != 200) throw failure(Response(code, null, null, conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()))
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: remote.sizeBytes
            var received = 0L
            dest.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n); received += n
                        onProgress(received, total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (remote.sha256 != null && remote.sizeBytes > 0 && dest.length() != remote.sizeBytes) {
            dest.delete(); throw BackupException("The download was incomplete", retryable = true)
        }
    }

    override suspend fun delete(remote: RemoteBackup) = withContext(Dispatchers.IO) {
        val r = call("DELETE", "$API/files/${enc(remote.id)}", tokens())
        if (r.code != 204 && r.code != 404) throw failure(r)
    }

    override suspend fun deleteAll(): Int {
        val all = list()
        all.forEach { delete(it) }
        return all.size
    }

    // ---- resumable upload steps ----

    private suspend fun startSession(name: String, contentHash: String, total: Long): String {
        val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder"))
            .put("appProperties", JSONObject().put("sha256", contentHash)).toString().toByteArray(Charsets.UTF_8)
        val token = tokens()
        val r = call(
            "POST", "$UPLOAD/files?uploadType=resumable&fields=$LIST_FIELDS_ONE", token,
            headers = mapOf("Content-Type" to "application/json; charset=UTF-8", "X-Upload-Content-Type" to "application/zip", "X-Upload-Content-Length" to total.toString()),
            body = meta,
        )
        requireOk(r, 200)
        return r.location ?: throw BackupException("Google Drive did not start the upload", retryable = true)
    }

    private class Progress(val offset: Long?, val done: RemoteBackup?)

    /** Asks the session how much it has. 404 or 410 means it expired. */
    private fun queryProgress(session: String, total: Long): Progress {
        val r = call("PUT", session, null, headers = mapOf("Content-Range" to DriveProtocol.statusRange(total), "Content-Length" to "0"), body = ByteArray(0))
        return when (r.code) {
            308 -> Progress(DriveProtocol.nextOffsetFromRange(r.range), null)
            200, 201 -> Progress(null, parse(JSONObject(r.body)))
            404, 410 -> Progress(null, null)
            else -> throw failure(r)
        }
    }

    private fun putChunk(session: String, file: File, start: Long, endInclusive: Long, total: Long): Response {
        val len = (endInclusive - start + 1).toInt()
        val conn = open("PUT", session, null)
        try {
            conn.setRequestProperty("Content-Range", DriveProtocol.contentRange(start, endInclusive, total))
            conn.setRequestProperty("Content-Type", "application/zip")
            conn.setFixedLengthStreamingMode(len)
            conn.doOutput = true
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buf = ByteArray(64 * 1024)
                var left = len
                conn.outputStream.use { out ->
                    while (left > 0) {
                        val n = raf.read(buf, 0, minOf(buf.size, left))
                        if (n < 0) throw IOException("File ended early")
                        out.write(buf, 0, n); left -= n
                    }
                }
            }
            return readResponse(conn)
        } finally {
            conn.disconnect()
        }
    }

    // ---- plain HTTP ----

    private fun open(method: String, url: String, token: String?): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20_000
        c.readTimeout = 60_000
        c.instanceFollowRedirects = false      // a 308 here means "resume incomplete", not a redirect
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        return c
    }

    private fun call(method: String, url: String, token: String?, headers: Map<String, String> = emptyMap(), body: ByteArray? = null): Response {
        val c = open(method, url, token)
        try {
            headers.forEach { (k, v) -> if (k != "Content-Length") c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            return readResponse(c)
        } finally {
            c.disconnect()
        }
    }

    private fun readResponse(c: HttpURLConnection): Response {
        val code = c.responseCode
        val stream = if (code >= 400) c.errorStream else c.inputStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return Response(code, c.getHeaderField("Location"), c.getHeaderField("Range"), body)
    }

    private fun requireOk(r: Response, vararg ok: Int) { if (r.code !in ok) throw failure(r) }

    private fun failure(r: Response) =
        BackupException(DriveProtocol.userMessage(r.code, r.reason), retryable = DriveProtocol.isRetryable(r.code, r.reason))

    private fun parse(o: JSONObject) = RemoteBackup(
        id = o.getString("id"),
        name = o.optString("name"),
        sizeBytes = o.optString("size", "0").toLongOrNull() ?: 0,
        createdAt = o.optString("createdTime"),
        sha256 = o.optJSONObject("appProperties")?.optString("sha256")?.ifEmpty { null },
    )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val LIST_FIELDS_ONE = "id,name,size,createdTime,appProperties"
        const val LIST_FIELDS = "nextPageToken,files($LIST_FIELDS_ONE)"
    }
}

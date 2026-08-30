package com.watson.nutrilog.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Google Drive REST v3，只用到備份需要的那幾支端點。
 *
 * **不用官方的 Drive Java client**：那一包會拖進 google-api-client 與 guava，
 * 為了四支 HTTP 呼叫換來上百個類別並不划算 —— 和這個專案不用 Retrofit 是同一個
 *理由（見 [OpenFoodFactsClient]）。
 *
 * 授權範圍固定是 **drive.file**：只碰得到這個 app 自己建立的檔案。這是刻意的，
 * 有兩個好處：
 *
 * - 使用者的其他檔案在技術上就碰不到，不是靠我們自律。
 * - drive.file 不是 Google 定義的敏感範圍，**不需要通過安全評估審查**
 *   （drive / drive.readonly 需要，那要付費、跑好幾週）。
 *
 * 代價是列檔案時看不到別人放進那個資料夾的東西 —— 對備份來說剛好不是問題。
 */
class DriveClient(private val client: OkHttpClient = SharedHttp.client) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 找出（或建立）Drive 主頁底下的資料夾，回傳它的 id。
     *
     * 放在主頁而不是隱藏的 appDataFolder，是因為備份要**看得到、拿得走**：
     * 使用者可以自己下載那個 CSV，用本地匯入讀回來，不必依賴這個 app 還活著。
     */
    suspend fun ensureFolder(token: String, name: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val query = "name = '" + name.replace("'", "\'") + "'" +
                " and mimeType = 'application/vnd.google-apps.folder'" +
                " and 'root' in parents and trashed = false"
            val found = get(token, "/drive/v3/files?fields=files(id,name)&q=" + query.urlEncoded())
            json.decodeFromString(FileList.serializer(), found).files.firstOrNull()?.id
                ?: createFolder(token, name)
        }
    }

    private fun createFolder(token: String, name: String): String {
        val body = """{"name":"${name.jsonEscaped()}","mimeType":"application/vnd.google-apps.folder","parents":["root"]}"""
        val request = Request.Builder()
            .url(API + "/drive/v3/files?fields=id")
            .header("Authorization", "Bearer " + token)
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        return json.decodeFromString(DriveFile.serializer(), request.executeText()).id
    }

    /**
     * 上傳一個純文字檔。同名檔案存在就覆蓋它的內容（而不是再建一個同名的）——
     * Drive 允許同一個資料夾裡有多個同名檔案，不處理的話一天跑兩次備份就會多一份。
     */
    suspend fun upload(
        token: String,
        folderId: String,
        name: String,
        content: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = list(token, folderId).getOrThrow().firstOrNull { it.name == name }
            val media = content.toRequestBody(CSV_MEDIA)
            val metadata = if (existing == null) {
                """{"name":"${name.jsonEscaped()}","parents":["$folderId"]}"""
            } else {
                // 更新時不能再帶 parents，Drive 會回 400
                """{"name":"${name.jsonEscaped()}"}"""
            }
            val multipart = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadata.toRequestBody(JSON_MEDIA))
                .addPart(media)
                .build()

            val url = if (existing == null) {
                API + "/upload/drive/v3/files?uploadType=multipart&fields=id"
            } else {
                API + "/upload/drive/v3/files/" + existing.id + "?uploadType=multipart&fields=id"
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .let { if (existing == null) it.post(multipart) else it.patch(multipart) }
                .build()
            json.decodeFromString(DriveFile.serializer(), request.executeText()).id
        }
    }

    /** 資料夾裡的檔案，新到舊。名字是 nutrilog-YYYY-MM-DD.csv，字串排序就是日期排序。 */
    suspend fun list(token: String, folderId: String): Result<List<DriveFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val query = "'$folderId' in parents and trashed = false"
            val body = get(
                token,
                "/drive/v3/files?orderBy=name desc&pageSize=100" +
                    "&fields=files(id,name,size,modifiedTime)&q=" + query.urlEncoded(),
            )
            json.decodeFromString(FileList.serializer(), body).files
        }
    }

    suspend fun download(token: String, fileId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { get(token, "/drive/v3/files/" + fileId + "?alt=media") }
    }

    suspend fun delete(token: String, fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Request.Builder()
                .url(API + "/drive/v3/files/" + fileId)
                .header("Authorization", "Bearer " + token)
                .delete()
                .build()
                .executeText()
            Unit
        }
    }

    /** 授權當下用的是哪個帳號。設定頁要顯示它 —— 不然使用者不知道備份進了誰的 Drive。 */
    suspend fun accountEmail(token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get(token, "/drive/v3/about?fields=user(emailAddress)")
            json.decodeFromString(About.serializer(), body).user.emailAddress
        }
    }

    private fun get(token: String, path: String): String =
        Request.Builder()
            .url(API + path)
            .header("Authorization", "Bearer " + token)
            .build()
            .executeText()

    private fun Request.executeText(): String =
        client.newCall(this).execute().use { response ->
            val body = response.body?.string().orEmpty()
            // 訊息帶上 Drive 回的內容：權杖過期、範圍不足、配額用完長得都不一樣，
            // 只丟一個「HTTP 403」使用者跟我們都無從判斷是哪一種。
            if (!response.isSuccessful) error("HTTP " + response.code + " " + body.take(300))
            body
        }

    @Serializable
    data class DriveFile(
        val id: String,
        val name: String = "",
        val size: String? = null,
        val modifiedTime: String? = null,
    )

    @Serializable
    private data class FileList(val files: List<DriveFile> = emptyList())

    @Serializable
    private data class About(val user: User = User()) {
        @Serializable
        data class User(val emailAddress: String = "")
    }

    private companion object {
        const val API = "https://www.googleapis.com"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val CSV_MEDIA = "text/csv; charset=utf-8".toMediaType()
    }
}

/** 只有查詢字串會用到，所以不引 java.net.URLEncoder 以外的東西。 */
private fun String.urlEncoded(): String = java.net.URLEncoder.encode(this, "UTF-8")

private fun String.jsonEscaped(): String = replace("\\", "\\\\").replace("\"", "\\\"")

package edu.fudan.elearning.sync.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Canvas 课程（含学期）。 */
data class CanvasCourse(
    val id: Long,
    val name: String,
    @SerializedName("course_code") val courseCode: String = "",
    @SerializedName("enrollment_term_id") val enrollmentTermId: Long = 0,
    val term: CanvasTerm? = null
)

/** Canvas 学期。 */
data class CanvasTerm(
    val id: Long,
    val name: String = ""
)

/** Canvas 文件。 */
data class CanvasFile(
    val id: Long,
    @SerializedName("display_name") val displayName: String = "",
    val filename: String = "",
    val size: Long = 0,
    val url: String = "",
    @SerializedName("folder_id") val folderId: Long = 0,
    @SerializedName("updated_at") val updatedAt: String = "",
    @SerializedName("mime_class") val mimeClass: String = ""
)

/** Canvas REST API 客户端。 */
class CanvasApi(private val baseUrl: String = "https://elearning.fudan.edu.cn") {
    private val gson = Gson()

    /** 获取当前用户的所有可见课程（含学期）。 */
    suspend fun getCourses(): List<CanvasCourse> = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.get(
                "$baseUrl/api/v1/courses?per_page=100&include[]=term&state[]=available&enrollment_type=student"
            )
            resp.use {
                val body = it.body?.string() ?: "[]"
                gson.fromJson(body, Array<CanvasCourse>::class.java).toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 获取指定课程的所有文件（不区分文件夹，减少请求与流量）。 */
    suspend fun getCourseFiles(courseId: Long): List<CanvasFile> = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.get(
                "$baseUrl/api/v1/courses/$courseId/files?per_page=200&sort=position"
            )
            resp.use {
                val body = it.body?.string() ?: "[]"
                gson.fromJson(body, Array<CanvasFile>::class.java).toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 获取当前用户信息，用于校验登录。 */
    suspend fun getCurrentUser(): String? = withContext(Dispatchers.IO) {
        try {
            ApiClient.get("$baseUrl/api/v1/users/self").use {
                val body = it.body?.string() ?: "{}"
                val obj = com.google.gson.JsonParser.parseString(body).asJsonObject
                obj.get("name")?.takeIf { n -> !n.isJsonNull }?.asString
            }
        } catch (e: Exception) {
            null
        }
    }
}

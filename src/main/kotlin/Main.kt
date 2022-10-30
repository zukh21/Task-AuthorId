import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

const val BASE_URL = "http://localhost:9999/api/slow/"
val gson = Gson()
val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()

fun main() {
    runBlocking {
        val posts = getPosts()
        val postWithCommentAndAuthors = posts.map {
            async {
                PostWithCommentsAndAuthor(post = it, author = getAuthorById(it.authorId), comment = getComments(it.id))
            }
        }.awaitAll()
        for (i in postWithCommentAndAuthors) {
            println("Author: ${i.author.name}\nContent: ${i.post.content}\nComments: ${i.comment}")
        }
    }
}

suspend fun getAuthorById(id: Long): Author = makeRequest("authors/$id", object : TypeToken<Author>() {})
suspend fun getPosts(): List<Post> = makeRequest("posts", object : TypeToken<List<Post>>() {})

suspend fun getComments(id: Long): List<Comment> =
    makeRequest("posts/$id/comments", object : TypeToken<List<Comment>>() {})

suspend fun <T> makeRequest(endpoint: String, typeToken: TypeToken<T>): T = suspendCoroutine { continuation ->
    Request.Builder()
        .url("$BASE_URL$endpoint")
        .build()
        .let(client::newCall)
        .enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.body?.string() ?: throw Exception("")
                    val post = gson.fromJson<T>(result, typeToken.type)
                    continuation.resume(post)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        })
}

data class Post(
    val id: Long,
    val authorId: Long,
    val content: String,
    val published: Long,
    val likedByMe: Boolean,
    val likes: Int = 0,
    var attachment: Attachment? = null,
)

data class Attachment(
    val url: String,
    val description: String,
    val type: AttachmentType,
)

enum class AttachmentType {
    IMAGE
}


data class Comment(
    val id: Long,
    val postId: Long,
    val authorId: Long,
    val content: String,
    val published: Long,
    val likedByMe: Boolean,
    val likes: Int = 0,
)

data class PostWithCommentsAndAuthor(
    val post: Post,
    val author: Author,
    val comment: List<Comment>
)


data class Author(
    val id: Long,
    val name: String,
    val avatar: String,
)

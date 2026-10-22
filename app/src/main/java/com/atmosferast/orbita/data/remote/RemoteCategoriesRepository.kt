package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.Category
import com.atmosferast.orbita.domain.model.CategoryDraft
import com.atmosferast.orbita.domain.repository.CategoriesRepository
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/**
 * The user's categories (atm-orbita-api, plan 03). Like the settings, the last list read is kept
 * here, updated with the answer of every write, and dropped when the session changes.
 */
@Singleton
class RemoteCategoriesRepository @Inject constructor(
    private val api: ApiSession,
    private val changes: RemoteChanges = RemoteChanges(),
) : CategoriesRepository {

    private val cache = MutableStateFlow<List<Category>?>(null)

    init {
        api.onSessionChange { cache.value = null }
    }

    override fun observeCategories(): Flow<List<Category>> =
        cache.onStart { if (cache.value == null) load() }.filterNotNull()

    override suspend fun create(draft: CategoryDraft) {
        val request = CreateCategoryRequest(
            id = UUID.randomUUID().toString(),
            name = draft.name,
            kind = draft.kind.toApi(),
            color = draft.colorHex,
        )
        val created = send(HttpMethod.Post, "categories", request)
        cache.update { list -> list?.let { it + created } }
        changes.bump()
    }

    override suspend fun update(id: String, draft: CategoryDraft) {
        val updated = send(HttpMethod.Patch, "categories/$id", UpdateCategoryRequest(draft.name, draft.colorHex))
        cache.update { list -> list?.map { if (it.id == id) updated else it } }
        changes.bump()
    }

    override suspend fun archive(id: String) {
        api.authorized {
            method = HttpMethod.Post
            url("categories/$id/archive")
        }.requireSuccess()
        cache.update { list -> list?.filterNot { it.id == id } }
        changes.bump()
    }

    private suspend fun load() {
        val response = api.authorized { url("categories") }.requireSuccess()
        cache.value = response.body<DataEnvelope<List<CategoryDto>>>().data.map { it.toCategory() }
    }

    private suspend inline fun <reified T : Any> send(method: HttpMethod, path: String, payload: T): Category {
        val response = api.authorized {
            this.method = method
            url(path)
            setBody(payload)
        }.requireSuccess()
        return response.body<DataEnvelope<CategoryDto>>().data.toCategory()
    }
}

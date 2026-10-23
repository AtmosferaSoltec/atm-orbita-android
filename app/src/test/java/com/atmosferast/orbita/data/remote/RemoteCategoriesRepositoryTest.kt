package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.CategoryDraft
import com.atmosferast.orbita.domain.model.MovementKind
import com.atmosferast.orbita.domain.repository.DataError
import com.atmosferast.orbita.domain.repository.DataException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// The user's categories against the API (plan 03 of atm-orbita-api).

private class CategoryTokens : TokenStore {
    private var stored: StoredSession? = StoredSession("ana@correo.com", "refresh-1")
    override var accessToken: String? = "access-1"
    override fun load() = stored
    override fun save(session: StoredSession) {
        stored = session
    }
    override fun clear() {
        accessToken = null
        stored = null
    }
}

private class CategoriesHarness(
    val changes: RemoteChanges = RemoteChanges(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val session = ApiSession(createApiClient("http://api.test/api/v1", MockEngine(handler), "test"), CategoryTokens())
    val repository = RemoteCategoriesRepository(session, changes)
}

private val json = headersOf(HttpHeaders.ContentType, "application/json")

private fun category(id: String, name: String, kind: String, color: String = "#0E7490") =
    """{"id":"$id","name":"$name","kind":"$kind","color":"$color"}"""

private val initial = """{"data":[${category("c1", "Alimentación", "expense")},${category("c2", "Sueldo", "income", "#0B7A5A")}]}"""

class RemoteCategoriesRepositoryTest {

    @Test
    fun `the list keeps the API order and maps the kinds`() = runTest {
        val h = CategoriesHarness { respond(initial, HttpStatusCode.OK, json) }
        val list = h.repository.observeCategories().first()

        assertEquals(listOf("Alimentación", "Sueldo"), list.map { it.name })
        assertEquals(listOf(MovementKind.EXPENSE, MovementKind.INCOME), list.map { it.kind })
        assertEquals("#0B7A5A", list[1].colorHex)
    }

    @Test
    fun `creating sends an id of the app and adds the answer to the list`() = runTest {
        var sent = ""
        val h = CategoriesHarness { request ->
            if (request.method == HttpMethod.Get) return@CategoriesHarness respond(initial, HttpStatusCode.OK, json)
            sent = String(request.body.toByteArray())
            respond("""{"data":${category("c3", "Mascotas", "expense")}}""", HttpStatusCode.Created, json)
        }
        h.repository.observeCategories().first()
        h.repository.create(CategoryDraft("Mascotas", MovementKind.EXPENSE, "#0E7490"))

        assertTrue(sent, Regex("\"id\":\"[0-9a-f-]{36}\"").containsMatchIn(sent))
        assertTrue(sent, sent.contains("\"kind\":\"expense\""))
        assertEquals(listOf("Alimentación", "Sueldo", "Mascotas"), h.repository.observeCategories().first().map { it.name })
    }

    @Test
    fun `a repeated name is told as NAME_TAKEN`() = runTest {
        val h = CategoriesHarness {
            respond(
                """{"error":{"code":"CATEGORY_NAME_TAKEN","message":"x","details":[{"field":"name","code":"NAME_TAKEN"}]}}""",
                HttpStatusCode.Conflict, json,
            )
        }
        try {
            h.repository.create(CategoryDraft("alimentacion", MovementKind.EXPENSE, "#0E7490"))
            fail("expected a DataException")
        } catch (e: DataException) {
            assertEquals(DataError.NAME_TAKEN, e.error)
        }
    }

    @Test
    fun `updating sends only the name and the color`() = runTest {
        var sent = ""
        var path = ""
        val h = CategoriesHarness { request ->
            if (request.method == HttpMethod.Get) return@CategoriesHarness respond(initial, HttpStatusCode.OK, json)
            sent = String(request.body.toByteArray())
            path = request.url.encodedPath
            respond("""{"data":${category("c1", "Comida", "expense", "#B45309")}}""", HttpStatusCode.OK, json)
        }
        h.repository.observeCategories().first()
        h.repository.update("c1", CategoryDraft("Comida", MovementKind.INCOME, "#B45309"))

        assertEquals("/api/v1/categories/c1", path)
        assertFalse(sent, sent.contains("kind"))
        val updated = h.repository.observeCategories().first().first()
        assertEquals("Comida", updated.name)
        assertEquals(MovementKind.EXPENSE, updated.kind)
    }

    @Test
    fun `archiving removes it from the list`() = runTest {
        var path = ""
        val h = CategoriesHarness { request ->
            if (request.method == HttpMethod.Get) return@CategoriesHarness respond(initial, HttpStatusCode.OK, json)
            path = request.url.encodedPath
            respond("", HttpStatusCode.NoContent)
        }
        h.repository.observeCategories().first()
        h.repository.archive("c1")

        assertEquals("/api/v1/categories/c1/archive", path)
        assertEquals(listOf("Sueldo"), h.repository.observeCategories().first().map { it.name })
    }

    @Test
    fun `another session never sees the previous one's categories`() = runTest {
        var body = initial
        val h = CategoriesHarness { respond(body, HttpStatusCode.OK, json) }
        assertEquals(2, h.repository.observeCategories().first().size)

        h.session.end()
        body = """{"data":[${category("b1", "Viajes", "expense")}]}"""
        h.session.start("beto@correo.com", TokensDto("access-2", 900, "refresh-2"))

        assertEquals(listOf("Viajes"), h.repository.observeCategories().first().map { it.name })
    }

    @Test
    fun `back from the background the list is read again, and offline it stays`() = runTest {
        var answer = initial
        var offline = false
        var served = 0
        val h = CategoriesHarness { _ ->
            if (offline) throw IOException("offline")
            served++
            respond(answer, HttpStatusCode.OK, json)
        }
        val seen = async(Dispatchers.Default) { h.repository.observeCategories().take(2).toList() }
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (served == 0) delay(10)
                // An offline refresh changes nothing; then one with the new data arrives.
                offline = true
                h.changes.refreshAll()
                delay(50)
                offline = false
                answer = """{"data":[${category("c9", "Viajes", "expense")}]}"""
                while (!seen.isCompleted) {
                    h.changes.refreshAll()
                    delay(20)
                }
            }
        }

        val lists = seen.await().map { list -> list.map { it.name } }
        assertEquals(listOf(listOf("Alimentación", "Sueldo"), listOf("Viajes")), lists)
    }
}

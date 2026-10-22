package com.atmosferast.orbita.data

import com.atmosferast.orbita.data.demo.DemoCategoriesRepository
import com.atmosferast.orbita.data.demo.DemoCategoryMirror
import com.atmosferast.orbita.data.demo.DemoDateProvider
import com.atmosferast.orbita.data.demo.DemoEntriesRepository
import com.atmosferast.orbita.data.demo.DemoStore
import com.atmosferast.orbita.domain.model.Category
import com.atmosferast.orbita.domain.model.CategoryDraft
import com.atmosferast.orbita.domain.model.Movement
import com.atmosferast.orbita.domain.model.MovementDraft
import com.atmosferast.orbita.domain.model.MovementKind
import com.atmosferast.orbita.domain.repository.CategoriesRepository
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// While categories come from the API and movements are still demo data (plans 03 to 05 of
// atm-orbita-api), a movement can point to a category of the API.

private class FixedCategories(list: List<Category>) : CategoriesRepository {
    val list = MutableStateFlow(list)
    override fun observeCategories(): Flow<List<Category>> = list
    override suspend fun create(draft: CategoryDraft) = Unit
    override suspend fun update(id: String, draft: CategoryDraft) = Unit
    override suspend fun archive(id: String) = Unit
}

class DemoCategoryMirrorTest {

    @Test
    fun `a demo movement can use a category of the API`() = runTest {
        val store = DemoStore()
        val api = Category("0199a0e2-0000-7000-8000-000000000001", "Mascotas", MovementKind.EXPENSE, "#0E7490")
        val mirror = DemoCategoryMirror(FixedCategories(listOf(api)), store)
        val entries = DemoEntriesRepository(store, DemoDateProvider())

        assertEquals(listOf(api), mirror.observeCategories().first())
        entries.createMovement(MovementDraft(MovementKind.EXPENSE, BigDecimal("30.00"), "debit", api.id, "Croquetas"))

        val newest = entries.observeRecentEntries(1).first().single() as Movement
        assertEquals(api, newest.category)
        // The sample movements keep their demo categories, which no longer show.
        assertEquals(listOf(api), DemoCategoriesRepository(store).observeCategories().first())
        val old = entries.observeRecentEntries(50).first().filterIsInstance<Movement>().last()
        assertEquals(false, old.category.id == api.id)
    }
}

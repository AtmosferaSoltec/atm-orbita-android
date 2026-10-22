package com.atmosferast.orbita.data.demo

import com.atmosferast.orbita.domain.model.Category
import com.atmosferast.orbita.domain.repository.CategoriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Temporary, until movements move to the API (plan 05 of atm-orbita-api): the categories come
 * from the API but movements and purchases are still demo data, and the demo store must know
 * every category a movement points to. Each list read from [remote] is copied into [store]; the
 * demo's own categories stay there archived, so the sample movements keep theirs.
 */
class DemoCategoryMirror(
    private val remote: CategoriesRepository,
    private val store: DemoStore,
) : CategoriesRepository by remote {

    override fun observeCategories(): Flow<List<Category>> =
        remote.observeCategories().onEach(::mirror)

    private fun mirror(categories: List<Category>) = store.write { state ->
        val byId = categories.associateBy { it.id }
        val known = state.categories.map { record ->
            byId[record.category.id]?.let { CategoryRecord(it) } ?: record.copy(archived = true)
        }
        val knownIds = state.categories.map { it.category.id }.toSet()
        state.copy(categories = known + categories.filterNot { it.id in knownIds }.map { CategoryRecord(it) })
    }
}

package com.atmosferast.orbita.ui.feature.categories

import com.atmosferast.orbita.domain.model.Category
import com.atmosferast.orbita.domain.model.CategoryDraft
import com.atmosferast.orbita.domain.repository.CategoriesRepository
import com.atmosferast.orbita.ui.common.ActionViewModel
import com.atmosferast.orbita.ui.common.Load
import com.atmosferast.orbita.ui.common.MessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: CategoriesRepository,
    messages: MessageBus,
) : ActionViewModel(messages) {

    val categories: StateFlow<Load<List<Category>>> = load { repository.observeCategories() }

    /** Creates the category, or updates [editing] when it is set. */
    fun save(editing: Category?, draft: CategoryDraft, onDone: () -> Unit) =
        act(draft.validate(), onDone) {
            if (editing == null) repository.create(draft) else repository.update(editing.id, draft)
        }

    fun archive(category: Category, onDone: () -> Unit) =
        act(onDone = onDone) { repository.archive(category.id) }
}

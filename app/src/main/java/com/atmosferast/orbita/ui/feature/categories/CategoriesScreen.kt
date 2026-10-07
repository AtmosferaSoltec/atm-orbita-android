package com.atmosferast.orbita.ui.feature.categories

import com.atmosferast.orbita.ui.components.toHex
import com.atmosferast.orbita.ui.components.color
import com.atmosferast.orbita.domain.model.CategoryDraft
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atmosferast.orbita.R
import com.atmosferast.orbita.ui.components.ChipGroup
import com.atmosferast.orbita.ui.components.ConfirmDialog
import com.atmosferast.orbita.ui.components.FieldLabel
import com.atmosferast.orbita.ui.components.HintText
import com.atmosferast.orbita.ui.components.ModalTopBar
import com.atmosferast.orbita.ui.components.OrbitaCard
import com.atmosferast.orbita.ui.components.OrbitaChip
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.OrbitaTextField
import com.atmosferast.orbita.ui.components.PillButton
import com.atmosferast.orbita.ui.components.PrimaryButton
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.components.SegmentedControl
import com.atmosferast.orbita.domain.model.Category
import com.atmosferast.orbita.domain.model.MovementKind
import com.atmosferast.orbita.data.demo.SampleData
import com.atmosferast.orbita.ui.theme.CategoryPalette
import com.atmosferast.orbita.ui.theme.DividerSoft
import com.atmosferast.orbita.ui.theme.Expense
import com.atmosferast.orbita.ui.theme.ExpenseSoft
import com.atmosferast.orbita.ui.theme.Ink
import com.atmosferast.orbita.ui.theme.Muted
import com.atmosferast.orbita.ui.components.OrbitaPreview
import com.atmosferast.orbita.ui.theme.Primary
import com.atmosferast.orbita.ui.theme.PrimarySoft

/** Manage income and expense categories. Not in the original mockups (docs/05, section 9). */
@Composable
fun CategoriesScreen(
    categories: List<Category>,
    onBack: () -> Unit,
    onCategoryClick: (Category) -> Unit,
    onNewCategory: (MovementKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    var kind by remember { mutableStateOf(MovementKind.EXPENSE) }

    ScreenScaffold(
        modifier = modifier,
        header = {
            ModalTopBar(
                title = stringResource(R.string.categories_title),
                navigationIcon = OrbitaIcons.ChevronLeft,
                navigationLabel = stringResource(R.string.action_back),
                onNavigate = onBack,
            )
        },
        footer = {
            PrimaryButton(
                stringResource(R.string.category_new),
                onClick = { onNewCategory(kind) },
                container = PrimarySoft,
                content = Primary,
                icon = OrbitaIcons.Plus,
            )
        },
    ) {
        SegmentedControl(
            options = listOf(
                MovementKind.EXPENSE to stringResource(R.string.categories_expenses),
                MovementKind.INCOME to stringResource(R.string.categories_incomes),
            ),
            selected = kind,
            onSelect = { kind = it },
        )
        Spacer(Modifier.height(12.dp))
        OrbitaCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            categories.filter { it.kind == kind }.forEachIndexed { index, category ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = DividerSoft,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { onCategoryClick(category) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(category.color),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        category.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        OrbitaIcons.Edit,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** New category ([category] = null) or rename / recolor / archive an existing one. */
@Composable
fun CategoryFormScreen(
    category: Category?,
    onClose: () -> Unit,
    onSave: (CategoryDraft) -> Unit,
    modifier: Modifier = Modifier,
    initialKind: MovementKind = MovementKind.EXPENSE,
    onArchive: () -> Unit = {},
) {
    val editing = category != null
    var name by remember { mutableStateOf(category?.name.orEmpty()) }
    var kind by remember { mutableStateOf(category?.kind ?: initialKind) }
    var color by remember { mutableStateOf(category?.color ?: CategoryPalette.first()) }
    var confirmArchive by remember { mutableStateOf(false) }

    ScreenScaffold(
        modifier = modifier,
        header = {
            ModalTopBar(
                title = stringResource(
                    if (editing) R.string.category_edit_title else R.string.category_new,
                ),
                navigationIcon = if (editing) OrbitaIcons.ChevronLeft else OrbitaIcons.Close,
                navigationLabel = stringResource(
                    if (editing) R.string.action_back else R.string.action_close,
                ),
                onNavigate = onClose,
            )
        },
        footer = {
            PrimaryButton(
                stringResource(
                    if (editing) R.string.action_save_changes else R.string.category_save,
                ),
                onClick = { onSave(CategoryDraft(name, kind, color.toHex())) },
            )
        },
    ) {
        FieldLabel(stringResource(R.string.field_name))
        OrbitaTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.category_name_placeholder),
        )

        // The kind (income / expense) is fixed once the category exists.
        if (!editing) {
            FieldLabel(stringResource(R.string.field_type))
            ChipGroup {
                OrbitaChip(
                    stringResource(R.string.kind_expense),
                    kind == MovementKind.EXPENSE,
                    { kind = MovementKind.EXPENSE },
                )
                OrbitaChip(
                    stringResource(R.string.kind_income),
                    kind == MovementKind.INCOME,
                    { kind = MovementKind.INCOME },
                )
            }
        }

        FieldLabel(stringResource(R.string.field_color))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CategoryPalette.forEachIndexed { index, option ->
                ColorSwatch(
                    color = option,
                    selected = option == color,
                    label = stringResource(R.string.category_color_option, index + 1),
                    onClick = { color = option },
                )
            }
        }

        if (editing) {
            Spacer(Modifier.height(28.dp))
            PillButton(
                stringResource(R.string.category_archive),
                onClick = { confirmArchive = true },
                icon = OrbitaIcons.Archive,
                container = ExpenseSoft,
                content = Expense,
            )
            HintText(stringResource(R.string.category_archive_hint), Modifier.padding(top = 8.dp))
        }
    }

    if (confirmArchive) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_archive_category_title),
            text = stringResource(R.string.category_archive_hint),
            confirmLabel = stringResource(R.string.action_archive),
            onConfirm = {
                confirmArchive = false
                onArchive()
            },
            onDismiss = { confirmArchive = false },
        )
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(
                BorderStroke(2.dp, if (selected) color else Color.Transparent),
                CircleShape,
            )
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Preview(name = "Categorías", widthDp = 390, heightDp = 844)
@Composable
private fun CategoriesPreview() {
    OrbitaPreview {
        CategoriesScreen(SampleData.categories, onBack = {}, onCategoryClick = {}, onNewCategory = {})
    }
}

@Preview(name = "Nueva categoría", widthDp = 390, heightDp = 844)
@Composable
private fun CategoryNewPreview() {
    OrbitaPreview { CategoryFormScreen(category = null, onClose = {}, onSave = {}) }
}

@Preview(name = "Editar categoría", widthDp = 390, heightDp = 844)
@Composable
private fun CategoryEditPreview() {
    OrbitaPreview {
        CategoryFormScreen(SampleData.categories.first(), onClose = {}, onSave = {})
    }
}

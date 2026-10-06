package com.atmosferast.orbita.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.ui.components.BottomTab
import com.atmosferast.orbita.ui.components.OrbitaBottomBar
import com.atmosferast.orbita.ui.feature.accounts.AccountFormScreen
import com.atmosferast.orbita.ui.feature.accounts.AccountsScreen
import com.atmosferast.orbita.ui.feature.auth.AuthScreen
import com.atmosferast.orbita.ui.feature.categories.CategoriesScreen
import com.atmosferast.orbita.ui.feature.categories.CategoryFormScreen
import com.atmosferast.orbita.ui.feature.credit.CreditCardFormScreen
import com.atmosferast.orbita.ui.feature.credit.CreditScreen
import com.atmosferast.orbita.ui.feature.credit.PayCreditScreen
import com.atmosferast.orbita.ui.feature.home.HomeScreen
import com.atmosferast.orbita.ui.feature.movement.MovementFormScreen
import com.atmosferast.orbita.ui.feature.movement.MovementsScreen
import com.atmosferast.orbita.ui.feature.reports.ReportsScreen
import com.atmosferast.orbita.ui.feature.settings.SettingsScreen
import com.atmosferast.orbita.ui.feature.transfer.TransferScreen
import com.atmosferast.orbita.ui.mock.MockAccount
import com.atmosferast.orbita.ui.mock.MockCategory
import com.atmosferast.orbita.ui.mock.MockCreditCard
import com.atmosferast.orbita.ui.mock.MockCreditPurchase
import com.atmosferast.orbita.ui.mock.MockEntry
import com.atmosferast.orbita.ui.mock.MockFx
import com.atmosferast.orbita.ui.mock.MockMovement
import com.atmosferast.orbita.ui.mock.MockTransfer
import com.atmosferast.orbita.ui.mock.MovementKind
import com.atmosferast.orbita.ui.mock.SampleData
import com.atmosferast.orbita.ui.theme.Background

private sealed interface Screen {
    data object Login : Screen
    data object Register : Screen
    data object Main : Screen
    data class MovementForm(val editing: MockMovement? = null, val credit: Boolean = false) : Screen
    data class Transfer(val editing: MockTransfer? = null) : Screen
    data class PayCredit(val purchase: MockCreditPurchase) : Screen
    data class CreditCardForm(val card: MockCreditCard?) : Screen
    data object Settings : Screen
    data object Movements : Screen
    data class AccountForm(val account: MockAccount?) : Screen
    data object Categories : Screen
    data class CategoryForm(val category: MockCategory?, val kind: MovementKind) : Screen
}

/**
 * Navigable mockup of every screen, fed with sample data and an in-memory back stack.
 * Phase 1 replaces it with Navigation Compose, ViewModels and the Supabase session.
 */
@Composable
fun OrbitaMockApp() {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Login) }
    var tab by remember { mutableStateOf(BottomTab.HOME) }
    var accounts by remember { mutableStateOf(SampleData.accounts) }
    var purchases by remember { mutableStateOf(SampleData.creditPurchases) }
    var displayCurrency by remember { mutableStateOf(PEN) }
    var fx by remember { mutableStateOf(MockFx()) }
    // Set when Reportes is opened from a month card of Inicio; the bottom bar clears it.
    var reportFocus by remember { mutableStateOf<MovementKind?>(null) }

    fun push(screen: Screen) {
        backStack.add(screen)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun replaceTop(screen: Screen) {
        backStack[backStack.lastIndex] = screen
    }

    fun resetTo(screen: Screen) {
        backStack.clear()
        backStack.add(screen)
        tab = BottomTab.HOME
    }

    fun openEntry(entry: MockEntry) {
        when (entry) {
            is MockMovement -> push(Screen.MovementForm(editing = entry))
            is MockTransfer -> push(Screen.Transfer(editing = entry))
        }
    }

    BackHandler(enabled = backStack.size > 1) { pop() }
    BackHandler(enabled = backStack.size == 1 && tab != BottomTab.HOME) { tab = BottomTab.HOME }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        when (val screen = backStack.last()) {
            Screen.Login -> AuthScreen(
                register = false,
                onSubmit = { resetTo(Screen.Main) },
                onSwitch = { replaceTop(Screen.Register) },
            )

            Screen.Register -> AuthScreen(
                register = true,
                onSubmit = { resetTo(Screen.Main) },
                onSwitch = { replaceTop(Screen.Login) },
            )

            Screen.Main -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        BottomTab.HOME -> HomeScreen(
                            accounts = accounts,
                            fx = fx,
                            displayCurrency = displayCurrency,
                            onDisplayCurrencyChange = { displayCurrency = it },
                            onOpenSettings = { push(Screen.Settings) },
                            onOpenAccounts = { tab = BottomTab.ACCOUNTS },
                            onOpenCredit = { tab = BottomTab.CREDIT },
                            onOpenReport = { kind ->
                                reportFocus = kind
                                tab = BottomTab.REPORTS
                            },
                            onOpenMovements = { push(Screen.Movements) },
                            onEntryClick = ::openEntry,
                        )

                        BottomTab.ACCOUNTS -> AccountsScreen(
                            accounts = accounts,
                            fx = fx,
                            displayCurrency = displayCurrency,
                            onToggleSavings = { account, include ->
                                accounts = accounts.map {
                                    if (it.id == account.id) it.copy(includeInSavings = include)
                                    else it
                                }
                            },
                            onTransfer = { push(Screen.Transfer()) },
                            onAccountClick = { push(Screen.AccountForm(it)) },
                            onNewAccount = { push(Screen.AccountForm(null)) },
                        )

                        BottomTab.REPORTS -> ReportsScreen(
                            initialMonth = if (reportFocus != null) SampleData.currentMonth
                            else SampleData.reportMonth,
                            focus = reportFocus,
                        )

                        BottomTab.CREDIT -> CreditScreen(
                            cards = SampleData.creditCards,
                            mainCurrency = fx.main,
                            purchases = purchases,
                            onRegisterPurchase = { push(Screen.MovementForm(credit = true)) },
                            onPay = { push(Screen.PayCredit(it)) },
                            onCardClick = { push(Screen.CreditCardForm(it)) },
                            onNewCard = { push(Screen.CreditCardForm(null)) },
                        )
                    }
                }
                OrbitaBottomBar(
                    current = tab,
                    onSelect = {
                        reportFocus = null
                        tab = it
                    },
                    onAdd = { push(Screen.MovementForm()) },
                )
            }

            is Screen.MovementForm -> MovementFormScreen(
                accounts = accounts,
                onClose = ::pop,
                onSave = ::pop,
                onOpenTransfer = { replaceTop(Screen.Transfer()) },
                editing = screen.editing,
                initialCredit = screen.credit,
                onDelete = ::pop,
            )

            is Screen.Transfer -> TransferScreen(
                accounts = accounts,
                onClose = ::pop,
                onSave = ::pop,
                onOpenMovement = { replaceTop(Screen.MovementForm()) },
                initialFrom = screen.editing?.from ?: SampleData.dollarAccount,
                initialTo = screen.editing?.to ?: SampleData.debitAccount,
                fx = fx,
                editing = screen.editing,
                onDelete = ::pop,
            )

            is Screen.PayCredit -> PayCreditScreen(
                purchase = screen.purchase,
                accounts = accounts,
                onBack = ::pop,
                onConfirm = {
                    // The purchase leaves the pending list once it is paid.
                    purchases = purchases.filterNot { it.id == screen.purchase.id }
                    pop()
                },
            )

            is Screen.CreditCardForm -> CreditCardFormScreen(
                card = screen.card,
                defaultCurrency = fx.main,
                onClose = ::pop,
                onSave = ::pop,
            )

            Screen.Settings -> SettingsScreen(
                fx = fx,
                onFxChange = { newFx ->
                    // The total is shown in one of the two currencies of the pair.
                    if (displayCurrency != newFx.main && displayCurrency != newFx.secondary) {
                        displayCurrency = newFx.main
                    }
                    fx = newFx
                },
                displayCurrency = displayCurrency,
                onDisplayCurrencyChange = { displayCurrency = it },
                onBack = ::pop,
                onManageCategories = { push(Screen.Categories) },
                onLogout = { resetTo(Screen.Login) },
            )

            Screen.Movements -> MovementsScreen(
                accounts = accounts,
                onBack = ::pop,
                onEntryClick = ::openEntry,
            )

            is Screen.AccountForm -> AccountFormScreen(
                account = screen.account,
                defaultCurrency = fx.main,
                onClose = ::pop,
                onSave = ::pop,
            )

            Screen.Categories -> CategoriesScreen(
                onBack = ::pop,
                onCategoryClick = { push(Screen.CategoryForm(it, it.kind)) },
                onNewCategory = { push(Screen.CategoryForm(null, it)) },
            )

            is Screen.CategoryForm -> CategoryFormScreen(
                category = screen.category,
                onClose = ::pop,
                onSave = ::pop,
                initialKind = screen.kind,
            )
        }
    }
}

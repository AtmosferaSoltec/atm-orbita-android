package com.atmosferast.orbita.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atmosferast.orbita.R
import com.atmosferast.orbita.core.PEN
import com.atmosferast.orbita.core.USD
import com.atmosferast.orbita.domain.model.Account
import com.atmosferast.orbita.domain.model.Category
import com.atmosferast.orbita.domain.model.CreditCard
import com.atmosferast.orbita.domain.model.CreditPurchase
import com.atmosferast.orbita.domain.model.Entry
import com.atmosferast.orbita.domain.model.FxPair
import com.atmosferast.orbita.domain.model.Movement
import com.atmosferast.orbita.domain.model.MovementKind
import com.atmosferast.orbita.domain.model.SessionState
import com.atmosferast.orbita.domain.model.Transfer
import com.atmosferast.orbita.ui.common.Load
import com.atmosferast.orbita.ui.common.textRes
import com.atmosferast.orbita.ui.components.BottomTab
import com.atmosferast.orbita.ui.components.LocalToday
import com.atmosferast.orbita.ui.components.ModalTopBar
import com.atmosferast.orbita.ui.components.OrbitaBottomBar
import com.atmosferast.orbita.ui.components.OrbitaIcons
import com.atmosferast.orbita.ui.components.ScreenScaffold
import com.atmosferast.orbita.ui.components.StateMessage
import com.atmosferast.orbita.ui.feature.accounts.AccountFormScreen
import com.atmosferast.orbita.ui.feature.accounts.AccountsScreen
import com.atmosferast.orbita.ui.feature.accounts.AccountsViewModel
import com.atmosferast.orbita.ui.feature.auth.AuthScreen
import com.atmosferast.orbita.ui.feature.categories.CategoriesScreen
import com.atmosferast.orbita.ui.feature.categories.CategoriesViewModel
import com.atmosferast.orbita.ui.feature.categories.CategoryFormScreen
import com.atmosferast.orbita.ui.feature.credit.CreditCardFormScreen
import com.atmosferast.orbita.ui.feature.credit.CreditScreen
import com.atmosferast.orbita.ui.feature.credit.CreditViewModel
import com.atmosferast.orbita.ui.feature.credit.PayCreditScreen
import com.atmosferast.orbita.ui.feature.home.HomeScreen
import com.atmosferast.orbita.ui.feature.home.HomeState
import com.atmosferast.orbita.ui.feature.home.HomeUiState
import com.atmosferast.orbita.ui.feature.home.HomeViewModel
import com.atmosferast.orbita.ui.feature.movement.MovementFormScreen
import com.atmosferast.orbita.ui.feature.movement.MovementsScreen
import com.atmosferast.orbita.ui.feature.movement.MovementsViewModel
import com.atmosferast.orbita.ui.feature.reports.ReportsScreen
import com.atmosferast.orbita.ui.feature.reports.ReportsViewModel
import com.atmosferast.orbita.ui.feature.settings.SettingsScreen
import com.atmosferast.orbita.ui.feature.settings.SettingsViewModel
import com.atmosferast.orbita.ui.feature.transfer.TransferScreen
import com.atmosferast.orbita.ui.feature.transfer.TransferViewModel
import com.atmosferast.orbita.ui.theme.Background
import java.math.BigDecimal
import kotlinx.coroutines.flow.collectLatest

private sealed interface Screen {
    data object Main : Screen
    /** [purchase]: a pending purchase with a credit card being edited, instead of a movement. */
    data class MovementForm(
        val editing: Movement? = null,
        val credit: Boolean = false,
        val purchase: CreditPurchase? = null,
    ) : Screen
    data class TransferForm(val editing: Transfer? = null) : Screen
    data class PayCredit(val purchase: CreditPurchase) : Screen
    data class CreditCardForm(val card: CreditCard?) : Screen
    data object Settings : Screen
    data object Movements : Screen
    data class AccountForm(val account: Account?) : Screen
    data object Categories : Screen
    data class CategoryForm(val category: Category?, val kind: MovementKind) : Screen
}

/**
 * Root of the app: login or sign-up without a session, the screens with one. Every screen gets
 * its data from its ViewModel, which only talks to the repositories of the domain; whether
 * those are the demo data or the API is decided in di/AppModule.
 *
 * Navigation is still an in-memory back stack; moving it to Navigation Compose is pending.
 */
@Composable
fun OrbitaApp(app: AppViewModel = hiltViewModel()) {
    val session by app.session.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // A newer message replaces the one on screen instead of queueing behind it.
    LaunchedEffect(Unit) {
        app.messages.collectLatest { snackbar.showSnackbar(context.getString(it.textRes)) }
    }

    CompositionLocalProvider(LocalToday provides app.today) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            when (val current = session) {
                // The stored session is being restored: nothing to show yet.
                SessionState.Loading -> Unit
                SessionState.SignedOut -> AuthFlow(app)
                is SessionState.SignedIn -> MainFlow(email = current.email, onLogout = app::signOut)
            }
            // Above the button pinned to the foot of the forms.
            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 88.dp),
            )
        }
    }
}

@Composable
private fun AuthFlow(app: AppViewModel) {
    var register by rememberSaveable { mutableStateOf(false) }
    val error by app.authError.collectAsStateWithLifecycle()

    fun switchTo(newRegister: Boolean) {
        app.clearAuthError()
        register = newRegister
    }

    BackHandler(enabled = register) { switchTo(false) }

    AuthScreen(
        register = register,
        onSubmit = { email, password ->
            if (register) app.signUp(email, password) else app.signIn(email, password)
        },
        onSwitch = { switchTo(!register) },
        error = error?.let { stringResource(it.textRes) },
    )
}

// Never shown: Inicio draws its skeleton or its error instead of the totals while it has no data.
private val PlaceholderFx = FxPair(PEN, USD, rate = null)

@Composable
private fun MainFlow(email: String, onLogout: () -> Unit) {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Main) }
    var tab by remember { mutableStateOf(BottomTab.HOME) }
    // Set when Reportes is opened from a month card of Inicio; the bottom bar clears it.
    var reportFocus by remember { mutableStateOf<MovementKind?>(null) }

    val home: HomeViewModel = hiltViewModel()
    val accounts: AccountsViewModel = hiltViewModel()
    val movements: MovementsViewModel = hiltViewModel()
    val transfers: TransferViewModel = hiltViewModel()
    val reports: ReportsViewModel = hiltViewModel()
    val credit: CreditViewModel = hiltViewModel()
    val categories: CategoriesViewModel = hiltViewModel()
    val settings: SettingsViewModel = hiltViewModel()

    fun push(screen: Screen) {
        backStack.add(screen)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Closes [screen] once its save is done, unless the user already left it. */
    fun close(screen: Screen) {
        if (backStack.lastOrNull() === screen) pop()
    }

    fun replaceTop(screen: Screen) {
        backStack[backStack.lastIndex] = screen
    }

    fun openEntry(entry: Entry) {
        when (entry) {
            is Movement -> push(Screen.MovementForm(editing = entry))
            is Transfer -> push(Screen.TransferForm(editing = entry))
        }
    }

    BackHandler(enabled = backStack.size > 1) { pop() }
    BackHandler(enabled = backStack.size == 1 && tab != BottomTab.HOME) { tab = BottomTab.HOME }

    when (val screen = backStack.last()) {
        Screen.Main -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    BottomTab.HOME -> {
                        val load by home.state.collectAsStateWithLifecycle()
                        HomeTab(
                            load = load,
                            onDisplayCurrencyChange = home::setDisplayCurrency,
                            onRetry = home::retry,
                            onOpenSettings = { push(Screen.Settings) },
                            onOpenAccounts = { tab = BottomTab.ACCOUNTS },
                            onOpenCredit = { tab = BottomTab.CREDIT },
                            onOpenReport = { kind ->
                                reports.showCurrentMonth()
                                reportFocus = kind
                                tab = BottomTab.REPORTS
                            },
                            onOpenMovements = { push(Screen.Movements) },
                            onEntryClick = ::openEntry,
                        )
                    }

                    BottomTab.ACCOUNTS -> {
                        val state by accounts.state.collectAsStateWithLifecycle()
                        state?.let {
                            AccountsScreen(
                                accounts = it.accounts,
                                fx = it.fx,
                                displayCurrency = it.displayCurrency,
                                onToggleSavings = accounts::setIncludeInSavings,
                                onTransfer = { push(Screen.TransferForm()) },
                                onAccountClick = { account -> push(Screen.AccountForm(account)) },
                                onNewAccount = { push(Screen.AccountForm(null)) },
                                onConfigureFx = { push(Screen.Settings) },
                            )
                        }
                    }

                    BottomTab.REPORTS -> {
                        val state by reports.state.collectAsStateWithLifecycle()
                        ReportsScreen(
                            state = state,
                            onModeChange = reports::setMode,
                            onPreviousMonth = reports::previousMonth,
                            onNextMonth = reports::nextMonth,
                            onPreviousYear = reports::previousYear,
                            onNextYear = reports::nextYear,
                            onRangeChange = reports::setRange,
                            onRetry = reports::retry,
                            focus = reportFocus,
                        )
                    }

                    BottomTab.CREDIT -> {
                        val state by credit.state.collectAsStateWithLifecycle()
                        state?.let {
                            CreditScreen(
                                cards = it.cards,
                                purchases = it.pendingPurchases,
                                mainCurrency = it.mainCurrency,
                                onRegisterPurchase = { push(Screen.MovementForm(credit = true)) },
                                onPay = { purchase -> push(Screen.PayCredit(purchase)) },
                                onCardClick = { card -> push(Screen.CreditCardForm(card)) },
                                onNewCard = { push(Screen.CreditCardForm(null)) },
                                onPurchaseClick = { purchase ->
                                    push(Screen.MovementForm(purchase = purchase))
                                },
                            )
                        }
                    }
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

        is Screen.MovementForm -> {
            val state by movements.state.collectAsStateWithLifecycle()
            state?.let {
                // A purchase with a credit card needs no account until it is paid.
                if (it.accounts.isEmpty() && screen.editing == null && screen.purchase == null) {
                    NoticeScreen(
                        barTitle = stringResource(R.string.movement_new_title),
                        title = stringResource(R.string.error_no_accounts_title),
                        message = stringResource(R.string.error_no_accounts_message),
                        onClose = ::pop,
                    )
                } else {
                    MovementFormScreen(
                        accounts = it.accounts,
                        categories = it.categories,
                        creditCards = it.creditCards,
                        onClose = ::pop,
                        onSave = { draft -> movements.save(screen.editing, draft) { close(screen) } },
                        onOpenTransfer = { replaceTop(Screen.TransferForm()) },
                        editing = screen.editing,
                        initialCredit = screen.credit,
                        defaultAccount = it.defaultAccount,
                        onSavePurchase = { draft ->
                            movements.savePurchase(screen.purchase, draft) { close(screen) }
                        },
                        onDelete = {
                            screen.editing?.let { movement ->
                                movements.delete(movement) { close(screen) }
                            }
                        },
                        editingPurchase = screen.purchase,
                        onDeletePurchase = {
                            screen.purchase?.let { purchase ->
                                movements.deletePurchase(purchase) { close(screen) }
                            }
                        },
                    )
                }
            }
        }

        is Screen.TransferForm -> {
            val state by transfers.state.collectAsStateWithLifecycle()
            state?.let {
                // A new transfer starts on the first two accounts; both can be changed.
                val from = screen.editing?.from ?: it.accounts.getOrNull(0)
                val to = screen.editing?.to ?: it.accounts.getOrNull(1)
                if (from == null || to == null) {
                    NoticeScreen(
                        barTitle = stringResource(R.string.transfer_title),
                        title = stringResource(R.string.error_two_accounts_title),
                        message = stringResource(R.string.error_two_accounts_message),
                        onClose = ::pop,
                    )
                } else {
                    TransferScreen(
                        accounts = it.accounts,
                        initialFrom = from,
                        initialTo = to,
                        fx = it.fx,
                        onClose = ::pop,
                        onSave = { draft, sameCurrency ->
                            transfers.save(screen.editing, draft, sameCurrency) { close(screen) }
                        },
                        onOpenMovement = { replaceTop(Screen.MovementForm()) },
                        editing = screen.editing,
                        onDelete = {
                            screen.editing?.let { transfer ->
                                transfers.delete(transfer) { close(screen) }
                            }
                        },
                    )
                }
            }
        }

        is Screen.PayCredit -> {
            val state by credit.state.collectAsStateWithLifecycle()
            state?.let {
                PayCreditScreen(
                    purchase = screen.purchase,
                    accounts = it.accounts,
                    onBack = ::pop,
                    onConfirm = { payment -> credit.pay(payment) { close(screen) } },
                )
            }
        }

        is Screen.CreditCardForm -> {
            val state by credit.state.collectAsStateWithLifecycle()
            state?.let {
                CreditCardFormScreen(
                    card = screen.card,
                    defaultCurrency = it.mainCurrency,
                    fxConfigured = it.fxConfigured,
                    hasPendingPurchases = it.pendingPurchases.any { purchase ->
                        purchase.card.id == screen.card?.id
                    },
                    onClose = ::pop,
                    onSave = { draft -> credit.saveCard(screen.card, draft) { close(screen) } },
                    onArchive = {
                        screen.card?.let { card -> credit.archiveCard(card) { close(screen) } }
                    },
                )
            }
        }

        Screen.Settings -> {
            val state by settings.settings.collectAsStateWithLifecycle()
            state?.let {
                SettingsScreen(
                    fx = it.fx,
                    onFxChange = settings::setFx,
                    displayCurrency = it.displayCurrency,
                    onDisplayCurrencyChange = settings::setDisplayCurrency,
                    onBack = ::pop,
                    onManageCategories = { push(Screen.Categories) },
                    onLogout = onLogout,
                    email = email,
                )
            }
        }

        Screen.Movements -> {
            val state by movements.state.collectAsStateWithLifecycle()
            state?.let {
                MovementsScreen(
                    accounts = it.accounts,
                    entries = it.entries,
                    onBack = ::pop,
                    onEntryClick = ::openEntry,
                )
            }
        }

        is Screen.AccountForm -> {
            val state by accounts.state.collectAsStateWithLifecycle()
            state?.let {
                AccountFormScreen(
                    account = screen.account,
                    defaultCurrency = it.fx.main,
                    fxConfigured = it.fx.isConfigured,
                    onClose = ::pop,
                    onSave = { draft -> accounts.save(screen.account, draft) { close(screen) } },
                    onArchive = {
                        screen.account?.let { account -> accounts.archive(account) { close(screen) } }
                    },
                )
            }
        }

        Screen.Categories -> {
            val list by categories.categories.collectAsStateWithLifecycle()
            CategoriesScreen(
                categories = list,
                onBack = ::pop,
                onCategoryClick = { category -> push(Screen.CategoryForm(category, category.kind)) },
                onNewCategory = { kind -> push(Screen.CategoryForm(null, kind)) },
            )
        }

        is Screen.CategoryForm -> {
            val list by categories.categories.collectAsStateWithLifecycle()
            CategoryFormScreen(
                category = screen.category,
                onClose = ::pop,
                onSave = { draft -> categories.save(screen.category, draft) { close(screen) } },
                initialKind = screen.kind,
                existing = list,
                onArchive = {
                    screen.category?.let { category ->
                        categories.archive(category) { close(screen) }
                    }
                },
            )
        }
    }
}

@Composable
private fun HomeTab(
    load: Load<HomeUiState>,
    onDisplayCurrencyChange: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCredit: () -> Unit,
    onOpenReport: (MovementKind) -> Unit,
    onOpenMovements: () -> Unit,
    onEntryClick: (Entry) -> Unit,
) {
    val ready = (load as? Load.Ready)?.value
    HomeScreen(
        accounts = ready?.accounts.orEmpty(),
        fx = ready?.fx ?: PlaceholderFx,
        displayCurrency = ready?.displayCurrency ?: PEN,
        monthIncome = ready?.monthIncome ?: BigDecimal.ZERO,
        monthExpense = ready?.monthExpense ?: BigDecimal.ZERO,
        recentEntries = ready?.recentEntries.orEmpty(),
        pendingPurchases = ready?.pendingPurchases.orEmpty(),
        onDisplayCurrencyChange = onDisplayCurrencyChange,
        onOpenSettings = onOpenSettings,
        onOpenAccounts = onOpenAccounts,
        onOpenCredit = onOpenCredit,
        onOpenReport = onOpenReport,
        onOpenMovements = onOpenMovements,
        onEntryClick = onEntryClick,
        state = when {
            ready == null && load is Load.Failed -> HomeState.ERROR
            ready == null -> HomeState.LOADING
            ready.recentEntries.isEmpty() -> HomeState.EMPTY
            else -> HomeState.CONTENT
        },
        onRetry = onRetry,
    )
}

/** Shown instead of a form that cannot be filled yet (e.g. a movement without any account). */
@Composable
private fun NoticeScreen(barTitle: String, title: String, message: String, onClose: () -> Unit) {
    ScreenScaffold(
        header = {
            ModalTopBar(
                title = barTitle,
                navigationIcon = OrbitaIcons.Close,
                navigationLabel = stringResource(R.string.action_close),
                onNavigate = onClose,
            )
        },
    ) {
        StateMessage(OrbitaIcons.Alert, title, message = message)
    }
}

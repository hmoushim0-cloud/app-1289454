package sa.factory.manaraedtechsmarte.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import sa.factory.manaraedtechsmarte.domain.usecase.*
import sa.factory.manaraedtechsmarte.model.ManaraEdtechItem
import sa.factory.manaraedtechsmarte.model.ManaraEdtechOrder
import sa.factory.manaraedtechsmarte.model.ManaraEdtechTelemetry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ManaraEdtechUiState(
    val catalog: List<ManaraEdtechItem> = emptyList(),
    val filteredCatalog: List<ManaraEdtechItem> = emptyList(),
    val selectedCategory: String = "الكل",
    val cart: List<ManaraEdtechItem> = emptyList(),
    val telemetry: ManaraEdtechTelemetry? = null,
    val lastOrder: ManaraEdtechOrder? = null,
    val isCheckingOut: Boolean = false,
    val activeTab: String = "home"
)

class ManaraEdtechViewModel(
    private val getCatalogUseCase: GetCatalogUseCase,
    private val manageCartUseCase: ManageCartUseCase,
    private val processCheckoutUseCase: ProcessCheckoutUseCase,
    private val observeHardwareTelemetryUseCase: ObserveHardwareTelemetryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManaraEdtechUiState())
    val uiState: StateFlow<ManaraEdtechUiState> = _uiState.asStateFlow()

    init {
        // Observe Catalog
        getCatalogUseCase().onEach { list ->
            _uiState.update { it.copy(catalog = list, filteredCatalog = list) }
        }.launchIn(viewModelScope)

        // Observe Cart
        manageCartUseCase.cart.onEach { cartItems ->
            _uiState.update { it.copy(cart = cartItems) }
        }.launchIn(viewModelScope)

        // Observe Telemetry
        observeHardwareTelemetryUseCase().onEach { telem ->
            _uiState.update { it.copy(telemetry = telem) }
        }.launchIn(viewModelScope)
    }

    fun selectCategory(category: String) {
        _uiState.update { state ->
            val filtered = if (category == "الكل") state.catalog else state.catalog.filter { it.category == category }
            state.copy(selectedCategory = category, filteredCatalog = filtered)
        }
    }

    fun switchTab(tab: String) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun addToCart(item: ManaraEdtechItem) = manageCartUseCase.add(item)
    fun removeFromCart(item: ManaraEdtechItem) = manageCartUseCase.remove(item)

    fun checkout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingOut = true) }
            val order = processCheckoutUseCase()
            _uiState.update { it.copy(isCheckingOut = false, lastOrder = order, activeTab = "checkout") }
        }
    }
}

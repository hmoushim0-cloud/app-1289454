package sa.factory.manaraedtechsmarte.domain.usecase

import sa.factory.manaraedtechsmarte.domain.repository.ManaraEdtechRepository
import sa.factory.manaraedtechsmarte.model.ManaraEdtechItem
import sa.factory.manaraedtechsmarte.model.ManaraEdtechOrder
import sa.factory.manaraedtechsmarte.model.ManaraEdtechTelemetry
import kotlinx.coroutines.flow.Flow

class GetCatalogUseCase(private val repository: ManaraEdtechRepository) {
    operator fun invoke(): Flow<List<ManaraEdtechItem>> = repository.catalogFlow
}

class ManageCartUseCase(private val repository: ManaraEdtechRepository) {
    val cart = repository.activeCart
    fun add(item: ManaraEdtechItem) = repository.addToCart(item)
    fun remove(item: ManaraEdtechItem) = repository.removeFromCart(item)
    fun clear() = repository.clearCart()
}

class ProcessCheckoutUseCase(private val repository: ManaraEdtechRepository) {
    suspend operator fun invoke(paymentMethod: String = "Mada Contactless EMV"): ManaraEdtechOrder =
        repository.checkout(paymentMethod)
}

class ObserveHardwareTelemetryUseCase(private val repository: ManaraEdtechRepository) {
    operator fun invoke(): Flow<ManaraEdtechTelemetry> = repository.telemetryFlow
}

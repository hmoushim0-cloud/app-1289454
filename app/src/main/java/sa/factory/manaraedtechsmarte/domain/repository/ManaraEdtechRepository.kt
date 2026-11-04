package sa.factory.manaraedtechsmarte.domain.repository

import sa.factory.manaraedtechsmarte.model.ManaraEdtechItem
import sa.factory.manaraedtechsmarte.model.ManaraEdtechOrder
import sa.factory.manaraedtechsmarte.model.ManaraEdtechTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ManaraEdtechRepository {
    val catalogFlow: Flow<List<ManaraEdtechItem>>
    val activeCart: StateFlow<List<ManaraEdtechItem>>
    val telemetryFlow: Flow<ManaraEdtechTelemetry>
    
    fun addToCart(item: ManaraEdtechItem)
    fun removeFromCart(item: ManaraEdtechItem)
    fun clearCart()
    suspend fun checkout(paymentMethod: String): ManaraEdtechOrder
    suspend fun refreshCatalog()
}

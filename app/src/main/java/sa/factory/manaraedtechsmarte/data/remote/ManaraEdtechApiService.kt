package sa.factory.manaraedtechsmarte.data.remote

import sa.factory.manaraedtechsmarte.model.ManaraEdtechItem
import sa.factory.manaraedtechsmarte.model.ManaraEdtechOrder

interface ManaraEdtechApiService {
    suspend fun fetchCatalog(): List<ManaraEdtechItem>
    suspend fun submitOrder(order: ManaraEdtechOrder): Boolean
}

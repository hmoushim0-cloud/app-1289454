package sa.factory.manaraedtechsmarte.data.repository

import sa.factory.manaraedtechsmarte.domain.repository.ManaraEdtechRepository
import sa.factory.manaraedtechsmarte.hardware.HardwareSensorDriver
import sa.factory.manaraedtechsmarte.model.ManaraEdtechItem
import sa.factory.manaraedtechsmarte.model.ManaraEdtechOrder
import sa.factory.manaraedtechsmarte.model.ManaraEdtechTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ManaraEdtechRepositoryImpl(
    private val sensorDriver: HardwareSensorDriver
) : ManaraEdtechRepository {

    private val _catalog = MutableStateFlow<List<ManaraEdtechItem>>(listOf(
        ManaraEdtechItem("1", "الباقة التنفيذية المعتمدة", "تكامل مباشر مع Smart Stylus Digitizer 4096-Level + BLE Beacon", 180.0, "معتمد", "الخدمات الرئيسية"),
        ManaraEdtechItem("2", "خدمة المعالجة الفورية", "أداء أصيل بسرعة 60 إطار في الثانية", 95.0, "فوري", "المراقبة الحية"),
        ManaraEdtechItem("3", "باقة الصيانة والدعم السحابي", "تشفير كامل للبيانات المحلية ومزامنة آمنة", 250.0, "شامل", "التقارير الضريبية")
    ))
    override val catalogFlow: Flow<List<ManaraEdtechItem>> = _catalog.asStateFlow()

    private val _cart = MutableStateFlow<List<ManaraEdtechItem>>(emptyList())
    override val activeCart: StateFlow<List<ManaraEdtechItem>> = _cart.asStateFlow()

    override val telemetryFlow: Flow<ManaraEdtechTelemetry> = sensorDriver.observePeripheralStream()
        .map { frame ->
            ManaraEdtechTelemetry(
                peripheralName = frame.peripheralName,
                status = frame.connectionState,
                liveReading = frame.livePayload,
                batteryPct = 96,
                signalDbm = -54
            )
        }

    override fun addToCart(item: ManaraEdtechItem) {
        val current = _cart.value.toMutableList()
        current.add(item)
        _cart.value = current
    }

    override fun removeFromCart(item: ManaraEdtechItem) {
        val current = _cart.value.toMutableList()
        current.remove(item)
        _cart.value = current
    }

    override fun clearCart() {
        _cart.value = emptyList()
    }

    override suspend fun checkout(paymentMethod: String): ManaraEdtechOrder {
        val currentItems = _cart.value.toList()
        val subtotal = currentItems.sumOf { it.priceSar }
        val vat = subtotal * 0.15
        val total = subtotal + vat
        val order = ManaraEdtechOrder(
            orderId = "ORD-${System.currentTimeMillis() % 100000}",
            items = currentItems,
            subtotalSar = subtotal,
            vatSar = vat,
            totalSar = total,
            paymentMethod = paymentMethod
        )
        clearCart()
        return order
    }

    override suspend fun refreshCatalog() {
        // Offline-first sync logic
    }
}

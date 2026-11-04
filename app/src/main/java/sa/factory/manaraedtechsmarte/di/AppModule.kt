package sa.factory.manaraedtechsmarte.di

import sa.factory.manaraedtechsmarte.data.repository.ManaraEdtechRepositoryImpl
import sa.factory.manaraedtechsmarte.domain.repository.ManaraEdtechRepository
import sa.factory.manaraedtechsmarte.domain.usecase.*
import sa.factory.manaraedtechsmarte.hardware.HardwareSensorDriver
import sa.factory.manaraedtechsmarte.ui.viewmodel.ManaraEdtechViewModel

class DiContainer {
    val sensorDriver = HardwareSensorDriver()
    val repository: ManaraEdtechRepository = ManaraEdtechRepositoryImpl(sensorDriver)

    val getCatalogUseCase = GetCatalogUseCase(repository)
    val manageCartUseCase = ManageCartUseCase(repository)
    val processCheckoutUseCase = ProcessCheckoutUseCase(repository)
    val observeHardwareTelemetryUseCase = ObserveHardwareTelemetryUseCase(repository)

    fun provideViewModel(): ManaraEdtechViewModel = ManaraEdtechViewModel(
        getCatalogUseCase,
        manageCartUseCase,
        processCheckoutUseCase,
        observeHardwareTelemetryUseCase
    )
}

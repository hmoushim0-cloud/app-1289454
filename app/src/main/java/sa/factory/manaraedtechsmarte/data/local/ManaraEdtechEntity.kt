package sa.factory.manaraedtechsmarte.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import sa.factory.manaraedtechsmarte.model.ManaraEdtechItem

@Entity(tableName = "smart_education_items_cache")
data class ManaraEdtechEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val priceSar: Double,
    val badge: String,
    val category: String,
    val status: String
) {
    fun toDomain(): ManaraEdtechItem = ManaraEdtechItem(
        id = id,
        title = title,
        description = description,
        priceSar = priceSar,
        badge = badge,
        category = category,
        status = status
    )
}

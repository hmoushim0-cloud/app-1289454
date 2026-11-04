package sa.factory.manaraedtechsmarte.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ManaraEdtechEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smart_educationDao(): ManaraEdtechDao
}

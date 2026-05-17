package com.nammanala

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Entity(tableName = "breach_reports")
data class BreachReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoUri: String,
    val lat: Double,
    val lng: Double,
    val createdAtEpochMs: Long,
    val nearestName: String,
    val nearestDistanceMeters: Double,
)

@Entity(tableName = "water_status")
data class WaterStatusEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val village: String,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "maintenance")
data class MaintenanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectionName: String,
    val nextCleaning: String,
    val status: String,
)

@Entity(tableName = "silt_alerts")
data class SiltAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val note: String,
    val createdAtEpochMs: Long,
)

@Dao
interface NammaNalaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaterStatus(item: WaterStatusEntity): Long

    @Query("SELECT * FROM water_status ORDER BY updatedAtEpochMs DESC")
    fun waterStatusFeed(): Flow<List<WaterStatusEntity>>

    @Query("UPDATE water_status SET updatedAtEpochMs = :t WHERE id = :id")
    suspend fun bumpWaterStatus(id: Long, t: Long)

    @Insert
    suspend fun insertBreach(report: BreachReportEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaintenance(items: List<MaintenanceEntity>)

    @Query("SELECT * FROM maintenance ORDER BY id ASC")
    fun maintenance(): Flow<List<MaintenanceEntity>>

    @Insert
    suspend fun insertSiltAlert(item: SiltAlertEntity): Long

    @Query("SELECT * FROM silt_alerts ORDER BY createdAtEpochMs DESC")
    fun siltAlerts(): Flow<List<SiltAlertEntity>>

    @Query("SELECT COUNT(*) FROM maintenance")
    suspend fun maintenanceCount(): Int

    @Query("SELECT COUNT(*) FROM water_status")
    suspend fun waterStatusCount(): Int
}

@Database(
    entities = [
        BreachReportEntity::class,
        WaterStatusEntity::class,
        MaintenanceEntity::class,
        SiltAlertEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): NammaNalaDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, AppDb::class.java, "namma_nala.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}

class NammaNalaRepository(private val db: AppDb) {
    private val dao = db.dao()

    val waterStatusFeed: Flow<List<WaterStatusEntity>> = dao.waterStatusFeed()
    val maintenanceItems: Flow<List<MaintenanceEntity>> = dao.maintenance()
    val siltAlerts: Flow<List<SiltAlertEntity>> = dao.siltAlerts()

    suspend fun postWaterStatus(village: String) {
        dao.insertWaterStatus(
            WaterStatusEntity(
                village = village,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun bumpWaterStatus(id: Long) {
        dao.bumpWaterStatus(id, System.currentTimeMillis())
    }

    suspend fun submitBreach(photoUri: String, lat: Double, lng: Double, nearestName: String, nearestDistanceMeters: Double) {
        dao.insertBreach(
            BreachReportEntity(
                photoUri = photoUri,
                lat = lat,
                lng = lng,
                createdAtEpochMs = System.currentTimeMillis(),
                nearestName = nearestName,
                nearestDistanceMeters = nearestDistanceMeters,
            ),
        )
    }

    suspend fun addSiltAlert(note: String) {
        dao.insertSiltAlert(
            SiltAlertEntity(
                note = note,
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun seedIfEmpty() {
        val now = LocalDate.now()
        val df = DateTimeFormatter.ofPattern("dd MMM yyyy")

        if (dao.maintenanceCount() == 0) {
            dao.insertMaintenance(
                listOf(
                    MaintenanceEntity(sectionName = "Primary Canal: Section A", nextCleaning = now.plusDays(7).format(df), status = "Scheduled"),
                    MaintenanceEntity(sectionName = "Primary Canal: Section B", nextCleaning = now.plusDays(14).format(df), status = "Scheduled"),
                    MaintenanceEntity(sectionName = "Secondary Canal: Branch 1", nextCleaning = now.plusDays(3).format(df), status = "Urgent"),
                    MaintenanceEntity(sectionName = "Secondary Canal: Branch 2", nextCleaning = now.plusDays(10).format(df), status = "Scheduled"),
                ),
            )
        }

        if (dao.waterStatusCount() == 0) {
            val t = System.currentTimeMillis()
            dao.insertWaterStatus(WaterStatusEntity(village = "Village X", updatedAtEpochMs = t - 3 * 60 * 60 * 1000))
            dao.insertWaterStatus(WaterStatusEntity(village = "Village Y", updatedAtEpochMs = t - 26 * 60 * 60 * 1000))
        }
    }
}

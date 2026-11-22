package cz.rodina.mordor

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period

/** Čtení denních kroků z Health Connect (sem je zapisuje Garmin Connect i Mi Fitness). */
class Health(private val context: Context) {
    val permissions: Set<String> = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    fun status(): Int = HealthConnectClient.getSdkStatus(context)
    fun available(): Boolean = status() == HealthConnectClient.SDK_AVAILABLE

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasPermission(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun dailySteps(from: LocalDate, to: LocalDate): Map<String, Long> {
        val res = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
        return res.associate { it.startTime.toLocalDate().toString() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
    }
}

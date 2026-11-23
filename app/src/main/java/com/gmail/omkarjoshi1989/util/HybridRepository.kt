package com.gmail.omkarjoshi1989.util

import com.gmail.omkarjoshi1989.BuildConfig
import com.gmail.omkarjoshi1989.model.HomeOfficeWeekend
import com.gmail.omkarjoshi1989.model.HybridDay
import com.gmail.omkarjoshi1989.model.HybridEntry
import com.gmail.omkarjoshi1989.model.PlannedHybridMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.YearMonth

/** Minimal Supabase PostgREST client for the `Hybrid` table (no extra dependencies). */
object HybridRepository {

    private const val TABLE = "Hybrid"

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    /** Default planned mode for a freshly created row. */
    fun defaultMode(date: LocalDate): PlannedHybridMode =
        if (HybridDay.of(date) == HybridDay.Saturday || HybridDay.of(date) == HybridDay.Sunday)
            PlannedHybridMode.WEEKEND else PlannedHybridMode.NONE

    /** Previous, current and next month relative to [center]. */
    fun threeMonths(center: YearMonth = YearMonth.now()): List<YearMonth> =
        listOf(center.minusMonths(1), center, center.plusMonths(1))

    /**
     * Ensures rows exist for every day of previous/current/next month, then returns all of them
     * ordered by date.
     */
    suspend fun loadThreeMonths(center: YearMonth = YearMonth.now()): List<HybridEntry> =
        withContext(Dispatchers.IO) {
            val months = threeMonths(center)
            val from = months.first().atDay(1)
            val to = months.last().atEndOfMonth()

            var rows = fetch(from, to)
            val existing = rows.map { it.date }.toSet()
            val missing = generateSequence(from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(to) }
                .filter { it !in existing }
                .toList()
            if (missing.isNotEmpty()) {
                insertMissing(missing)
                rows = fetch(from, to)
            }
            val withSerials = months.flatMap { ym -> syncSerials(rows.filter { YearMonth.from(it.date) == ym }) }
            withSerials.sortedBy { it.date }
        }

    /** Changes planned_mode (and matching H_O_W) of one day, then refreshes serial numbers of that month. */
    suspend fun updateMode(
        entry: HybridEntry,
        mode: PlannedHybridMode,
        monthEntries: List<HybridEntry>
    ): List<HybridEntry> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("planned_mode", mode.dbValue)
            .put("H_O_W", mode.how?.dbValue ?: JSONObject.NULL)
        patch("date_of_month=eq.${entry.date}", body)
        val updated = monthEntries.map {
            if (it.date == entry.date) it.copy(mode = mode, how = mode.how) else it
        }
        syncSerials(updated)
    }

    /**
     * Serial number = running count within the month. OFFICE, LEAVE and HOLIDAY all share the
     * OFFICE counter (e.g. OFFICE, LEAVE, OFFICE → 1, 2, 3). Other modes count separately.
     * Persists changed values and returns the new list.
     */
    private fun syncSerials(monthRows: List<HybridEntry>): List<HybridEntry> {
        val counters = HashMap<PlannedHybridMode, Int>()
        return monthRows.sortedBy { it.date }.map { e ->
            val group = when (e.mode) {
                PlannedHybridMode.LEAVE, PlannedHybridMode.HOLIDAY -> PlannedHybridMode.OFFICE
                else -> e.mode
            }
            val n = (counters[group] ?: 0) + 1
            counters[group] = n
            val serial = n.toString()
            if (e.serial != serial) {
                try {
                    patch("date_of_month=eq.${e.date}", JSONObject().put("office_or_home_serial_number", serial))
                } catch (_: Exception) { /* shown value is still correct; retried on next load */ }
            }
            e.copy(serial = serial)
        }
    }

    private fun fetch(from: LocalDate, to: LocalDate): List<HybridEntry> {
        val q = "select=*&date_of_month=gte.$from&date_of_month=lte.$to&order=date_of_month.asc"
        val json = JSONArray(request("GET", q, null, emptyMap()))
        return (0 until json.length()).mapNotNull { i ->
            val o = json.getJSONObject(i)
            val date = LocalDate.parse(o.getString("date_of_month"))
            val mode = PlannedHybridMode.fromDb(o.optString("planned_mode")) ?: return@mapNotNull null
            HybridEntry(
                date = date,
                day = HybridDay.fromDb(o.optString("day_of_week")) ?: HybridDay.of(date),
                mode = mode,
                how = HomeOfficeWeekend.fromDb(o.optStringOrNull("H_O_W")),
                serial = o.optStringOrNull("office_or_home_serial_number"),
                status = o.optStringOrNull("status")
            )
        }
    }

    private fun insertMissing(dates: List<LocalDate>) {
        val arr = JSONArray()
        dates.forEach { d ->
            val mode = defaultMode(d)
            arr.put(
                JSONObject()
                    .put("date_of_month", d.toString())
                    .put("day_of_week", HybridDay.of(d).dbValue)
                    .put("planned_mode", mode.dbValue)
                    .put("H_O_W", mode.how?.dbValue ?: JSONObject.NULL)
            )
        }
        request(
            "POST", "", arr.toString(),
            mapOf("Prefer" to "resolution=ignore-duplicates,return=minimal")
        )
    }

    private fun patch(filter: String, body: JSONObject) {
        request("PATCH", filter, body.toString(), mapOf("Prefer" to "return=minimal"))
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key) || !has(key)) null else getString(key)

    private fun request(method: String, query: String, body: String?, headers: Map<String, String>): String {
        check(isConfigured) { "Supabase not configured. Add supabase.url and supabase.anonKey to local.properties." }
        val base = BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/" + URLEncoder.encode(TABLE, "UTF-8")
        val url = URL(if (query.isEmpty()) base else "$base?$query")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            // New-style publishable keys (sb_publishable_...) are not JWTs; only legacy JWT keys go in Authorization.
            if (!BuildConfig.SUPABASE_ANON_KEY.startsWith("sb_")) {
                conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            }
            conn.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Supabase error $code: $text")
            return text
        } finally {
            conn.disconnect()
        }
    }
}


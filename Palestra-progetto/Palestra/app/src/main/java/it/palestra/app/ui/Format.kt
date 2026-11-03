package it.palestra.app.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val loc = Locale.ITALIAN
    private val dec = DecimalFormat("#.##", DecimalFormatSymbols(Locale.ITALY))
    private val dec1 = DecimalFormat("#.#", DecimalFormatSymbols(Locale.ITALY))
    private val thousands = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.ITALY))

    fun num(v: Double): String = dec.format(v)
    fun kg(v: Double): String = "${num(v)} kg"

    fun volume(kg: Double): String = when {
        kg >= 10_000 -> "${dec1.format(kg / 1000)} t"
        else -> "${thousands.format(kg)} kg"
    }

    fun parseDecimal(s: String): Double? = s.trim().replace(',', '.').let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() }
    fun parseInt(s: String): Int? = s.trim().let { if (it.isEmpty()) 0 else it.toIntOrNull() }

    /** 84 → "1:24" */
    fun clock(totalSec: Int): String = "%d:%02d".format(totalSec / 60, totalSec % 60)
    fun clockMs(ms: Long): String = clock(((ms + 999) / 1000).toInt())

    fun elapsed(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    fun minutes(min: Int): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "$min min"

    fun rest(sec: Int): String = if (sec % 60 == 0) "${sec / 60}:00" else clock(sec)

    private val f_dayLong = DateTimeFormatter.ofPattern("EEEE d MMMM", loc)
    private val f_dayShort = DateTimeFormatter.ofPattern("EEE d MMM", loc)
    private val f_dayShortYear = DateTimeFormatter.ofPattern("d MMM yyyy", loc)
    private val f_dm = DateTimeFormatter.ofPattern("d MMM", loc)
    private val f_monthYear = DateTimeFormatter.ofPattern("LLLL yyyy", loc)

    fun cap(s: String) = s.replaceFirstChar { c -> c.titlecase(loc) }
    fun dayLong(d: LocalDate) = cap(f_dayLong.format(d))
    fun dayShort(d: LocalDate) = cap(f_dayShort.format(d))
    fun dayMonth(d: LocalDate) = f_dm.format(d)
    fun dateWithYear(d: LocalDate) = f_dayShortYear.format(d)
    fun monthYear(d: LocalDate) = cap(f_monthYear.format(d))

    val weekLetters = listOf("L", "M", "M", "G", "V", "S", "D")
    val weekShort = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
    val weekLong = listOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")

    fun relativeDay(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
        today -> "Oggi"
        today.minusDays(1) -> "Ieri"
        else -> if (d.year == today.year) dayShort(d) else dateWithYear(d)
    }
}

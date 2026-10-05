// Núcleo sencillo para explicar la lógica de la app de hábitos.
// No es todavía una app Android completa: contiene los cálculos principales.

enum class Dia {
    LUNES, MARTES, MIERCOLES, JUEVES, VIERNES, SABADO, DOMINGO
}

data class HorarioDia(
    val trabajoMin: Int = 0,
    val suenoMin: Int = 0,
    val trasladosMin: Int = 0,
    val otrosCompromisosMin: Int = 0
) {
    fun minutosOcupados(): Int = trabajoMin + suenoMin + trasladosMin + otrosCompromisosMin

    fun minutosRestantes(): Int {
        val totalDelDia = 24 * 60
        require(minutosOcupados() <= totalDelDia) {
            "Las actividades no pueden sumar más de 24 horas."
        }
        return totalDelDia - minutosOcupados()
    }
}

data class ResultadoHabito(
    val nombre: String,
    val vecesRealizadas: Int,
    val vecesPlaneadas: Int,
    val importancia: Int // de 1 a 5
)

fun clasificarHabito(habito: ResultadoHabito): String {
    require(habito.vecesPlaneadas > 0) { "Indica cuántas veces planeabas hacerlo." }
    require(habito.vecesRealizadas in 0..habito.vecesPlaneadas)
    require(habito.importancia in 1..5)

    val avance = habito.vecesRealizadas.toDouble() / habito.vecesPlaneadas
    return when {
        avance >= 0.80 -> "Avanzado"
        avance >= 0.40 -> "En progreso"
        else -> "Conviene reforzarlo"
    }
}

fun main() {
    // Ejemplo: el sábado se trabaja 4 h, se duerme 8 h,
    // hay 1 h de traslados y 2 h de otros compromisos.
    val sabado = HorarioDia(
        trabajoMin = 4 * 60,
        suenoMin = 8 * 60,
        trasladosMin = 60,
        otrosCompromisosMin = 2 * 60
    )

    val horasRestantes = sabado.minutosRestantes() / 60.0
    println("Sábado: quedan $horasRestantes horas antes de asignar tiempo a hábitos.")

    val lectura = ResultadoHabito(
        nombre = "Lectura",
        vecesRealizadas = 2,
        vecesPlaneadas = 5,
        importancia = 4
    )
    println("${lectura.nombre}: ${clasificarHabito(lectura)}")
}

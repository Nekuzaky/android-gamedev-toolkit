package dev.nekuz.gamemath

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val C1 = 1.70158
private const val C2 = C1 * 1.525
private const val C3 = C1 + 1.0
private const val C4 = (2.0 * PI) / 3.0
private const val C5 = (2.0 * PI) / 4.5

private fun bounceOut(start: Double): Double {
    val n = 7.5625
    val d = 2.75
    var t = start
    return when {
        t < 1.0 / d -> n * t * t
        t < 2.0 / d -> {
            t -= 1.5 / d
            n * t * t + 0.75
        }
        t < 2.5 / d -> {
            t -= 2.25 / d
            n * t * t + 0.9375
        }
        else -> {
            t -= 2.625 / d
            n * t * t + 0.984375
        }
    }
}

enum class Easing(
    val label: String,
    val method: String,
    val body: String,
    private val curve: (Double) -> Double,
) {
    LINEAR("Linear", "EaseLinear", "return t;", { it }),
    SINE_IN("Sine In", "EaseInSine", "return 1f - Mathf.Cos(t * Mathf.PI / 2f);", { 1 - cos(it * PI / 2) }),
    SINE_OUT("Sine Out", "EaseOutSine", "return Mathf.Sin(t * Mathf.PI / 2f);", { sin(it * PI / 2) }),
    SINE_IN_OUT("Sine In Out", "EaseInOutSine", "return -(Mathf.Cos(Mathf.PI * t) - 1f) / 2f;", { -(cos(PI * it) - 1) / 2 }),
    QUAD_IN("Quad In", "EaseInQuad", "return t * t;", { it * it }),
    QUAD_OUT("Quad Out", "EaseOutQuad", "return 1f - (1f - t) * (1f - t);", { 1 - (1 - it) * (1 - it) }),
    QUAD_IN_OUT(
        "Quad In Out",
        "EaseInOutQuad",
        "return t < 0.5f ? 2f * t * t : 1f - Mathf.Pow(-2f * t + 2f, 2f) / 2f;",
        { if (it < 0.5) 2 * it * it else 1 - (-2 * it + 2).pow(2) / 2 },
    ),
    CUBIC_IN("Cubic In", "EaseInCubic", "return t * t * t;", { it * it * it }),
    CUBIC_OUT("Cubic Out", "EaseOutCubic", "return 1f - Mathf.Pow(1f - t, 3f);", { 1 - (1 - it).pow(3) }),
    CUBIC_IN_OUT(
        "Cubic In Out",
        "EaseInOutCubic",
        "return t < 0.5f ? 4f * t * t * t : 1f - Mathf.Pow(-2f * t + 2f, 3f) / 2f;",
        { if (it < 0.5) 4 * it * it * it else 1 - (-2 * it + 2).pow(3) / 2 },
    ),
    QUART_IN("Quart In", "EaseInQuart", "return t * t * t * t;", { it.pow(4) }),
    QUART_OUT("Quart Out", "EaseOutQuart", "return 1f - Mathf.Pow(1f - t, 4f);", { 1 - (1 - it).pow(4) }),
    QUART_IN_OUT(
        "Quart In Out",
        "EaseInOutQuart",
        "return t < 0.5f ? 8f * t * t * t * t : 1f - Mathf.Pow(-2f * t + 2f, 4f) / 2f;",
        { if (it < 0.5) 8 * it.pow(4) else 1 - (-2 * it + 2).pow(4) / 2 },
    ),
    QUINT_IN("Quint In", "EaseInQuint", "return t * t * t * t * t;", { it.pow(5) }),
    QUINT_OUT("Quint Out", "EaseOutQuint", "return 1f - Mathf.Pow(1f - t, 5f);", { 1 - (1 - it).pow(5) }),
    QUINT_IN_OUT(
        "Quint In Out",
        "EaseInOutQuint",
        "return t < 0.5f ? 16f * t * t * t * t * t : 1f - Mathf.Pow(-2f * t + 2f, 5f) / 2f;",
        { if (it < 0.5) 16 * it.pow(5) else 1 - (-2 * it + 2).pow(5) / 2 },
    ),
    EXPO_IN("Expo In", "EaseInExpo", "return t <= 0f ? 0f : Mathf.Pow(2f, 10f * t - 10f);", { 2.0.pow(10 * it - 10) }),
    EXPO_OUT("Expo Out", "EaseOutExpo", "return t >= 1f ? 1f : 1f - Mathf.Pow(2f, -10f * t);", { 1 - 2.0.pow(-10 * it) }),
    EXPO_IN_OUT(
        "Expo In Out",
        "EaseInOutExpo",
        "if (t <= 0f) return 0f;\n    if (t >= 1f) return 1f;\n    return t < 0.5f ? Mathf.Pow(2f, 20f * t - 10f) / 2f : (2f - Mathf.Pow(2f, -20f * t + 10f)) / 2f;",
        { if (it < 0.5) 2.0.pow(20 * it - 10) / 2 else (2 - 2.0.pow(-20 * it + 10)) / 2 },
    ),
    CIRC_IN("Circ In", "EaseInCirc", "return 1f - Mathf.Sqrt(1f - t * t);", { 1 - sqrt(1 - it * it) }),
    CIRC_OUT("Circ Out", "EaseOutCirc", "return Mathf.Sqrt(1f - (t - 1f) * (t - 1f));", { sqrt(1 - (it - 1) * (it - 1)) }),
    CIRC_IN_OUT(
        "Circ In Out",
        "EaseInOutCirc",
        "return t < 0.5f ? (1f - Mathf.Sqrt(1f - 4f * t * t)) / 2f : (Mathf.Sqrt(1f - Mathf.Pow(-2f * t + 2f, 2f)) + 1f) / 2f;",
        { if (it < 0.5) (1 - sqrt(1 - 4 * it * it)) / 2 else (sqrt(1 - (-2 * it + 2).pow(2)) + 1) / 2 },
    ),
    BACK_IN(
        "Back In",
        "EaseInBack",
        "const float c1 = 1.70158f;\n    const float c3 = c1 + 1f;\n    return c3 * t * t * t - c1 * t * t;",
        { C3 * it * it * it - C1 * it * it },
    ),
    BACK_OUT(
        "Back Out",
        "EaseOutBack",
        "const float c1 = 1.70158f;\n    const float c3 = c1 + 1f;\n    float u = t - 1f;\n    return 1f + c3 * u * u * u + c1 * u * u;",
        { 1 + C3 * (it - 1).pow(3) + C1 * (it - 1).pow(2) },
    ),
    BACK_IN_OUT(
        "Back In Out",
        "EaseInOutBack",
        "const float c2 = 1.70158f * 1.525f;\n    return t < 0.5f\n        ? (Mathf.Pow(2f * t, 2f) * ((c2 + 1f) * 2f * t - c2)) / 2f\n        : (Mathf.Pow(2f * t - 2f, 2f) * ((c2 + 1f) * (t * 2f - 2f) + c2) + 2f) / 2f;",
        {
            if (it < 0.5) {
                ((2 * it).pow(2) * ((C2 + 1) * 2 * it - C2)) / 2
            } else {
                ((2 * it - 2).pow(2) * ((C2 + 1) * (it * 2 - 2) + C2) + 2) / 2
            }
        },
    ),
    ELASTIC_IN(
        "Elastic In",
        "EaseInElastic",
        "if (t <= 0f) return 0f;\n    if (t >= 1f) return 1f;\n    const float c4 = 2f * Mathf.PI / 3f;\n    return -Mathf.Pow(2f, 10f * t - 10f) * Mathf.Sin((10f * t - 10.75f) * c4);",
        { -(2.0.pow(10 * it - 10)) * sin((10 * it - 10.75) * C4) },
    ),
    ELASTIC_OUT(
        "Elastic Out",
        "EaseOutElastic",
        "if (t <= 0f) return 0f;\n    if (t >= 1f) return 1f;\n    const float c4 = 2f * Mathf.PI / 3f;\n    return Mathf.Pow(2f, -10f * t) * Mathf.Sin((10f * t - 0.75f) * c4) + 1f;",
        { 2.0.pow(-10 * it) * sin((10 * it - 0.75) * C4) + 1 },
    ),
    ELASTIC_IN_OUT(
        "Elastic In Out",
        "EaseInOutElastic",
        "if (t <= 0f) return 0f;\n    if (t >= 1f) return 1f;\n    const float c5 = 2f * Mathf.PI / 4.5f;\n    return t < 0.5f\n        ? -(Mathf.Pow(2f, 20f * t - 10f) * Mathf.Sin((20f * t - 11.125f) * c5)) / 2f\n        : Mathf.Pow(2f, -20f * t + 10f) * Mathf.Sin((20f * t - 11.125f) * c5) / 2f + 1f;",
        {
            if (it < 0.5) {
                -(2.0.pow(20 * it - 10) * sin((20 * it - 11.125) * C5)) / 2
            } else {
                2.0.pow(-20 * it + 10) * sin((20 * it - 11.125) * C5) / 2 + 1
            }
        },
    ),
    BOUNCE_IN("Bounce In", "EaseInBounce", "return 1f - EaseOutBounce(1f - t);", { 1 - bounceOut(1 - it) }),
    BOUNCE_OUT(
        "Bounce Out",
        "EaseOutBounce",
        "const float n = 7.5625f;\n    const float d = 2.75f;\n    if (t < 1f / d) return n * t * t;\n    if (t < 2f / d)\n    {\n        t -= 1.5f / d;\n        return n * t * t + 0.75f;\n    }\n    if (t < 2.5f / d)\n    {\n        t -= 2.25f / d;\n        return n * t * t + 0.9375f;\n    }\n    t -= 2.625f / d;\n    return n * t * t + 0.984375f;",
        { bounceOut(it) },
    ),
    BOUNCE_IN_OUT(
        "Bounce In Out",
        "EaseInOutBounce",
        "return t < 0.5f\n        ? (1f - EaseOutBounce(1f - 2f * t)) / 2f\n        : (1f + EaseOutBounce(2f * t - 1f)) / 2f;",
        { if (it < 0.5) (1 - bounceOut(1 - 2 * it)) / 2 else (1 + bounceOut(2 * it - 1)) / 2 },
    ),
    ;

    fun eval(progress: Double): Double {
        if (progress.isNaN() || progress <= 0.0) return 0.0
        if (progress >= 1.0) return 1.0
        return curve(progress) + 0.0
    }

    fun csharp(): String {
        val own = "public static float $method(float t)\n{\n    t = Mathf.Clamp01(t);\n    $body\n}\n"
        return if (this == BOUNCE_IN || this == BOUNCE_IN_OUT) "$own\n${BOUNCE_OUT.csharp()}" else own
    }

    fun samples(count: Int): DoubleArray {
        val size = count.coerceIn(2, MAX_SAMPLES)
        return DoubleArray(size) { eval(it.toDouble() / (size - 1).toDouble()) }
    }

    companion object {
        const val MAX_SAMPLES = 1024

        fun fromLabel(label: String?): Easing = entries.firstOrNull { it.label == label } ?: LINEAR
    }
}

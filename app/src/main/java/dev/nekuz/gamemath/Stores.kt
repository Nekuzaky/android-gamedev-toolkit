package dev.nekuz.gamemath

import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class MonteCarloState(
    val running: Boolean = false,
    val percent: Int = 0,
    val plan: MonteCarloPlan? = null,
    val result: MonteCarloResult? = null,
    val failed: Boolean = false,
)

object MonteCarloStore {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<() -> Unit>()
    private var flag: AtomicBoolean? = null
    private var generation = 0

    var state = MonteCarloState()
        private set

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun start(plan: MonteCarloPlan) {
        flag?.set(true)
        val cancel = AtomicBoolean(false)
        flag = cancel
        val job = ++generation
        state = MonteCarloState(running = true, plan = plan)
        notifyListeners()
        val worker = Thread({ work(plan, cancel, job) }, "gamemath-montecarlo")
        worker.isDaemon = true
        worker.priority = Thread.MIN_PRIORITY
        worker.start()
    }

    fun cancel() {
        flag?.set(true)
    }

    private fun work(plan: MonteCarloPlan, cancel: AtomicBoolean, job: Int) {
        var last = 0
        val outcome = try {
            MonteCarlo.run(plan, { cancel.get() }) { fraction ->
                val percent = (fraction * 100.0).toInt().coerceIn(0, 100)
                if (percent != last) {
                    last = percent
                    main.post { progress(job, percent) }
                }
            }
        } catch (e: RuntimeException) {
            null
        }
        main.post { finish(job, plan, outcome) }
    }

    private fun progress(job: Int, percent: Int) {
        if (job != generation || !state.running) return
        state = state.copy(percent = percent)
        notifyListeners()
    }

    private fun finish(job: Int, plan: MonteCarloPlan, outcome: MonteCarloResult?) {
        if (job != generation) return
        flag = null
        state = MonteCarloState(running = false, percent = 100, plan = plan, result = outcome, failed = outcome == null)
        notifyListeners()
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it() }
    }
}

object PresetRepository {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "gamemath-presets").apply { isDaemon = true }
    }

    fun load(file: File, done: (PresetLoad) -> Unit) {
        worker.execute {
            val load = PresetFile.inspect(file)
            main.post { done(load) }
        }
    }

    fun save(file: File, preset: Preset, done: (SaveOutcome) -> Unit) {
        worker.execute {
            val outcome = PresetFile.saveMerged(file, preset)
            main.post { done(outcome) }
        }
    }

    fun delete(file: File, name: String, done: (Boolean) -> Unit) {
        worker.execute {
            val saved = PresetFile.removeFrom(file, name)
            main.post { done(saved) }
        }
    }
}

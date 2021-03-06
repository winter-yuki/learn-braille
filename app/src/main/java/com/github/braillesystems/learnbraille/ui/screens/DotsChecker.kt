package com.github.braillesystems.learnbraille.ui.screens

import android.os.Vibrator
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.github.braillesystems.learnbraille.data.entities.BrailleDots
import com.github.braillesystems.learnbraille.data.repository.PreferenceRepository
import com.github.braillesystems.learnbraille.ui.brailletrainer.BrailleTrainer
import com.github.braillesystems.learnbraille.ui.views.*
import com.github.braillesystems.learnbraille.utils.checkedBuzz
import org.koin.core.KoinComponent
import org.koin.core.get
import timber.log.Timber

/**
 * Represents state machine that serves input tasks.
 */
interface DotsChecker : KoinComponent {

    /**
     * User pressed `next` button.
     */
    val eventCorrect: LiveData<Boolean>
    fun onCorrectComplete()

    /**
     * Correctness listening on the fly.
     */
    val eventSoftCorrect: LiveData<Boolean>
    fun onSoftCorrectComplete()

    val eventIncorrect: LiveData<Boolean>
    fun onIncorrectComplete()

    val eventHint: LiveData<BrailleDots?>
    fun onHintComplete()

    val eventPassHint: LiveData<Boolean>
    fun onPassHintComplete()

    val state: State

    /**
     * Next button pressed.
     */
    fun onCheck()

    /**
     * On the fly check.
     */
    fun onSoftCheck()
    fun onHint()

    enum class State {
        INPUT, HINT
    }
}

interface MutableDotsChecker : DotsChecker {

    var getEnteredDots: () -> BrailleDots
    var getExpectedDots: () -> BrailleDots?

    var onCheckHandler: () -> Unit
    var onSoftCheckHandler: () -> Unit
    var onCorrectHandler: () -> Unit
    var onSoftCorrectHandler: () -> Unit
    var onIncorrectHandler: () -> Unit
    var onHintHandler: () -> Unit
    var onPassHintHandler: () -> Unit

    companion object {
        fun create(): MutableDotsChecker = DotsCheckerImpl()
    }
}

/**
 * Initialize lateinit callbacks firstly.
 *
 * Callbacks are called before changing state,
 * so they can access previous state (the next one is obvious).
 */
private class DotsCheckerImpl : MutableDotsChecker {

    override lateinit var getEnteredDots: () -> BrailleDots
    override lateinit var getExpectedDots: () -> BrailleDots?

    override var onCheckHandler: () -> Unit = {}
    override var onSoftCheckHandler: () -> Unit = {}
    override var onCorrectHandler: () -> Unit = {}
    override var onSoftCorrectHandler: () -> Unit = {}
    override var onIncorrectHandler: () -> Unit = {}
    override var onHintHandler: () -> Unit = {}
    override var onPassHintHandler: () -> Unit = {}

    private val _eventCorrect = MutableLiveData<Boolean>()
    override val eventCorrect: LiveData<Boolean>
        get() = _eventCorrect

    private val _eventSoftCorrect = MutableLiveData<Boolean>()
    override val eventSoftCorrect: LiveData<Boolean>
        get() = _eventSoftCorrect

    private val _eventIncorrect = MutableLiveData<Boolean>()
    override val eventIncorrect: LiveData<Boolean>
        get() = _eventIncorrect

    private val _eventHint = MutableLiveData<BrailleDots>()
    override val eventHint: LiveData<BrailleDots?>
        get() = _eventHint

    private val _eventPassHint = MutableLiveData<Boolean>()
    override val eventPassHint: LiveData<Boolean>
        get() = _eventPassHint

    private val enteredDots: BrailleDots
        get() =
            if (::getEnteredDots.isInitialized) getEnteredDots()
            else error("getEnteredDots property should be initialized")

    private val expectedDots: BrailleDots?
        get() =
            if (::getExpectedDots.isInitialized) getExpectedDots()
            else error("getExpectedDots should be initialized")

    private val isCorrect: Boolean
        get() = (enteredDots == expectedDots).also {
            Timber.i(
                if (it) "Correct"
                else "Incorrect: entered = $enteredDots, expected = $expectedDots"
            )
        }

    override var state = DotsChecker.State.INPUT
        private set

    override fun onCheck() = onCheckHandler().also {
        if (state == DotsChecker.State.HINT) {
            state = DotsChecker.State.INPUT
            onPassHint()
        } else {
            if (isCorrect) onCorrect()
            else onIncorrect()
        }
    }

    override fun onSoftCheck() = onSoftCheckHandler().also {
        if (state != DotsChecker.State.INPUT) return@also
        if (isCorrect) onSoftCorrect()
    }

    private fun onCorrect() = onCorrectHandler().also {
        _eventCorrect.value = true
    }

    private fun onSoftCorrect() = onSoftCorrectHandler().also {
        _eventSoftCorrect.value = true
    }

    private fun onIncorrect() = onIncorrectHandler().also {
        _eventIncorrect.value = true
    }

    private fun onPassHint() = onPassHintHandler().also {
        _eventPassHint.value = true
    }

    override fun onHint() = onHintHandler().also {
        if (state == DotsChecker.State.HINT) {
            state = DotsChecker.State.INPUT
            onPassHint()
        } else {
            state = DotsChecker.State.HINT
            _eventHint.value = expectedDots
        }
    }

    override fun onCorrectComplete() {
        _eventCorrect.value = false
    }


    override fun onSoftCorrectComplete() {
        _eventSoftCorrect.value = false
    }

    override fun onHintComplete() {
        _eventHint.value = null
    }

    override fun onPassHintComplete() {
        _eventPassHint.value = false
    }

    override fun onIncorrectComplete() {
        _eventIncorrect.value = false
    }
}

@Suppress("LongParameterList")
fun DotsChecker.observeCheckedOnFly(
    lifecycleOwner: LifecycleOwner,
    getDotsState: () -> BrailleDotsState,
    buzzer: Vibrator? = null,
    preferenceRepository: PreferenceRepository = get(),
    block: () -> Unit = {},
    softBlock: () -> Unit = {}
) {
    if (preferenceRepository.inputOnFlyCheck) {
        observeEventCorrect(lifecycleOwner, getDotsState, buzzer = null, block = block)
        observeEventSoftCorrect(lifecycleOwner, buzzer = buzzer, block = softBlock)
    } else {
        observeEventCorrect(lifecycleOwner, getDotsState, buzzer) {
            softBlock()
            block()
        }
    }
}

fun DotsChecker.observeEventCorrect(
    lifecycleOwner: LifecycleOwner,
    getDotsState: () -> BrailleDotsState,
    buzzer: Vibrator? = null,
    preferenceRepository: PreferenceRepository = get(),
    block: () -> Unit = {}
): Unit = eventCorrect.observe(
    lifecycleOwner,
    Observer {
        if (!it) return@Observer
        Timber.i("Handle correct")
        buzzer.checkedBuzz(preferenceRepository.correctBuzzPattern)
        getDotsState().uncheck()
        block()
        onCorrectComplete()
    }
)

fun DotsChecker.observeEventSoftCorrect(
    lifecycleOwner: LifecycleOwner,
    buzzer: Vibrator? = null,
    preferenceRepository: PreferenceRepository = get(),
    block: () -> Unit = {}
): Unit = eventSoftCorrect.observe(
    lifecycleOwner,
    Observer {
        if (!it) return@Observer
        Timber.i("Handle soft correct")
        buzzer.checkedBuzz(preferenceRepository.correctBuzzPattern)
        block()
        onSoftCorrectComplete()
    }
)

fun DotsChecker.observeEventIncorrect(
    lifecycleOwner: LifecycleOwner,
    getDotsState: () -> BrailleDotsState,
    buzzer: Vibrator? = null,
    preferenceRepository: PreferenceRepository = get(),
    block: () -> Unit = {}
): Unit = eventIncorrect.observe(
    lifecycleOwner,
    Observer {
        if (!it) return@Observer
        Timber.i("Handle incorrect: entered = ${getDotsState().spelling}")
        buzzer.checkedBuzz(preferenceRepository.incorrectBuzzPattern)
        getDotsState().uncheck()
        block()
        onIncorrectComplete()
    }
)

fun DotsChecker.observeEventHint(
    lifecycleOwner: LifecycleOwner,
    getDotsState: () -> BrailleDotsState,
    block: (BrailleDots) -> Unit = {}
): Unit = eventHint.observe(
    lifecycleOwner,
    Observer { expectedDots ->
        if (expectedDots == null) return@Observer
        Timber.i("Handle hint")
        getDotsState().display(expectedDots)
        BrailleTrainer.trySend(expectedDots)
        block(expectedDots)
        onHintComplete()
    }
)

fun DotsChecker.observeEventPassHint(
    lifecycleOwner: LifecycleOwner,
    getDotsState: () -> BrailleDotsState,
    block: () -> Unit = {}
): Unit = eventPassHint.observe(
    lifecycleOwner,
    Observer {
        if (!it) return@Observer
        getDotsState().uncheck()
        getDotsState().clickable(true)
        block()
        onPassHintComplete()
    }
)

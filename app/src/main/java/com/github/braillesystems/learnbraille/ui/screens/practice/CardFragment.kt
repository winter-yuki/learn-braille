package com.github.braillesystems.learnbraille.ui.screens.practice

import android.os.Bundle
import android.os.Vibrator
import android.view.*
import androidx.core.content.getSystemService
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.github.braillesystems.learnbraille.R
import com.github.braillesystems.learnbraille.data.entities.MarkerSymbol
import com.github.braillesystems.learnbraille.data.entities.Symbol
import com.github.braillesystems.learnbraille.data.repository.PreferenceRepository
import com.github.braillesystems.learnbraille.databinding.FragmentCardBinding
import com.github.braillesystems.learnbraille.res.captionRules
import com.github.braillesystems.learnbraille.res.deckTagToName
import com.github.braillesystems.learnbraille.res.inputMarkerPrintRules
import com.github.braillesystems.learnbraille.ui.brailletrainer.BrailleTrainer
import com.github.braillesystems.learnbraille.ui.brailletrainer.BrailleTrainerSignalHandler
import com.github.braillesystems.learnbraille.ui.inputPrint
import com.github.braillesystems.learnbraille.ui.screens.*
import com.github.braillesystems.learnbraille.ui.showCorrectToast
import com.github.braillesystems.learnbraille.ui.showHintToast
import com.github.braillesystems.learnbraille.ui.showIncorrectToast
import com.github.braillesystems.learnbraille.ui.views.BrailleDotsState
import com.github.braillesystems.learnbraille.ui.views.brailleDots
import com.github.braillesystems.learnbraille.ui.views.dotsState
import com.github.braillesystems.learnbraille.ui.views.subscribe
import com.github.braillesystems.learnbraille.utils.*
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import timber.log.Timber

class CardFragment : AbstractFragmentWithHelp(R.string.practice_help) {

    private val preferenceRepository: PreferenceRepository by inject()

    // This value can change during ViewModel lifetime (ViewModelProvider does not call
    // ViewModelFactory each time onCreateView runs). And once created ViewModel
    // should be able to use up to date dotsState.
    private lateinit var dotsState: BrailleDotsState

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = DataBindingUtil.inflate<FragmentCardBinding>(
        inflater,
        R.layout.fragment_card,
        container,
        false
    ).also { binding ->

        Timber.i("onCreateView")

        title = title()
        setHasOptionsMenu(true)

        if (preferenceRepository.extendedAccessibilityEnabled) {
            applyExtendedAccessibility(
                leftButton = binding.hintButton,
                rightButton = binding.nextButton,
                rightMiddleButton = binding.flipButton,
                textView = binding.markerDescription
            )
        }

        dotsState = binding.brailleDots.dotsState

        val viewModelFactory: CardViewModelFactory by inject {
            parametersOf({ dotsState.brailleDots })
        }
        val viewModel = ViewModelProvider(this, viewModelFactory)
            .get(CardViewModel::class.java)

        dotsState.subscribe(View.OnClickListener {
            viewModel.onSoftCheck()
        })


        val buzzer: Vibrator? = activity?.getSystemService()

        BrailleTrainer.setSignalHandler(object : BrailleTrainerSignalHandler {
            override fun onJoystickRight() = viewModel.onCheck()
            override fun onJoystickLeft() = viewModel.onHint()
        })


        binding.cardViewModel = viewModel
        binding.lifecycleOwner = this@CardFragment


        viewModel.symbol.observe(viewLifecycleOwner, Observer {
            if (it == null) return@Observer
            when (it) {
                is Symbol -> {
                    binding.letter.visibility = View.VISIBLE
                    binding.markerDescription.visibility = View.GONE
                    binding.letter.letter = it.char
                    binding.letterCaption.text = captionRules.getValue(it)
                }
                is MarkerSymbol -> {
                    binding.letter.visibility = View.GONE
                    binding.markerDescription.visibility = View.VISIBLE
                    binding.markerDescription.text = inputMarkerPrintRules[it.type]
                    binding.letterCaption.text = ""
                }
            }
        })

        viewModel.observeCheckedOnFly(
            viewLifecycleOwner, dotsState, buzzer,
            block = { title = title(viewModel) },
            softBlock = ::showCorrectToast
        )

        viewModel.observeEventIncorrect(
            viewLifecycleOwner, dotsState, buzzer
        ) {
            viewModel.symbol.value
                ?.let { showIncorrectToast(inputPrint(it)) }
                ?: checkedToast(getString(R.string.input_loading))
            title = title(viewModel)
        }

        viewModel.observeEventHint(
            viewLifecycleOwner, dotsState
        ) { expectedDots ->
            showHintToast(expectedDots)
        }

        viewModel.observeEventPassHint(
            viewLifecycleOwner, dotsState
        ) {
            viewModel.symbol.value?.let {
                checkedAnnounce(inputPrint(it))
            }
        }

        viewModel.symbol.observe(
            viewLifecycleOwner,
            Observer {
                if (it == null) return@Observer
                checkedAnnounce(inputPrint(it))
            }
        )

        viewModel.deckTag.observe(
            viewLifecycleOwner,
            Observer { tag ->
                if (tag == null) return@Observer
                val template = if (preferenceRepository.practiceUseOnlyKnownMaterials) {
                    getString(R.string.practice_deck_name_enabled_template)
                } else {
                    getString(R.string.practice_deck_name_disabled_template)
                }
                toast(template.format(deckTagToName.getValue(tag)))
            }
        )

    }.root

    private fun title(viewModel: CardViewModel? = null): String =
        getString(R.string.practice_actionbar_title_template).run {
            if (viewModel == null) format(0, 0)
            else format(viewModel.nCorrect, viewModel.nTries)
        }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.card_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = false.also {
        when (item.itemId) {
            R.id.help -> navigateToHelp()
            R.id.decks_list -> navigate(R.id.action_cardFragment_to_decksList)
        }
    }
}

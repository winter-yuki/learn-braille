package com.github.braillesystems.learnbraille.ui.screens.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.github.braillesystems.learnbraille.R
import com.github.braillesystems.learnbraille.data.entities.MarkerSymbol
import com.github.braillesystems.learnbraille.data.entities.Material
import com.github.braillesystems.learnbraille.data.entities.Symbol
import com.github.braillesystems.learnbraille.data.entities.spelling
import com.github.braillesystems.learnbraille.data.repository.BrowserRepository
import com.github.braillesystems.learnbraille.databinding.BrowserListItemBinding
import com.github.braillesystems.learnbraille.databinding.FragmentBrowserBinding
import com.github.braillesystems.learnbraille.res.showMarkerPrintRules
import com.github.braillesystems.learnbraille.res.showSymbolPrintRules
import com.github.braillesystems.learnbraille.ui.screens.AbstractFragmentWithHelp
import com.github.braillesystems.learnbraille.utils.getValue
import com.github.braillesystems.learnbraille.utils.navigate
import com.github.braillesystems.learnbraille.utils.stringify
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class BrowserFragment : AbstractFragmentWithHelp(R.string.browser_help) {

    private val browserRepository: BrowserRepository by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = DataBindingUtil.inflate<FragmentBrowserBinding>(
        inflater,
        R.layout.fragment_browser,
        container,
        false
    ).ini().also { binding ->

        lifecycleScope.launch {
            val deckId = browserRepository.currentDeckId
            val materials = browserRepository.allMaterialsFromDeck(deckId)
            val listener = object : BrowserItemListener {
                override fun onClick(item: Material) {
                    val arg = stringify(Material.serializer(), item)
                    when (item.data) {
                        is Symbol -> navigate(
                            BrowserFragmentDirections
                                .actionBrowserFragmentToSymbolViewFragment(arg)
                        )
                        is MarkerSymbol -> navigate(
                            BrowserFragmentDirections
                                .actionBrowserFragmentToMarkerViewFragment(arg)
                        )
                    }
                }
            }
            binding.materialsList.adapter = BrowserListAdapter(materials) { item ->
                this.item = item
                materialText.text = when (item.data) {
                    is Symbol -> getString(R.string.browser_represent_template).format(
                        showSymbolPrintRules.getValue(item.data.char),
                        item.data.brailleDots.spelling
                    )
                    is MarkerSymbol -> getString(R.string.browser_represent_template).format(
                        showMarkerPrintRules.getValue(item.data.type),
                        item.data.brailleDots.spelling
                    )
                }
                clickListener = listener
            }
        }

    }.root
}

private class BrowserListAdapter(
    private val materials: List<Material>,
    private val bind: BrowserListItemBinding.(Material) -> Unit
) : RecyclerView.Adapter<BrowserItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        BrowserItemViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.browser_list_item,
                parent, false
            )
        )

    override fun getItemCount(): Int = materials.size

    override fun onBindViewHolder(holder: BrowserItemViewHolder, position: Int) {
        val item = materials[position]
        holder.binding.bind(item)
    }
}

private class BrowserItemViewHolder(
    val binding: BrowserListItemBinding
) : RecyclerView.ViewHolder(binding.root)

interface BrowserItemListener {
    fun onClick(item: Material)
}

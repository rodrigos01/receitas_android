package com.rodrigossantos.recipe

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.Group
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rodrigossantos.recipe.databinding.AddButtonRecipeItemBinding
import com.rodrigossantos.recipe.databinding.FragmentRecipeBinding
import com.rodrigossantos.recipe.databinding.RecipeHeaderItemBinding
import com.rodrigossantos.recipe.databinding.RecipeIngredientItemBinding
import com.rodrigossantos.recipe.databinding.RecipeStepItemBinding
import kotlinx.coroutines.launch


class RecipeFragment : Fragment() {

    private val args by navArgs<RecipeFragmentArgs>()

    private val binding: FragmentRecipeBinding by lazy {
        FragmentRecipeBinding.bind(requireView())
    }

    private val editRecipeButton: MenuItem by lazy {
        binding.topAppBar.menu.findItem(R.id.edit_recipe_button)
    }

    private val multiplierButton: MenuItem by lazy {
        binding.topAppBar.menu.findItem(R.id.multiplier_button)
    }

    private val saveRecipeButton: MenuItem by lazy {
        binding.topAppBar.menu.findItem(R.id.save_recipe_button)
    }

    private val viewModel: RecipeViewModel by viewModels(
        factoryProducer = { RecipeViewModel.Factory(binding.root.context, args.recipeIndex) }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_recipe, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.edit_recipe_button -> viewModel.editButtonClicked()
                R.id.multiplier_button -> {
                    AlertDialog.Builder(binding.topAppBar.context)
                        .setTitle("Change Ingredients Proportion")
                        .setSingleChoiceItems(
                            viewModel.uiState.value.availableMultipliers.map { it.stringValue }
                                .toTypedArray(),
                            viewModel.uiState.value.currentMultiplierIndex,
                        ) { dialog, selectedIndex ->
                            dialog.dismiss()
                            viewModel.multiplierUpdated(selectedIndex)
                        }
                        .show()
                }

                R.id.save_recipe_button -> viewModel.saveRecipe(binding.recipeNameEdittext.text.toString())
                R.id.delete_recipe_item -> {
                    AlertDialog.Builder(binding.topAppBar.context)
                        .setTitle("Are you sure")
                        .setPositiveButton("Delete") { dialog, _ ->
                            viewModel.deleteRecipe()
                            dialog.dismiss()
                            findNavController().popBackStack()
                        }
                        .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            }
            true
        }
        val adapter = RecipeItemsAdapter(
            itemClick = viewModel::itemClicked,
            addIngredient = viewModel::addIngredientClicked,
            saveIngredient = viewModel::saveIngredient,
            addStep = viewModel::addStepClicked,
            saveStep = viewModel::saveStep,
            cancelEdit = viewModel::cancelEdit,
            deleteItem = viewModel::deleteItem
        )
        binding.list.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.uiState.collect {
                    binding.recipeNameEdittext.apply {
                        setText(it.recipeName)
                        visibility = if (it.editing) View.VISIBLE else View.GONE
                    }
                    binding.topAppBar.navigationIcon = if (!it.editing) {
                        AppCompatResources.getDrawable(
                            binding.topAppBar.context,
                            R.drawable.baseline_arrow_back_24,
                        )
                    } else {
                        null
                    }
                    binding.topAppBar.setNavigationOnClickListener {
                        findNavController().popBackStack()
                    }
                    binding.topAppBar.title = it.recipeName
                    editRecipeButton.isVisible = !it.editing
                    multiplierButton.isVisible = !it.editing
                    multiplierButton.title =
                        it.availableMultipliers[it.currentMultiplierIndex].stringValue
                    saveRecipeButton.isVisible = it.editing
                    adapter.items = it.items
                }
            }
        }
    }
}

class RecipeItemsAdapter(
    private val itemClick: (index: Int) -> Unit,
    private val addIngredient: () -> Unit,
    private val saveIngredient: (index: Int, quantity: String, unitIndex: Int, name: String) -> Unit,
    private val addStep: () -> Unit,
    private val saveStep: (index: Int, content: String) -> Unit,
    private val cancelEdit: (index: Int) -> Unit,
    private val deleteItem: (index: Int) -> Unit,
) :
    RecyclerView.Adapter<RecipeItemsAdapter.ViewHolder>() {

    enum class ViewType(val value: Int) {
        HEADER(1),
        INGREDIENT(2),
        ADD_INGREDIENT(3),
        STEP(4),
        ADD_STEP(5),
    }

    var items: List<RecipeViewModel.RecipeItem> = emptyList()
        get() = field
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewTypeValue: Int): ViewHolder {
        val viewType =
            ViewType.entries.find { it.value == viewTypeValue } ?: error("invalid view type")
        return when (viewType) {
            ViewType.HEADER -> ViewHolder.HeaderViewHolder(
                RecipeHeaderItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )

            ViewType.INGREDIENT -> ViewHolder.IngredientViewHolder(
                RecipeIngredientItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )

            ViewType.STEP -> ViewHolder.StepViewHolder(
                RecipeStepItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )

            ViewType.ADD_STEP, ViewType.ADD_INGREDIENT -> ViewHolder.AddButtonViewHolder(
                AddButtonRecipeItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items.getOrNull(position) ?: return
        when (item) {
            is RecipeViewModel.RecipeItem.Header -> {
                if (holder !is ViewHolder.HeaderViewHolder) return
                holder.binding.titleText.text = item.title
            }

            is RecipeViewModel.RecipeItem.Modifiable -> {
                (holder as ViewHolder.ModifiableViewHolder).run {
                    clickableView.setOnClickListener {
                        itemClick(position)
                    }
                    cancelButton.setOnClickListener {
                        cancelEdit(position)
                        clickableView.requestFocus()
                    }
                    deleteButton.setOnClickListener {
                        deleteClicked(it.context, position)
                        clickableView.requestFocus()
                    }
                    if (item.editing) {
                        viewGroup.visibility = View.GONE
                        editGroup.visibility = View.VISIBLE
                        firstField.requestFocus()
                        firstField.showKeyboard()
                    } else {
                        viewGroup.visibility = View.VISIBLE
                        editGroup.visibility = View.GONE
                    }
                }
                when (item) {
                    is RecipeViewModel.RecipeItem.Ingredient -> {
                        (holder as ViewHolder.IngredientViewHolder).binding.run {
                            quantityText.text = item.quantity
                            quantityEdittext.setText(item.quantity)
                            var selectedUnitIndex: Int = item.measurementUnitIndex
                            val units =
                                quantityText.resources.getStringArray(R.array.measurement_units)
                            unitText.text = units[selectedUnitIndex]
                            val popupMenu = PopupMenu(unitSelector.context, unitSelector).apply {
                                menuInflater.inflate(R.menu.measurement_unit_selector, menu)
                                setOnMenuItemClickListener {
                                    selectedUnitIndex = it.order
                                    unitSelector.text = it.title
                                    true
                                }
                            }
                            unitSelector.text = popupMenu.menu.getItem(selectedUnitIndex).title
                            unitSelector.setOnClickListener { popupMenu.show() }
                            nameText.text = item.name
                            nameEdittext.setText(item.name)
                            saveButton.setOnClickListener {
                                saveIngredient(
                                    position,
                                    quantityEdittext.text.toString(),
                                    selectedUnitIndex,
                                    nameEdittext.text.toString(),
                                )
                                quantityText.requestFocus()
                            }
                        }
                    }

                    is RecipeViewModel.RecipeItem.Step -> {
                        (holder as ViewHolder.StepViewHolder).binding.run {
                            indexText.text = item.index
                            contentText.text = item.text
                            contentEdittext.setText(item.text)
                            saveButton.setOnClickListener {
                                saveStep(
                                    position,
                                    contentEdittext.text.toString()
                                )
                                root.requestFocus()
                            }
                        }
                    }
                }
            }

            is RecipeViewModel.RecipeItem.AddIngredient -> {
                (holder as ViewHolder.AddButtonViewHolder).binding.run {
                    addButtonText.text = "Add Ingredient"
                    addButtonText.setOnClickListener { addIngredient() }
                }
            }

            is RecipeViewModel.RecipeItem.AddStep -> {
                (holder as ViewHolder.AddButtonViewHolder).binding.run {
                    addButtonText.text = "Add Step"
                    addButtonText.setOnClickListener { addStep() }
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return getItemViewType(items[position]).value
    }

    private fun getItemViewType(item: RecipeViewModel.RecipeItem): ViewType {
        return when (item) {
            is RecipeViewModel.RecipeItem.Header -> ViewType.HEADER
            is RecipeViewModel.RecipeItem.Ingredient -> ViewType.INGREDIENT
            is RecipeViewModel.RecipeItem.AddIngredient -> ViewType.ADD_INGREDIENT
            is RecipeViewModel.RecipeItem.Step -> ViewType.STEP
            is RecipeViewModel.RecipeItem.AddStep -> ViewType.ADD_STEP
        }
    }

    override fun getItemCount(): Int = items.count()

    private fun deleteClicked(context: Context, position: Int) {
        AlertDialog.Builder(context)
            .setTitle("Are you sure")
            .setPositiveButton("Delete") { dialog, _ ->
                deleteItem(position)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    sealed class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        class HeaderViewHolder(val binding: RecipeHeaderItemBinding) : ViewHolder(binding.root)
        sealed interface ModifiableViewHolder {
            val clickableView: View
            val firstField: EditText
            val saveButton: Button
            val cancelButton: Button
            val deleteButton: Button
            val viewGroup: Group
            val editGroup: Group
        }

        class IngredientViewHolder(val binding: RecipeIngredientItemBinding) :
            ViewHolder(binding.root), ModifiableViewHolder {
            override val clickableView: View
                get() = binding.root
            override val firstField: EditText
                get() = binding.quantityEdittext
            override val saveButton: Button
                get() = binding.saveButton
            override val cancelButton: Button
                get() = binding.cancelButton
            override val deleteButton: Button
                get() = binding.deleteButton
            override val viewGroup: Group
                get() = binding.viewGroup
            override val editGroup: Group
                get() = binding.editGroup
        }

        class StepViewHolder(val binding: RecipeStepItemBinding) : ViewHolder(binding.root),
            ModifiableViewHolder {
            override val clickableView: View
                get() = binding.root
            override val firstField: EditText
                get() = binding.contentEdittext
            override val saveButton: Button
                get() = binding.saveButton
            override val cancelButton: Button
                get() = binding.cancelButton
            override val deleteButton: Button
                get() = binding.deleteButton
            override val viewGroup: Group
                get() = binding.viewGroup
            override val editGroup: Group
                get() = binding.editGroup
        }

        class AddButtonViewHolder(val binding: AddButtonRecipeItemBinding) :
            ViewHolder(binding.root)
    }
}

private fun View.showKeyboard() {
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(
        this,
        InputMethodManager.SHOW_IMPLICIT
    )
}

private val RecipeViewModel.Multiplier.stringValue
    get() = when (this) {
        RecipeViewModel.Multiplier.HALF -> "0.5x"
        RecipeViewModel.Multiplier.SINGLE -> "1x"
        RecipeViewModel.Multiplier.ONE_AND_A_HALF -> "1.5x"
        RecipeViewModel.Multiplier.DOUBLE -> "2x"
    }
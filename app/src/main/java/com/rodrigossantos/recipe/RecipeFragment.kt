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
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.rodrigossantos.recipe.RecipeViewModel.RecipeItem
import com.rodrigossantos.recipe.databinding.AddButtonRecipeItemBinding
import com.rodrigossantos.recipe.databinding.FragmentRecipeBinding
import com.rodrigossantos.recipe.databinding.RecipeHeaderItemBinding
import com.rodrigossantos.recipe.databinding.RecipeIngredientItemBinding
import com.rodrigossantos.recipe.databinding.RecipeStepItemBinding
import kotlinx.coroutines.launch
import kotlin.contracts.ExperimentalContracts


@OptIn(ExperimentalContracts::class)
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

    private val ingredientsAdapter by lazy {
        buildAdapter {
            items = it.ingredients
        }
    }
    private val stepsAdapter by lazy {
        buildAdapter {
            items = it.steps
        }
    }

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
                R.id.multiplier_button -> multiplierButtonClicked()
                R.id.save_recipe_button -> viewModel.saveRecipe(binding.recipeNameEdittext.text.toString())
                R.id.delete_recipe_item -> deleteButtonClicked()
            }
            true
        }
        binding.tabBar.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> binding.slidingPaneLayout.close()
                    1 -> binding.slidingPaneLayout.open()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit

            override fun onTabReselected(tab: TabLayout.Tab?) = Unit

        })
        binding.ingredientsList.adapter = ingredientsAdapter
        binding.stepsList.adapter = stepsAdapter
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.uiState.collect(::stateUpdated)
            }
        }
    }

    private fun stateUpdated(state: RecipeViewModel.UiState) {
        binding.recipeNameEdittext.apply {
            setText(state.recipeName)
            visibility = if (state.editing) View.VISIBLE else View.GONE
        }
        binding.topAppBar.navigationIcon = if (!state.editing) {
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
        binding.topAppBar.title = state.recipeName
        editRecipeButton.isVisible = !state.editing
        multiplierButton.isVisible = !state.editing
        multiplierButton.title =
            state.availableMultipliers[state.currentMultiplierIndex].stringValue
        saveRecipeButton.isVisible = state.editing
    }

    private fun deleteButtonClicked() {
        showDialog(binding.topAppBar.context, "Are you sure") {
            setPositiveButton("Delete") { dialog, _ ->
                viewModel.deleteRecipe()
                dialog.dismiss()
                findNavController().popBackStack()
            }
            setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        }
    }

    private fun multiplierButtonClicked() {
        showDialog(
            binding.topAppBar.context,
            "Change Ingredients Proportion"
        ) {
            setSingleChoiceItems(
                viewModel.uiState.value.availableMultipliers.map { it.stringValue }
                    .toTypedArray(),
                viewModel.uiState.value.currentMultiplierIndex,
            ) { dialog, selectedIndex ->
                dialog.dismiss()
                viewModel.multiplierUpdated(selectedIndex)
            }
        }
    }

    private fun showDialog(
        context: Context,
        title: String,
        build: AlertDialog.Builder.() -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .apply(build)
            .show()
    }

    private sealed interface ViewType {
        object HEADER : ViewType
        object INGREDIENT : ViewType
        object ADD_INGREDIENT : ViewType
        object STEP : ViewType
        object ADD_STEP : ViewType
    }

    private fun buildAdapter(onStateUpdate: suspend MutableItemListSource<RecipeItem>.(RecipeViewModel.UiState) -> Unit) =
        createAdapter(
            getViewType = { item: RecipeItem ->
                when (item) {
                    is RecipeItem.Header -> ViewType.HEADER
                    is RecipeItem.Ingredient -> ViewType.INGREDIENT
                    is RecipeItem.AddIngredient -> ViewType.ADD_INGREDIENT
                    is RecipeItem.Step -> ViewType.STEP
                    is RecipeItem.AddStep -> ViewType.ADD_STEP
                }
            },
            create = { type: ViewType ->
                when (type) {
                    is ViewType.HEADER -> RecipeHeaderItemBinding::inflate
                    is ViewType.INGREDIENT -> RecipeIngredientItemBinding::inflate
                    is ViewType.STEP -> RecipeStepItemBinding::inflate
                    is ViewType.ADD_STEP,
                    is ViewType.ADD_INGREDIENT -> AddButtonRecipeItemBinding::inflate
                }
            },
            itemsProducer = viewLifecycleOwner.produceOnLifecycle {
                viewModel.uiState.collect { onStateUpdate(it) }
            }
        ) { item, position ->
            when (item) {
                is RecipeItem.Header -> {
                    (this as RecipeHeaderItemBinding).titleText.text = item.title
                }

                is RecipeItem.Ingredient -> (this as RecipeIngredientItemBinding).bindIngredient(
                    item,
                    position
                )

                is RecipeItem.Step -> (this as RecipeStepItemBinding).bindStep(
                    position,
                    item
                )

                is RecipeItem.AddIngredient -> {
                    this as AddButtonRecipeItemBinding
                    addButtonText.text = "Add Ingredient"
                    addButtonText.setOnClickListener { viewModel.addIngredientClicked() }
                }

                is RecipeItem.AddStep -> {
                    this as AddButtonRecipeItemBinding
                    addButtonText.text = "Add Step"
                    addButtonText.setOnClickListener { viewModel.addStepClicked() }
                }
            }
        }

    private fun RecipeStepItemBinding.bindStep(
        position: Int,
        item: RecipeItem.Step
    ) {
        bindModifiableItem(
            root,
            contentEdittext,
            saveButton,
            saveButtonClicked = {
                viewModel.saveStep(
                    position,
                    contentEdittext.text.toString()
                )
            },
            cancelButton,
            deleteButton,
            viewGroup,
            editGroup,
            item,
            position,
        )
        indexText.text = item.index
        contentText.text = item.text
        contentEdittext.setText(item.text)
    }

    private fun RecipeIngredientItemBinding.bindIngredient(
        item: RecipeItem.Ingredient,
        position: Int
    ) {
        var selectedUnitIndex: Int = item.measurementUnitIndex
        bindModifiableItem(
            root,
            quantityEdittext,
            saveButton,
            saveButtonClicked = {
                viewModel.saveIngredient(
                    position,
                    quantityEdittext.text.toString(),
                    selectedUnitIndex,
                    nameEdittext.text.toString(),
                )
            },
            cancelButton,
            deleteButton,
            viewGroup,
            editGroup,
            item,
            position,
        )
        quantityText.text = item.quantity
        quantityEdittext.setText(item.quantity)
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
    }

    private inline fun <reified T : RecipeItem.Modifiable> bindModifiableItem(
        clickableView: View,
        firstField: EditText,
        saveButton: Button,
        crossinline saveButtonClicked: () -> Unit,
        cancelButton: Button,
        deleteButton: Button,
        viewGroup: Group,
        editGroup: Group,
        item: T,
        position: Int,
    ) {
        clickableView.setOnClickListener {
            viewModel.itemClicked<T>(position)
        }
        cancelButton.setOnClickListener {
            viewModel.cancelEdit<T>(position)
            clickableView.hideKeyboard()
        }
        deleteButton.setOnClickListener {
            viewModel.deleteItem<T>(position)
            clickableView.hideKeyboard()
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
        saveButton.setOnClickListener {
            saveButtonClicked()
            clickableView.hideKeyboard()
        }
    }
}

private fun View.showKeyboard() {
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(
        this,
        InputMethodManager.SHOW_IMPLICIT
    )
}

private fun View.hideKeyboard() {
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(
        windowToken,
        InputMethodManager.HIDE_IMPLICIT_ONLY
    )
}

private val RecipeViewModel.Multiplier.stringValue
    get() = when (this) {
        RecipeViewModel.Multiplier.HALF -> "0.5x"
        RecipeViewModel.Multiplier.SINGLE -> "1x"
        RecipeViewModel.Multiplier.ONE_AND_A_HALF -> "1.5x"
        RecipeViewModel.Multiplier.DOUBLE -> "2x"
    }
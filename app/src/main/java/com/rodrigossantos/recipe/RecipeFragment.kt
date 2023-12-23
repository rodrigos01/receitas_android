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

    private val adapter by lazy { buildAdapter() }

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
        binding.list.adapter = adapter
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
                }
            }
        }
    }

    private enum class ViewType {
        HEADER,
        INGREDIENT,
        ADD_INGREDIENT,
        STEP,
        ADD_STEP,
    }

    private fun buildAdapter() = createAdapter(
        itemsProducer = viewLifecycleOwner.produceOnLifecycle {
            viewModel.uiState.collect { items = it.items }
        },
        getViewType = { item: RecipeViewModel.RecipeItem ->
            when (item) {
                is RecipeViewModel.RecipeItem.Header -> ViewType.HEADER
                is RecipeViewModel.RecipeItem.Ingredient -> ViewType.INGREDIENT
                is RecipeViewModel.RecipeItem.AddIngredient -> ViewType.ADD_INGREDIENT
                is RecipeViewModel.RecipeItem.Step -> ViewType.STEP
                is RecipeViewModel.RecipeItem.AddStep -> ViewType.ADD_STEP
            }
        },
        create = { parent, type ->
            when (type) {
                ViewType.HEADER -> RecipeHeaderItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )

                ViewType.INGREDIENT -> RecipeIngredientItemBinding.inflate(
                    LayoutInflater.from(
                        parent.context
                    ),
                    parent, false,
                )

                ViewType.STEP -> RecipeStepItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )

                ViewType.ADD_STEP,
                ViewType.ADD_INGREDIENT -> AddButtonRecipeItemBinding.inflate(
                    LayoutInflater.from(
                        parent.context
                    ), parent, false
                )
            }
        }
    ) { binding, item, position ->
        when (item) {
            is RecipeViewModel.RecipeItem.Header -> {
                (binding as RecipeHeaderItemBinding).titleText.text = item.title
            }

            is RecipeViewModel.RecipeItem.Modifiable -> {
                when (item) {
                    is RecipeViewModel.RecipeItem.Ingredient -> {
                        (binding as RecipeIngredientItemBinding).run {
                            var selectedUnitIndex: Int = item.measurementUnitIndex
                            bindModifiableItem(
                                binding.root,
                                binding.quantityEdittext,
                                binding.saveButton,
                                saveButtonClicked = {
                                    viewModel.saveIngredient(
                                        position,
                                        quantityEdittext.text.toString(),
                                        selectedUnitIndex,
                                        nameEdittext.text.toString(),
                                    )
                                },
                                binding.cancelButton,
                                binding.deleteButton,
                                binding.viewGroup,
                                binding.editGroup,
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
                    }

                    is RecipeViewModel.RecipeItem.Step -> {
                        (binding as RecipeStepItemBinding).run {
                            bindModifiableItem(
                                binding.root,
                                binding.contentEdittext,
                                binding.saveButton,
                                saveButtonClicked = {
                                    viewModel.saveStep(
                                        position,
                                        contentEdittext.text.toString()
                                    )
                                },
                                binding.cancelButton,
                                binding.deleteButton,
                                binding.viewGroup,
                                binding.editGroup,
                                item,
                                position,
                            )
                            indexText.text = item.index
                            contentText.text = item.text
                            contentEdittext.setText(item.text)
                        }
                    }
                }
            }

            is RecipeViewModel.RecipeItem.AddIngredient -> {
                (binding as AddButtonRecipeItemBinding).run {
                    addButtonText.text = "Add Ingredient"
                    addButtonText.setOnClickListener { viewModel.addIngredientClicked() }
                }
            }

            is RecipeViewModel.RecipeItem.AddStep -> {
                (binding as AddButtonRecipeItemBinding).run {
                    addButtonText.text = "Add Step"
                    addButtonText.setOnClickListener { viewModel.addStepClicked() }
                }
            }
        }
    }

    private fun bindModifiableItem(
        clickableView: View,
        firstField: EditText,
        saveButton: Button,
        saveButtonClicked: () -> Unit,
        cancelButton: Button,
        deleteButton: Button,
        viewGroup: Group,
        editGroup: Group,
        item: RecipeViewModel.RecipeItem.Modifiable,
        position: Int,
    ) {
        clickableView.setOnClickListener {
            viewModel.itemClicked(position)
        }
        cancelButton.setOnClickListener {
            viewModel.cancelEdit(position)
            clickableView.requestFocus()
        }
        deleteButton.setOnClickListener {
            viewModel.deleteItem(position)
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
        saveButton.setOnClickListener {
            saveButtonClicked()
            clickableView.requestFocus()
        }
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
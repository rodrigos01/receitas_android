package com.rodrigossantos.recipe

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class RecipeViewModel(private val repository: RecipesRepository, private val recipeIndex: Int) :
    ViewModel() {

    data class UiState(
        val recipeName: String,
        val availableMultipliers: List<Multiplier>,
        val currentMultiplierIndex: Int,
        val items: List<RecipeItem>,
        val editing: Boolean,
    )

    enum class Multiplier(val value: Float) {
        HALF(0.5f),
        SINGLE(1F),
        ONE_AND_A_HALF(1.5F),
        DOUBLE(2F),
    }

    sealed interface RecipeItem {
        data class Header(val title: String) : RecipeItem

        sealed interface Modifiable : RecipeItem {
            val dataIndex: Int
            val editing: Boolean

            fun duplicate(editing: Boolean): Modifiable
        }

        data class Ingredient(
            override val dataIndex: Int,
            val quantity: String,
            val measurementUnitIndex: Int,
            val name: String,
            override val editing: Boolean
        ) :
            RecipeItem, Modifiable {
            override fun duplicate(editing: Boolean) = copy(editing = editing)
        }

        data class Step(
            override val dataIndex: Int,
            val index: String,
            val text: String,
            override val editing: Boolean
        ) : RecipeItem, Modifiable {
            override fun duplicate(editing: Boolean) = copy(editing = editing)
        }

        data object AddIngredient : RecipeItem
        data object AddStep : RecipeItem
    }

    private val _uiState = MutableStateFlow(
        UiState(
            "",
            Multiplier.entries,
            Multiplier.entries.indexOf(Multiplier.SINGLE),
            emptyList(),
            false,
        )
    )
    private val recipeFlow = repository.getRecipe(recipeIndex).onEach {
        _uiState.value = uiState.value.copy(
            items = genItems(it)
        )
    }
    val uiState: StateFlow<UiState> = combine(_uiState, recipeFlow) { state, recipe ->
        val multiplier = Multiplier.entries[state.currentMultiplierIndex].value
        val updatedItems = state.items.map {
            if (it is RecipeItem.Ingredient) {
                val quantity = recipe.ingredientsList[it.dataIndex].quantity * multiplier
                it.copy(quantity = DecimalFormat("#.##").format(quantity))
            } else {
                it
            }
        }
        UiState(
            recipeName = recipe.name,
            availableMultipliers = Multiplier.entries,
            currentMultiplierIndex = state.currentMultiplierIndex,
            items = updatedItems,
            editing = state.editing,
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, _uiState.value)

    fun editButtonClicked() {
        _uiState.value = uiState.value.copy(editing = true)
    }

    fun saveRecipe(recipeName: String) {
        viewModelScope.launch {
            repository.updateRecipe(recipeIndex, recipeName)
            _uiState.value = uiState.value.copy(editing = false)
        }
    }

    fun deleteRecipe() {
        viewModelScope.launch { repository.deleteRecipe(recipeIndex) }
    }

    fun multiplierUpdated(newMultiplierIndex: Int) {
        _uiState.value = uiState.value.copy(currentMultiplierIndex = newMultiplierIndex)
    }

    fun itemClicked(index: Int) {
        uiState.value.items.getOrNull(index)?.let { originalItem ->
            val newItem = when (originalItem) {
                is RecipeItem.Ingredient -> {
                    originalItem.copy(editing = true)
                }

                is RecipeItem.Step -> {
                    originalItem.copy(editing = true)
                }

                else -> {
                    originalItem
                }
            }
            _uiState.value = uiState.value.copy(
                items = uiState.value.items.toMutableList().also {
                    it[index] = newItem
                }.toList()
            )
        }
    }

    fun addIngredientClicked() {
        viewModelScope.launch {
            val newIndex = uiState.value.items.run {
                indexOfLast { it is RecipeItem.Ingredient }.takeIf { it != -1 }?.plus(1)
                    ?: (indexOfLast { it is RecipeItem.AddIngredient })
            }
            val newItem =
                RecipeItem.Ingredient(-1, "", 0, "", editing = true)
            _uiState.value = uiState.value.copy(
                items = uiState.value.items.toMutableList().also {
                    it.add(newIndex, newItem)
                }.toList()
            )
        }
    }

    fun saveIngredient(index: Int, quantity: String, unitIndex: Int, name: String) {
        val ingredient = uiState.value.items[index] as RecipeItem.Ingredient
        viewModelScope.launch {
            if (ingredient.dataIndex != -1) {
                repository.updateIngredient(
                    recipeIndex,
                    ingredient.dataIndex,
                    quantity.toFloat(),
                    MeasurementUnit.entries[unitIndex],
                    name,
                )
            } else {
                repository.addIngredient(
                    recipeIndex,
                    quantity.toFloat(),
                    MeasurementUnit.entries[unitIndex],
                    name
                )
            }
        }
        _uiState.value = uiState.value.copy(
            items = uiState.value.items.toMutableList().also {
                it[index] = ingredient.copy(editing = false)
            }.toList()
        )
    }

    fun addStepClicked() {
        viewModelScope.launch {
            val newIndex = uiState.value.items.run {
                indexOfLast { it is RecipeItem.Step }.takeIf { it != -1 }?.plus(1)
                    ?: (indexOfLast { it is RecipeItem.AddStep })
            }
            val newItem = RecipeItem.Step(-1, "", "", editing = true)
            _uiState.value = uiState.value.copy(
                items = uiState.value.items.toMutableList().also {
                    it.add(newIndex, newItem)
                }.toList()
            )
        }
    }

    fun saveStep(index: Int, content: String) {
        val step = uiState.value.items[index] as RecipeItem.Step
        viewModelScope.launch {
            if (step.dataIndex != -1) {
                repository.updateStep(recipeIndex, step.dataIndex, content)
            } else {
                repository.addStep(recipeIndex, content)
            }
        }
        _uiState.value = uiState.value.copy(
            items = uiState.value.items.toMutableList().also {
                it[index] = step.copy(editing = false)
            }.toList()
        )
    }

    fun cancelEdit(index: Int) {
        val item = uiState.value.items[index] as? RecipeItem.Modifiable ?: return
        _uiState.value = uiState.value.copy(
            items = uiState.value.items.toMutableList().also {
                it[index] = item.duplicate(editing = false)
            }.toList()
        )
    }

    fun deleteItem(index: Int) {
        val item = uiState.value.items[index] as? RecipeItem.Modifiable ?: return
        viewModelScope.launch {
            when (item) {
                is RecipeItem.Ingredient -> repository.removeIngredient(recipeIndex, item.dataIndex)
                is RecipeItem.Step -> repository.removeStep(recipeIndex, item.dataIndex)
            }
        }
    }

    private fun genItems(recipe: Recipe): List<RecipeItem> {
        val items: MutableList<RecipeItem> = mutableListOf()
        items.add(RecipeItem.Header("Ingredients"))
        recipe.ingredientsList.toList().mapIndexed { index, ingredient ->
            RecipeItem.Ingredient(
                dataIndex = index,
                quantity = ingredient.quantity.toString(),
                measurementUnitIndex = MeasurementUnit.entries.indexOf(ingredient.unit),
                name = ingredient.name,
                editing = (uiState.value.items.getOrNull(index) as? RecipeItem.Ingredient)?.editing
                    ?: false,
            )
        }.let { items.addAll(it) }
        items.add(RecipeItem.AddIngredient)
        items.add(RecipeItem.Header("Steps"))
        recipe.stepsList.toList().mapIndexed { index, step ->
            RecipeItem.Step(
                dataIndex = index,
                index = (index + 1).toString(),
                text = step.text,
                editing = (uiState.value.items.getOrNull(index) as? RecipeItem.Step)?.editing
                    ?: false,
            )
        }.let { items.addAll(it) }
        items.add(RecipeItem.AddStep)
        return items.toList()
    }

    class Factory(context: Context, recipeIndex: Int) :
        ViewModelProvider.Factory by viewModelFactory({
            initializer {
                RecipeViewModel(RecipesRepository(context.recipeListDataStore), recipeIndex)
            }
        })
}
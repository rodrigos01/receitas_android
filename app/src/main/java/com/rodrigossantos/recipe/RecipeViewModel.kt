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
import kotlin.reflect.typeOf

class RecipeViewModel(private val repository: RecipesRepository, private val recipeIndex: Int) :
    ViewModel() {

    data class UiState(
        val recipeName: String,
        val availableMultipliers: List<Multiplier>,
        val currentMultiplierIndex: Int,
        val ingredients: List<RecipeItem>,
        val steps: List<RecipeItem>,
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
            emptyList(),
            false,
        )
    )
    private val recipeFlow = repository.getRecipe(recipeIndex).onEach {
        _uiState.value = uiState.value.copy(
            ingredients = genIngredients(it),
            steps = genSteps(it),
        )
    }
    val uiState: StateFlow<UiState> = combine(_uiState, recipeFlow) { state, recipe ->
        val multiplier = Multiplier.entries[state.currentMultiplierIndex].value
        fun updateItem(item: RecipeItem) =
            if (item is RecipeItem.Ingredient && item.dataIndex != -1 && recipe.ingredientsList.size > item.dataIndex) {
                val quantity = recipe.ingredientsList[item.dataIndex].quantity * multiplier
                item.copy(quantity = DecimalFormat("#.##").format(quantity))
            } else {
                item
            }

        val updatedIngredients = state.ingredients.map(::updateItem)
        val updatedSteps = state.steps.map(::updateItem)
        UiState(
            recipeName = recipe.name,
            availableMultipliers = Multiplier.entries,
            currentMultiplierIndex = state.currentMultiplierIndex,
            ingredients = updatedIngredients,
            steps = updatedSteps,
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

    inline fun <reified T : RecipeItem.Modifiable> itemClicked(index: Int) {
        when (typeOf<T>()) {
            typeOf<RecipeItem.Ingredient>() -> ingredientItemClicked(index)
            typeOf<RecipeItem.Step>() -> stepItemClicked(index)
        }
    }

    inline fun <reified T : RecipeItem.Modifiable> cancelEdit(index: Int) {
        when (typeOf<T>()) {
            typeOf<RecipeItem.Ingredient>() -> cancelIngredientEdit(index)
            typeOf<RecipeItem.Step>() -> cancelStepEdit(index)
        }
    }

    inline fun <reified T : RecipeItem.Modifiable> deleteItem(index: Int) {
        when (typeOf<T>()) {
            typeOf<RecipeItem.Ingredient>() -> deleteIngredient(index)
            typeOf<RecipeItem.Step>() -> deleteStep(index)
        }
    }

    fun ingredientItemClicked(index: Int) {
        uiState.value.ingredients.getOrNull(index)?.let { originalItem ->
            val newItem =
                (originalItem as? RecipeItem.Modifiable)?.duplicate(editing = true) ?: originalItem
            _uiState.value = uiState.value.copy(
                ingredients = uiState.value.ingredients.toMutableList().also {
                    it[index] = newItem
                }.toList()
            )
        }
    }

    fun stepItemClicked(index: Int) {
        uiState.value.steps.getOrNull(index)?.let { originalItem ->
            val newItem =
                (originalItem as? RecipeItem.Modifiable)?.duplicate(editing = true) ?: originalItem
            _uiState.value = uiState.value.copy(
                ingredients = uiState.value.steps.toMutableList().also {
                    it[index] = newItem
                }.toList()
            )
        }
    }

    fun addIngredientClicked() {
        viewModelScope.launch {
            val newIndex = uiState.value.ingredients.run {
                indexOfLast { it is RecipeItem.Ingredient }.takeIf { it != -1 }?.plus(1)
                    ?: (indexOfLast { it is RecipeItem.AddIngredient })
            }
            val newItem =
                RecipeItem.Ingredient(-1, "", 0, "", editing = true)
            _uiState.value = uiState.value.copy(
                ingredients = uiState.value.ingredients.toMutableList().also {
                    it.add(newIndex, newItem)
                }.toList()
            )
        }
    }

    fun saveIngredient(index: Int, quantity: String, unitIndex: Int, name: String) {
        val ingredient = uiState.value.ingredients[index] as RecipeItem.Ingredient
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
            ingredients = uiState.value.ingredients.toMutableList().also {
                it[index] = ingredient.copy(editing = false)
            }.toList()
        )
    }

    fun addStepClicked() {
        viewModelScope.launch {
            val newIndex = uiState.value.steps.run {
                indexOfLast { it is RecipeItem.Step }.takeIf { it != -1 }?.plus(1)
                    ?: (indexOfLast { it is RecipeItem.AddStep })
            }
            val newItem = RecipeItem.Step(-1, "", "", editing = true)
            _uiState.value = uiState.value.copy(
                steps = uiState.value.steps.toMutableList().also {
                    it.add(newIndex, newItem)
                }.toList()
            )
        }
    }

    fun saveStep(index: Int, content: String) {
        val step = uiState.value.steps[index] as RecipeItem.Step
        viewModelScope.launch {
            if (step.dataIndex != -1) {
                repository.updateStep(recipeIndex, step.dataIndex, content)
            } else {
                repository.addStep(recipeIndex, content)
            }
        }
        _uiState.value = uiState.value.copy(
            steps = uiState.value.steps.toMutableList().also {
                it[index] = step.copy(editing = false)
            }.toList()
        )
    }

    fun cancelIngredientEdit(index: Int) {
        val item = uiState.value.ingredients[index] as? RecipeItem.Modifiable ?: return
        _uiState.value = uiState.value.copy(
            ingredients = uiState.value.ingredients.toMutableList().also {
                if (item.dataIndex != -1) {
                    it[index] = item.duplicate(editing = false)
                } else {
                    it.removeAt(index)
                }
            }.toList()
        )
    }

    fun cancelStepEdit(index: Int) {
        val item = uiState.value.steps[index] as? RecipeItem.Modifiable ?: return
        _uiState.value = uiState.value.copy(
            steps = uiState.value.steps.toMutableList().also {
                if (item.dataIndex != -1) {
                    it[index] = item.duplicate(editing = false)
                } else {
                    it.removeAt(index)
                }
            }.toList()
        )
    }

    fun deleteIngredient(index: Int) {
        val item = uiState.value.ingredients[index] as? RecipeItem.Modifiable ?: return
        if (item.dataIndex == -1) {
            cancelIngredientEdit(index)
            return
        }
        viewModelScope.launch {
            repository.removeIngredient(recipeIndex, item.dataIndex)
        }
    }

    fun deleteStep(index: Int) {
        val item = uiState.value.steps[index] as? RecipeItem.Modifiable ?: return
        if (item.dataIndex == -1) {
            cancelStepEdit(index)
            return
        }
        viewModelScope.launch {
            repository.removeStep(recipeIndex, item.dataIndex)
        }
    }

    private fun genIngredients(recipe: Recipe): List<RecipeItem> {
        val items: MutableList<RecipeItem> = mutableListOf()
        items.add(RecipeItem.Header("Ingredients"))
        recipe.ingredientsList.toList().mapIndexed { index, ingredient ->
            RecipeItem.Ingredient(
                dataIndex = index,
                quantity = ingredient.quantity.toString(),
                measurementUnitIndex = MeasurementUnit.entries.indexOf(ingredient.unit),
                name = ingredient.name,
                editing = (uiState.value.ingredients.getOrNull(index) as? RecipeItem.Ingredient)?.editing
                    ?: false,
            )
        }.let { items.addAll(it) }
        items.add(RecipeItem.AddIngredient)
        return items.toList()
    }

    private fun genSteps(recipe: Recipe): List<RecipeItem> {
        val items: MutableList<RecipeItem> = mutableListOf()
        items.add(RecipeItem.Header("Steps"))
        recipe.stepsList.toList().mapIndexed { index, step ->
            RecipeItem.Step(
                dataIndex = index,
                index = (index + 1).toString(),
                text = step.text,
                editing = (uiState.value.steps.getOrNull(index) as? RecipeItem.Step)?.editing
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
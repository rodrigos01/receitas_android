package com.rodrigossantos.recipe

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecipeListViewModel(
    private val repository: RecipesRepository,
    private val navController: NavController
) : ViewModel() {

    data class UiState(
        val recipes: List<String>,
    )

    val uiState: StateFlow<UiState> = repository.recipes.map { data ->
        UiState(
            recipes = data.map { it.name }
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, UiState(emptyList()))

    fun addButtonClicked() {
        viewModelScope.launch {
            repository.createRecipe("New Recipe")
            val action =
                RecipeListFragmentDirections.actionRecipeListFragmentToRecipeFragment(uiState.value.recipes.lastIndex)
            navController.navigate(action)
        }
    }

    fun recipeClicked(index: Int) {
        val action =
            RecipeListFragmentDirections.actionRecipeListFragmentToRecipeFragment(index)
        navController.navigate(action)
    }

    class Factory(context: Context, navController: NavController) :
        ViewModelProvider.Factory by viewModelFactory({
            initializer {
                RecipeListViewModel(RecipesRepository(context.recipeListDataStore), navController)
            }
        })

}
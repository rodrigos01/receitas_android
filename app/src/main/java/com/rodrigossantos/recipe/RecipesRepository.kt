package com.rodrigossantos.recipe

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.map

class RecipesRepository(private val dataStore: DataStore<RecipeList>) {

    val recipes = dataStore.data.map { it.recipesList.toList().filterNotNull() }

    fun getRecipe(index: Int) = dataStore.data.map { it.getRecipes(index) }

    suspend fun createRecipe(name: String) {
        dataStore.updateData { currentData ->
            currentData.toBuilder()
                .addRecipes(Recipe.newBuilder().setName(name).build())
                .build()
        }
    }

    suspend fun updateRecipe(recipeIndex: Int, recipeName: String) {
        dataStore.updateData { currentData ->
            currentData.toBuilder().setRecipes(
                recipeIndex,
                currentData.getRecipes(recipeIndex).toBuilder().setName(recipeName)
            ).build()
        }
    }

    suspend fun deleteRecipe(recipeIndex: Int) {
        dataStore.updateData { currentData ->
            currentData.toBuilder().removeRecipes(
                recipeIndex,
            ).build()
        }
    }

    suspend fun addIngredient(
        recipeIndex: Int,
        quantity: Float,
        unit: MeasurementUnit,
        name: String
    ) {
        dataStore.updateData { currentData ->
            currentData.toBuilder()
                .setRecipes(
                    recipeIndex, currentData.getRecipes(recipeIndex).toBuilder()
                        .addIngredients(
                            Ingredient.newBuilder().setQuantity(quantity).setUnit(unit)
                                .setName(name)
                        )
                )
                .build()
        }
    }

    suspend fun updateIngredient(
        recipeIndex: Int,
        ingredientIndex: Int,
        quantity: Float,
        unit: MeasurementUnit,
        name: String
    ) {
        dataStore.updateData { currentData ->
            currentData.toBuilder()
                .setRecipes(
                    recipeIndex, currentData.getRecipes(recipeIndex).toBuilder()
                        .setIngredients(
                            ingredientIndex,
                            Ingredient.newBuilder().setQuantity(quantity).setUnit(unit)
                                .setName(name)
                        )
                )
                .build()
        }
    }

    suspend fun removeIngredient(recipeIndex: Int, ingredientIndex: Int) {
        dataStore.updateData { currentData ->
            currentData.toBuilder().setRecipes(
                recipeIndex,
                currentData.getRecipes(recipeIndex).toBuilder().removeIngredients(ingredientIndex)
            ).build()
        }
    }

    suspend fun removeStep(recipeIndex: Int, stepIndex: Int) {
        dataStore.updateData { currentData ->
            currentData.toBuilder().setRecipes(
                recipeIndex,
                currentData.getRecipes(recipeIndex).toBuilder().removeSteps(stepIndex)
            ).build()
        }
    }

    suspend fun addStep(recipeIndex: Int, content: String) {
        dataStore.updateData { currentData ->
            currentData.toBuilder()
                .setRecipes(
                    recipeIndex, currentData.getRecipes(recipeIndex).toBuilder()
                        .addSteps(Step.newBuilder().setText(content))
                )
                .build()
        }
    }

    suspend fun updateStep(recipeIndex: Int, stepIndex: Int, content: String) {
        dataStore.updateData { currentData ->
            currentData.toBuilder()
                .setRecipes(
                    recipeIndex, currentData.getRecipes(recipeIndex).toBuilder()
                        .setSteps(
                            stepIndex,
                            Step.newBuilder().setText(content)
                        )
                )
                .build()
        }
    }

}
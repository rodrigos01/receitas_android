package com.rodrigossantos.recipe

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

class IngredientSerializer : Serializer<Ingredient> {
    override val defaultValue: Ingredient = Ingredient.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Ingredient {
        try {
            return Ingredient.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: Ingredient, output: OutputStream) {
        t.writeTo(output)
    }
}

class RecipeSerializer : Serializer<Recipe> {
    override val defaultValue: Recipe = Recipe.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Recipe {
        try {
            return Recipe.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: Recipe, output: OutputStream) {
        t.writeTo(output)
    }
}

object RecipeListSerializer : Serializer<RecipeList> {
    override val defaultValue: RecipeList = RecipeList.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): RecipeList {
        try {
            return RecipeList.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: RecipeList, output: OutputStream) {
        t.writeTo(output)
    }
}

val Context.recipeListDataStore: DataStore<RecipeList> by dataStore(
    fileName = "recipe_list.pb",
    serializer = RecipeListSerializer
)
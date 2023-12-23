package com.rodrigossantos.recipe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rodrigossantos.recipe.databinding.FragmentRecipeListBinding
import com.rodrigossantos.recipe.databinding.RecipeListItemBinding

class RecipeListFragment : Fragment() {


    private val viewModel: RecipeListViewModel by viewModels(
        factoryProducer = { RecipeListViewModel.Factory(binding.root.context, findNavController()) }
    )

    private val binding: FragmentRecipeListBinding by lazy {
        FragmentRecipeListBinding.bind(requireView())
    }

    private val adapter by lazy { buildAdapter() }

    private fun buildAdapter() = createAdapter(
        create = RecipeListItemBinding::inflate,
        itemsProducer = viewLifecycleOwner.produceOnLifecycle<String> {
            viewModel.uiState.collect {
                items = it.recipes
            }
        },
    ) { binding, item, position ->
        binding.recipeName.text = item
        binding.root.setOnClickListener { viewModel.recipeClicked(position) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_recipe_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<RecyclerView>(R.id.recipe_list)
        list.layoutManager = LinearLayoutManager(view.context)
        list.adapter = adapter
        binding.addButton.setOnClickListener { viewModel.addButtonClicked() }
    }
}
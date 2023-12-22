package com.rodrigossantos.recipe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rodrigossantos.recipe.databinding.FragmentRecipeListBinding
import com.rodrigossantos.recipe.databinding.RecipeListItemBinding
import kotlinx.coroutines.launch

class RecipeListFragment : Fragment() {


    private val viewModel: RecipeListViewModel by viewModels(
        factoryProducer = { RecipeListViewModel.Factory(binding.root.context, findNavController()) }
    )

    private val binding: FragmentRecipeListBinding by lazy {
        FragmentRecipeListBinding.bind(requireView())
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
        val adapter = RecipeListAdapter(recipeClicked = viewModel::recipeClicked)
        list.adapter = adapter

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.uiState.collect {
                    adapter.recipes = it.recipes
                }
            }
        }

        binding.addButton.setOnClickListener { viewModel.addButtonClicked() }
    }
}

class RecipeListAdapter(private val recipeClicked: (Int) -> Unit) :
    RecyclerView.Adapter<RecipeListAdapter.ViewHolder>() {

    var recipes: List<String> = emptyList()
        get() = field
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            RecipeListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        recipes.getOrNull(position).let {
            holder.nameText.text = it
        }
        holder.itemView.setOnClickListener { recipeClicked(position) }
    }

    override fun getItemCount(): Int = recipes.count()

    class ViewHolder(binding: RecipeListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val nameText: TextView = binding.recipeName
    }
}
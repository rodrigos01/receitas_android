package com.rodrigossantos.recipe

import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.launch

typealias SimpleAdapter = RecyclerView.Adapter<out RecyclerView.ViewHolder>

interface MutableItemListSource<T> {
    var items: List<T>
}

private abstract class TypedBindingAdapter<Binding : ViewBinding, V, T> :
    RecyclerView.Adapter<TypedBindingAdapter.TypedBindingViewHolder<Binding>>(),
    MutableItemListSource<T> {

    override var items: List<T> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount(): Int {
        return items.size
    }


    private val viewTypes: MutableSet<V> = mutableSetOf()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewTypeIndex: Int,
    ): TypedBindingViewHolder<Binding> {
        val actualViewType = viewTypes.elementAt(viewTypeIndex)
        return TypedBindingViewHolder(
            createBinding(
                parent,
                actualViewType,
            )
        )
    }

    override fun onBindViewHolder(
        holder: TypedBindingViewHolder<Binding>,
        position: Int
    ) {
        bind(holder.binding, items[position], position)
    }

    override fun getItemViewType(position: Int): Int {
        val type = getViewType(items[position])
        if (!viewTypes.contains(type)) {
            viewTypes.add(type)
        }
        return viewTypes.indexOf(type)
    }

    abstract fun getViewType(item: T): V

    abstract fun createBinding(parent: ViewGroup, viewType: V): Binding

    abstract fun bind(binding: Binding, item: T, position: Int)
    abstract class BindingViewHolder<B : ViewBinding>(binding: B) :
        RecyclerView.ViewHolder(binding.root)

    class TypedBindingViewHolder<Binding : ViewBinding>(val binding: Binding) :
        BindingViewHolder<Binding>(binding)
}

fun interface ItemProducer<T> {
    fun produce(source: MutableItemListSource<T>)
}

fun <T> LifecycleOwner.produceOnLifecycle(
    produce: suspend MutableItemListSource<T>.() -> Unit
) = ItemProducer {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
            it.produce()
        }
    }
}

fun <B : ViewBinding, T> createAdapter(
    create: (parent: ViewGroup) -> B,
    itemsProducer: ItemProducer<T>,
    bind: (binding: B, item: T, position: Int) -> Unit
): SimpleAdapter = createAdapter(
    getViewType = { 0 }, create = { parent, _ -> create(parent) }, itemsProducer, bind,
)

fun <B : ViewBinding, V, T> createAdapter(
    getViewType: (T) -> V,
    create: (parent: ViewGroup, viewType: V) -> B,
    itemsProducer: ItemProducer<T>,
    bind: (binding: B, item: T, position: Int) -> Unit
): SimpleAdapter =
    object : TypedBindingAdapter<B, V, T>() {
        init {
            itemsProducer.produce(this)
        }

        override fun createBinding(parent: ViewGroup, viewType: V): B =
            create(parent, viewType)

        override fun bind(binding: B, item: T, position: Int) =
            bind(binding, item, position)

        override fun getViewType(item: T): V = getViewType(item)
    }

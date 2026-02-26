package com.jons.iptv.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jons.iptv.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val onClick: (String) -> Unit
) : ListAdapter<String, CategoryAdapter.CategoryViewHolder>(Diff) {

    private var selectedCategory: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position), getItem(position) == selectedCategory)
    }

    fun setSelected(category: String) {
        selectedCategory = category
        notifyDataSetChanged()
    }

    inner class CategoryViewHolder(
        private val binding: ItemCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: String, selected: Boolean) {
            binding.categoryName.text = category
            binding.root.isSelected = selected
            binding.root.setOnClickListener { onClick(category) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    }
}

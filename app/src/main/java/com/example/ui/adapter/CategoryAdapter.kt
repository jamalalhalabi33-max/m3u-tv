package com.example.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.model.CategorySummary
import com.example.ui.leanback.LeanbackUtils

class CategoryAdapter(
    private val onCategoryClick: (CategorySummary) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private val items = mutableListOf<CategorySummary>()

    fun submitList(newList: List<CategorySummary>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_card, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_category_name)
        private val tvCount: TextView = itemView.findViewById(R.id.tv_category_count)

        init {
            LeanbackUtils.applyFocusHighlight(itemView, scale = 1.025f, elevationDp = 8f)
        }

        fun bind(category: CategorySummary) {
            tvName.text = category.name
            tvCount.text = "${category.count} CH"

            itemView.setOnClickListener {
                onCategoryClick(category)
            }
        }
    }
}

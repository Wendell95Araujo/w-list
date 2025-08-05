package com.example.wlist.ui.recyclerview.adapter

import androidx.recyclerview.widget.DiffUtil

class ShoppingDiffCallback(
    private val oldList: List<ListItem>,
    private val newList: List<ListItem>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]

        if (oldItem is ListItem.HeaderItem && newItem is ListItem.HeaderItem) {
            return oldItem.category == newItem.category
        }
        if (oldItem is ListItem.ContentItem && newItem is ListItem.ContentItem) {
            return oldItem.item.id == newItem.item.id
        }
        return false
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {

        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}
package com.example.wlist.ui.recyclerview.adapter

import android.content.Context
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.wlist.R
import com.example.wlist.model.ShoppingItem
import java.text.NumberFormat

sealed class ListItem {
    data class HeaderItem(val category: String) : ListItem()
    data class ContentItem(val item: ShoppingItem) : ListItem()
}

class ShoppingListAdapter(
    private val context: Context,
    private var listItems: MutableList<ListItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    var onItemClick: ((ShoppingItem) -> Unit)? = null
    var onItemCompletedChanged: ((ShoppingItem) -> Unit)? = null

    inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = itemView.findViewById(R.id.product_name_text)
        private val details: TextView = itemView.findViewById(R.id.quantity_price_details_text)
        private val subtotal: TextView = itemView.findViewById(R.id.item_subtotal_text)
        private val purchasedCheckbox: CheckBox = itemView.findViewById(R.id.item_purchased_checkbox)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    (listItems[position] as? ListItem.ContentItem)?.let { contentItem ->
                        onItemClick?.invoke(contentItem.item)
                    }
                }
            }
        }

        fun bind(item: ShoppingItem) {
            name.text = item.productName
            details.text = context.getString(R.string.quantity_and_price_format, item.quantity, formatCurrency(item.unitPrice))
            subtotal.text = formatCurrency(item.quantity * item.unitPrice)
            purchasedCheckbox.setOnCheckedChangeListener(null)
            purchasedCheckbox.isChecked = item.isPurchased
            updateTextStyle(item.isPurchased)
            purchasedCheckbox.setOnCheckedChangeListener { _, isChecked ->
                val updatedItem = item.copy(isPurchased = isChecked)
                onItemCompletedChanged?.invoke(updatedItem)
                updateTextStyle(isChecked)
                (context as? OnTotalAmountChangeListener)?.onTotalChanged()
            }
        }
        private fun updateTextStyle(isPurchased: Boolean) {
            val paintFlags = if (isPurchased) Paint.STRIKE_THRU_TEXT_FLAG else 0
            name.paintFlags = paintFlags
            details.paintFlags = paintFlags
            subtotal.paintFlags = paintFlags
        }
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.category_header_title)
        fun bind(category: String) {
            val displayText = if (category.equals("uncategorized", ignoreCase = true)) {
                context.getString(R.string.category_uncategorized)
            } else {
                category
            }

            title.text = displayText
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (listItems[position]) {
            is ListItem.HeaderItem -> TYPE_HEADER
            is ListItem.ContentItem -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(context).inflate(R.layout.item_shopping_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(context).inflate(R.layout.item_shopping, parent, false)
            ItemViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val currentItem = listItems[position]) {
            is ListItem.HeaderItem -> (holder as HeaderViewHolder).bind(currentItem.category)
            is ListItem.ContentItem -> (holder as ItemViewHolder).bind(currentItem.item)
        }
    }

    override fun getItemCount(): Int = listItems.size

    fun update(newList: List<ListItem>) {
        val diffCallback = ShoppingDiffCallback(this.listItems, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        this.listItems.clear()
        this.listItems.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }

    fun getItemAt(position: Int): ListItem? {
        return if (position in 0 until listItems.size) {
            listItems[position]
        } else {
            null
        }
    }

    private fun formatCurrency(value: Double): String {
        val currentLocale = context.resources.configuration.locales[0]
        val formatter = NumberFormat.getCurrencyInstance(currentLocale)
        return formatter.format(value)
    }

    interface OnTotalAmountChangeListener {
        fun onTotalChanged()
    }

    fun getCurrentList(): List<ListItem> {
        return listItems
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < listItems.size && toPosition < listItems.size) {
            val fromItem = listItems.removeAt(fromPosition)
            listItems.add(toPosition, fromItem)
            notifyItemMoved(fromPosition, toPosition)
        }
    }

}
package com.example.todolist.ui.activity

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.Group
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.todolist.AppDatabase
import com.example.todolist.R
import com.example.todolist.dao.ShoppingDao
import com.example.todolist.model.ShoppingItem
import com.example.todolist.ui.recyclerview.adapter.ListItem
import com.example.todolist.ui.recyclerview.adapter.ShoppingListAdapter
import com.example.todolist.util.normalize
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.launch
import java.text.NumberFormat

class ShoppingListActivity : AppCompatActivity(), ShoppingListAdapter.OnTotalAmountChangeListener {

    private lateinit var adapter: ShoppingListAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var totalAmountText: TextView
    private lateinit var shoppingDao: ShoppingDao

    private val formLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            updateShoppingList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shopping_list)

        shoppingDao = AppDatabase.getDatabase(this).shoppingDao()

        setupToolbar()
        initializeViews()
        setupRecyclerView()
        setupFab()
        setupItemTouchHelper()
    }

    override fun onResume() {
        super.onResume()
        updateShoppingList()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.shopping_list_menu, menu)

        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView

        searchView?.queryHint = getString(R.string.search_items_hint)

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                updateShoppingList(newText)
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                shareShoppingList()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.shopping_recycler_view)
        totalAmountText = findViewById(R.id.total_amount_text)
    }

    private fun setupRecyclerView() {
        adapter = ShoppingListAdapter(this, mutableListOf())
        recyclerView.adapter = adapter
        adapter.onItemClick = { item ->
            val intent = Intent(this, ShoppingItemFormActivity::class.java).apply {
                putExtra(ShoppingItemFormActivity.EXTRA_ITEM_ID, item.id)
            }
            formLauncher.launch(intent)
        }
        adapter.onItemCompletedChanged = { updatedItem ->
            lifecycleScope.launch {
                shoppingDao.updateItem(updatedItem)
                updateTotalAmount()
            }
        }
    }

    private fun setupFab() {
        findViewById<FloatingActionButton>(R.id.fab_add_shopping_item).setOnClickListener {
            formLauncher.launch(Intent(this, ShoppingItemFormActivity::class.java))
        }
    }

    private fun updateShoppingList(searchQuery: String? = null) {
        lifecycleScope.launch {
            val flatList = if (searchQuery.isNullOrBlank()) {
                shoppingDao.getAllItems()
            } else {
                shoppingDao.searchItemsByName("%${normalize(searchQuery)}%")
            }

            val groupedMap = flatList.groupBy { it.category ?: "uncategorized" }
            val structuredList = mutableListOf<ListItem>()
            val sortedKeys = groupedMap.keys.sortedWith(compareBy<String> { it == "uncategorized" }.thenBy { it })

            sortedKeys.forEach { category ->
                structuredList.add(ListItem.HeaderItem(category))
                groupedMap[category]?.forEach { item ->
                    structuredList.add(ListItem.ContentItem(item))
                }
            }

            adapter.update(structuredList)
            updateEmptyViewVisibility()
            updateTotalAmount()
        }
    }

    private fun updateEmptyViewVisibility() {
        val emptyGroup = findViewById<Group>(R.id.empty_shopping_list_group)
        if (adapter.itemCount == 0) {
            recyclerView.visibility = View.GONE
            emptyGroup.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyGroup.visibility = View.GONE
        }
    }

    private fun updateTotalAmount() {
        lifecycleScope.launch {
            val newTotal = shoppingDao.getAllItems().sumOf { it.quantity * it.unitPrice }
            val currentTotal = parseCurrencyValue(totalAmountText.text.toString())
            if (currentTotal == newTotal) {
                if (currentTotal == 0.0) {
                    totalAmountText.text = formatCurrency(0.0)
                }
                return@launch
            }
            val animator = ValueAnimator.ofFloat(currentTotal.toFloat(), newTotal.toFloat())
            animator.duration = 400
            animator.addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Float
                totalAmountText.text = formatCurrency(animatedValue.toDouble())
            }
            Handler(Looper.getMainLooper()).postDelayed({
                animator.start()
            }, 100)
        }
    }

    override fun onTotalChanged() {
        updateTotalAmount()
    }

    private fun shareShoppingList() {
        lifecycleScope.launch {
            val items = shoppingDao.getAllItems()
            if (items.isEmpty()) {
                Toast.makeText(this@ShoppingListActivity, getString(R.string.toast_empty_list), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val shareText = buildString {
                append(getString(R.string.share_title_shopping) + "\n\n")
                val groupedItems = items.groupBy { it.category ?: "uncategorized" }
                val sortedKeys = groupedItems.keys.sortedWith(compareBy<String> { it == "uncategorized" }.thenBy { it })
                sortedKeys.forEach { category ->
                    val categoryDisplay = if (category == "uncategorized") getString(R.string.category_uncategorized) else category
                    append("--- $categoryDisplay ---\n")
                    groupedItems[category]?.forEach { item ->
                        val status = if (item.isPurchased) "[x]" else "[ ]"
                        append("$status ${item.quantity}x ${item.productName}\n")
                    }
                    append("\n")
                }
            }
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }
    }

    private fun formatCurrency(value: Double): String {
        val currentLocale = this.resources.configuration.locales[0]
        val formatter = NumberFormat.getCurrencyInstance(currentLocale)
        return formatter.format(value)
    }

    private fun parseCurrencyValue(currencyString: String?): Double {
        if (currencyString.isNullOrBlank()) return 0.0
        return try {
            val cleanString = currencyString.replace(Regex("[^\\d,-]"), "").replace(',', '.')
            if (cleanString.isBlank()) 0.0 else cleanString.toDouble()
        } catch (e: NumberFormatException) {
            0.0
        }
    }

    private fun setupItemTouchHelper() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition

                if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
                    return false
                }

                val fromItem = adapter.getItemAt(fromPosition)
                val toItem = adapter.getItemAt(toPosition)

                if (fromItem is ListItem.ContentItem && toItem is ListItem.ContentItem) {
                    if (fromItem.item.category != toItem.item.category) {
                        return false
                    }
                } else {
                    return false
                }

                adapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    (adapter.getItemAt(position) as? ListItem.ContentItem)?.let { contentItem ->
                        val itemToDelete = contentItem.item
                        lifecycleScope.launch {
                            shoppingDao.deleteItem(itemToDelete)
                            updateShoppingList()
                            showUndoSnackbar(itemToDelete)
                        }
                    }
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                saveNewOrder()
            }

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                if (viewHolder is ShoppingListAdapter.HeaderViewHolder) {
                    return makeMovementFlags(0, 0)
                }
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                val swipeFlags = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                return makeMovementFlags(dragFlags, swipeFlags)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                RecyclerViewSwipeDecorator.Builder(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    .addBackgroundColor(ContextCompat.getColor(this@ShoppingListActivity, R.color.task_overdue_red))
                    .addActionIcon(R.drawable.ic_delete_white_24)
                    .create()
                    .decorate()
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun saveNewOrder() {
        lifecycleScope.launch {
            val currentList = adapter.getCurrentList()
            val groupedItems = currentList
                .filterIsInstance<ListItem.ContentItem>()
                .groupBy { it.item.category ?: "uncategorized" }

            val itemsToUpdate = mutableListOf<ShoppingItem>()

            groupedItems.forEach { (_, items) ->
                items.forEachIndexed { index, listItem ->
                    itemsToUpdate.add(listItem.item.copy(displayOrder = index.toLong()))
                }
            }

            if (itemsToUpdate.isNotEmpty()) {
                shoppingDao.updateItems(itemsToUpdate)
            }
        }
    }


    private fun showUndoSnackbar(deletedItem: ShoppingItem) {
        Snackbar.make(recyclerView, getString(R.string.snackbar_item_removed), Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.snackbar_action_undo)) {
                lifecycleScope.launch {
                    shoppingDao.insertItem(deletedItem)
                    updateShoppingList()
                }
            }
            .show()
    }
}
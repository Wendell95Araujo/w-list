package com.example.wlist.ui.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.wlist.AppDatabase
import com.example.wlist.R
import com.example.wlist.dao.ShoppingDao
import com.example.wlist.model.ShoppingItem
import com.example.wlist.model.ShoppingItem.Companion.normalize
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.util.Locale

class ShoppingItemFormActivity : AppCompatActivity() {

    private lateinit var nameInput: TextInputEditText
    private lateinit var categoryInput: AutoCompleteTextView
    private lateinit var quantityInput: TextInputEditText
    private lateinit var priceInput: TextInputEditText
    private lateinit var deleteButton: Button
    private var editingItem: ShoppingItem? = null
    private lateinit var shoppingDao: ShoppingDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shopping_item_form)

        shoppingDao = AppDatabase.getDatabase(this).shoppingDao()

        setupToolbar()
        initializeViews()
        setupCategoryAutoComplete()
        loadItemIfEditing()
        setupSaveButton()
        setupDeleteButton()
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

    private fun initializeViews() {
        nameInput = findViewById(R.id.product_name_input)
        categoryInput = findViewById(R.id.category_input)
        quantityInput = findViewById(R.id.quantity_input)
        priceInput = findViewById(R.id.price_input)
        deleteButton = findViewById(R.id.delete_item_button)

        if (intent.getStringExtra(EXTRA_ITEM_ID) == null) {
            quantityInput.setText("1")
        }
    }

    private fun setupCategoryAutoComplete() {
        lifecycleScope.launch {
            val allItems = shoppingDao.getAllItems()
            val existingCategories = allItems.mapNotNull { it.category }
                .filter { it.isNotBlank() && it != "uncategorized" }
                .distinct()
                .sorted()

            val adapter = ArrayAdapter(this@ShoppingItemFormActivity, android.R.layout.simple_dropdown_item_1line, existingCategories)
            categoryInput.setAdapter(adapter)

            categoryInput.threshold = 1
            categoryInput.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    categoryInput.showDropDown()
                }
            }
            categoryInput.setOnClickListener {
                categoryInput.showDropDown()
            }
        }
    }

    private fun loadItemIfEditing() {
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID)
        if (itemId != null) {
            lifecycleScope.launch {
                editingItem = shoppingDao.getItemById(itemId)
                editingItem?.let { item ->
                    nameInput.setText(item.productName)
                    if (item.category != "uncategorized") {
                        categoryInput.setText(item.category)
                    }
                    quantityInput.setText(item.quantity.toString())
                    if (item.unitPrice > 0.0) {
                        priceInput.setText(String.format(Locale.US, "%.2f", item.unitPrice))
                    }
                    deleteButton.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupSaveButton() {
        findViewById<Button>(R.id.save_item_button).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val quantityStr = quantityInput.text.toString().trim()
            val priceStr = priceInput.text.toString()
            var category = categoryInput.text.toString().trim()

            if (name.isEmpty() || quantityStr.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_fill_required_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (category.isEmpty()) {
                category = "uncategorized"
            }

            val formattedName = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            val formattedCategory = category.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            val quantity = quantityStr.toIntOrNull() ?: 1
            val price = priceStr.toDoubleOrNull() ?: 0.0

            lifecycleScope.launch {
                val resultIntent = Intent()
                if (editingItem != null) {
                    val updatedItem = editingItem!!.copy(
                        productName = formattedName,
                        productNameNormalized = normalize(formattedName),
                        category = formattedCategory,
                        quantity = quantity,
                        unitPrice = price
                    )
                    shoppingDao.updateItem(updatedItem)
                    resultIntent.putExtra(EXTRA_ACTION_TYPE, "EDITED")
                } else {
                    val newItem = ShoppingItem(
                        productName = formattedName,
                        productNameNormalized = normalize(formattedName),
                        category = formattedCategory,
                        quantity = quantity,
                        unitPrice = price
                    )
                    shoppingDao.insertItem(newItem)
                    resultIntent.putExtra(EXTRA_ACTION_TYPE, "ADDED")
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    private fun setupDeleteButton() {
        deleteButton.setOnClickListener {
            editingItem?.let { itemToRemove ->
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_delete_shopping_title))
                    .setMessage(getString(R.string.dialog_delete_message, itemToRemove.productName))
                    .setPositiveButton(getString(R.string.dialog_button_yes)) { _, _ ->
                        lifecycleScope.launch {
                            shoppingDao.deleteItem(itemToRemove)
                            val resultIntent = Intent()
                            resultIntent.putExtra(EXTRA_ACTION_TYPE, "DELETED")
                            setResult(Activity.RESULT_OK, resultIntent)
                            finish()
                        }
                    }
                    .setNegativeButton(getString(R.string.dialog_button_no), null)
                    .show()
            }
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_ACTION_TYPE = "extra_action_type"
    }
}
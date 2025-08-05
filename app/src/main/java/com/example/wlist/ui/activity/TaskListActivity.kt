package com.example.wlist.ui.activity

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.Group
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.wlist.*
import com.example.wlist.dao.TasksDao
import com.example.wlist.model.TaskDateCategory
import com.example.wlist.model.TaskItem
import com.example.wlist.ui.recyclerview.adapter.TaskListAdapter
import com.example.wlist.ui.recyclerview.adapter.TaskListItem
import com.example.wlist.util.normalize
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TaskListActivity : AppCompatActivity() {

    private lateinit var adapter: TaskListAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tasksDao: TasksDao

    private val formLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            updateAdapter()

            val actionType = result.data?.getStringExtra(TaskItemFormActivity.EXTRA_ACTION_TYPE)
            when (actionType) {
                "ADDED" -> Toast.makeText(this, getString(R.string.toast_task_saved), Toast.LENGTH_SHORT).show()
                "EDITED" -> Toast.makeText(this, getString(R.string.toast_task_edited), Toast.LENGTH_SHORT).show()
                "DELETED" -> Toast.makeText(this, getString(R.string.toast_task_removed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_list)
        tasksDao = AppDatabase.getDatabase(this).tasksDao()
        setupToolbar()
        initializeViews()
        setupFab()
        setupRecyclerView()
        setupItemTouchHelper()
    }

    override fun onResume() {
        super.onResume()
        updateAdapter()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            val typedValue = TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
            val color = typedValue.data
            toolbar.navigationIcon?.let { icon -> DrawableCompat.setTint(icon, color) }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.task_list_menu, menu)
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
        val iconColor = typedValue.data
        val searchItem = menu?.findItem(R.id.action_search)
        searchItem?.icon?.let { DrawableCompat.setTint(it, iconColor) }
        val searchView = searchItem?.actionView as? SearchView
        searchView?.queryHint = getString(R.string.search_tasks_hint)
        val searchEditText = searchView?.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        searchEditText?.setTextColor(iconColor)
        searchEditText?.setHintTextColor(iconColor)
        val closeIcon = searchView?.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        closeIcon?.setColorFilter(iconColor)
        searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                val backArrow = searchView?.findViewById<ImageView>(androidx.appcompat.R.id.search_button)
                backArrow?.setColorFilter(iconColor)
                return true
            }
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean = true
        })
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                updateAdapter(newText)
                return true
            }
        })
        return true
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recyclerView)
    }

    private fun setupFab() {
        findViewById<FloatingActionButton>(R.id.floatingActionButton).setOnClickListener {
            formLauncher.launch(Intent(this, TaskItemFormActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = TaskListAdapter(this, mutableListOf())
        recyclerView.adapter = adapter
        adapter.onItemClick = { task ->
            val intent = Intent(this, TaskItemFormActivity::class.java).apply {
                putExtra(TaskItemFormActivity.EXTRA_TASK_ID, task.id)
            }
            formLauncher.launch(intent)
        }
        adapter.onCompletedChanged = { task, isCompleted ->
            lifecycleScope.launch {
                val updatedTask = task.copy(isCompleted = isCompleted)
                tasksDao.updateTask(updatedTask)
                if (isCompleted) {
                    cancelAlarm(updatedTask)
                } else {
                    scheduleAlarm(updatedTask)
                }
                updateAdapter()
                updateTaskWidgets(this@TaskListActivity)
            }
        }
    }

    private fun updateAdapter(searchQuery: String? = null) {
        lifecycleScope.launch {
            val flatList = if (searchQuery.isNullOrBlank()) {
                tasksDao.getAllTasks()
            } else {
                tasksDao.searchTasksByName("%${normalize(searchQuery)}%")
            }
            val groupedMap = flatList.groupBy { it.getDateCategory() }
            val structuredList = mutableListOf<TaskListItem>()
            TaskDateCategory.entries.forEach { category ->
                val tasksInCategory = groupedMap[category]
                if (!tasksInCategory.isNullOrEmpty()) {
                    structuredList.add(TaskListItem.HeaderItem(category))
                    tasksInCategory.forEach { task ->
                        structuredList.add(TaskListItem.ContentItem(task))
                    }
                }
            }
            adapter.update(structuredList)
            updateEmptyViewVisibility()
        }
    }

    private fun updateEmptyViewVisibility() {
        val emptyGroup = findViewById<Group>(R.id.empty_list_group)
        if (adapter.itemCount == 0) {
            recyclerView.visibility = View.GONE
            emptyGroup.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyGroup.visibility = View.GONE
        }
    }

    private fun setupItemTouchHelper() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            private var dragFrom = -1
            private var dragTo = -1
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) return false
                if (viewHolder is TaskListAdapter.HeaderViewHolder || target is TaskListAdapter.HeaderViewHolder) return false
                if (dragFrom == -1) {
                    dragFrom = fromPosition
                }
                dragTo = toPosition
                adapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    adapter.getTaskAt(position)?.let { taskToDelete ->
                        lifecycleScope.launch {
                            cancelAlarm(taskToDelete)
                            tasksDao.deleteTask(taskToDelete)
                            updateAdapter()
                            showUndoSnackbar(taskToDelete)
                            updateTaskWidgets(this@TaskListActivity)
                        }
                    }
                }
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_IDLE && dragFrom != -1) {
                    saveNewOrder()
                    dragFrom = -1
                    dragTo = -1
                }
            }

            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (viewHolder is TaskListAdapter.HeaderViewHolder) {
                    return makeMovementFlags(0, 0)
                }
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                val swipeFlags = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                return makeMovementFlags(dragFlags, swipeFlags)
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                RecyclerViewSwipeDecorator.Builder(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    .addBackgroundColor(ContextCompat.getColor(this@TaskListActivity, R.color.task_overdue_red))
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
            val tasksToUpdate = currentList
                .filterIsInstance<TaskListItem.ContentItem>()
                .map { it.task }
                .mapIndexed { index, taskItem -> taskItem.copy(displayOrder = index.toLong()) }
            tasksDao.updateTasks(tasksToUpdate)
            updateAdapter()
            updateTaskWidgets(this@TaskListActivity)
        }
    }

    private fun showUndoSnackbar(deletedTask: TaskItem) {
        Snackbar.make(recyclerView, getString(R.string.snackbar_task_removed), Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.snackbar_action_undo)) {
                lifecycleScope.launch {
                    tasksDao.insertTask(deletedTask)
                    scheduleAlarm(deletedTask)
                    updateAdapter()
                    updateTaskWidgets(this@TaskListActivity)
                }
            }
            .show()
    }

    private fun scheduleAlarm(task: TaskItem) {
        if (task.isCompleted || task.datetime.isBlank()) return
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) return
        }
        try {
            val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val calendar = Calendar.getInstance().apply { time = format.parse(task.datetime)!! }
            if (calendar.before(Calendar.getInstance())) return

            val intent = Intent(this, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.ACTION_SHOW_NOTIFICATION
                putExtra(NotificationReceiver.TASK_ID_KEY, task.id)
                putExtra(NotificationReceiver.TASK_NAME_KEY, task.name)
                putExtra(NotificationReceiver.TASK_DATETIME_KEY, task.datetime)
                putExtra(NotificationReceiver.NOTIFICATION_ID_KEY, task.id.hashCode())
            }

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                task.id.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            AlarmManagerCompat.setExactAndAllowWhileIdle(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.toast_could_not_schedule_reminder), Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelAlarm(task: TaskItem) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_SHOW_NOTIFICATION
            putExtra(NotificationReceiver.TASK_ID_KEY, task.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
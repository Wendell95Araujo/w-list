package com.example.todolist.ui.activity

import android.app.Activity
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.AlarmManagerCompat
import androidx.lifecycle.lifecycleScope
import com.example.todolist.AppDatabase
import com.example.todolist.NotificationReceiver
import com.example.todolist.R
import com.example.todolist.updateTaskWidgets
import com.example.todolist.dao.TasksDao
import com.example.todolist.model.TaskItem
import com.example.todolist.util.normalize
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TaskItemFormActivity : AppCompatActivity() {

    private lateinit var nameField: TextInputEditText
    private lateinit var descriptionField: TextInputEditText
    private lateinit var datetimeField: TextInputEditText
    private lateinit var deleteButton: Button
    private var editingTaskItem: TaskItem? = null
    private val selectedDateTime = Calendar.getInstance()
    private lateinit var tasksDao: TasksDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_form)
        tasksDao = AppDatabase.getDatabase(this).tasksDao()
        setupToolbar()
        initializeViews()
        loadTaskIfEditing()
        setupSaveButton()
        setupDeleteButton()
        setupDateTimeField()
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
        nameField = findViewById(R.id.name)
        descriptionField = findViewById(R.id.description)
        datetimeField = findViewById(R.id.datetime)
        deleteButton = findViewById(R.id.delete_task_button)
    }

    private fun loadTaskIfEditing() {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        if (taskId != null) {
            lifecycleScope.launch {
                editingTaskItem = tasksDao.getTaskById(taskId)
                editingTaskItem?.let {
                    nameField.setText(it.name)
                    descriptionField.setText(it.description)
                    datetimeField.setText(it.datetime)
                    deleteButton.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupDateTimeField() {
        datetimeField.setOnClickListener {
            showDatePickerDialog()
        }
    }

    private fun showDatePickerDialog() {
        val currentDateTime = Calendar.getInstance()
        val year = currentDateTime.get(Calendar.YEAR)
        val month = currentDateTime.get(Calendar.MONTH)
        val day = currentDateTime.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            selectedDateTime.set(selectedYear, selectedMonth, selectedDay)
            showTimePickerDialog()
        }, year, month, day).show()
    }

    private fun showTimePickerDialog() {
        val currentDateTime = Calendar.getInstance()
        val hour = currentDateTime.get(Calendar.HOUR_OF_DAY)
        val minute = currentDateTime.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            selectedDateTime.set(Calendar.HOUR_OF_DAY, selectedHour)
            selectedDateTime.set(Calendar.MINUTE, selectedMinute)
            updateDateTimeField()
        }, hour, minute, true).show()
    }

    private fun updateDateTimeField() {
        val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        datetimeField.setText(format.format(selectedDateTime.time))
    }

    private fun setupSaveButton() {
        findViewById<Button>(R.id.submit_button).setOnClickListener {
            val name = nameField.text.toString().trim()
            val description = descriptionField.text.toString().trim()
            val datetime = datetimeField.text.toString()

            if (name.isEmpty() || description.isEmpty() || datetime.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val formattedName = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            val formattedDescription = description.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

            lifecycleScope.launch {
                val resultIntent = Intent()
                if (editingTaskItem != null) {
                    val updatedTask = editingTaskItem!!.copy(
                        name = formattedName,
                        nameNormalized = normalize(formattedName),
                        description = formattedDescription,
                        datetime = datetime
                    )
                    tasksDao.updateTask(updatedTask)
                    cancelAlarm(updatedTask)
                    scheduleAlarm(updatedTask)
                    resultIntent.putExtra(EXTRA_ACTION_TYPE, "EDITED")
                } else {
                    val newTaskItem = TaskItem(
                        name = formattedName,
                        nameNormalized = normalize(formattedName),
                        description = formattedDescription,
                        datetime = datetime
                    )
                    tasksDao.insertTask(newTaskItem)
                    scheduleAlarm(newTaskItem)
                    resultIntent.putExtra(EXTRA_ACTION_TYPE, "ADDED")
                }
                setResult(Activity.RESULT_OK, resultIntent)
                updateTaskWidgets(this@TaskItemFormActivity)
                finish()
            }
        }
    }

    private fun setupDeleteButton() {
        deleteButton.setOnClickListener {
            editingTaskItem?.let { taskToRemove ->
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_delete_task_title))
                    .setMessage(getString(R.string.dialog_delete_message, taskToRemove.name))
                    .setPositiveButton(getString(R.string.dialog_button_yes)) { _, _ ->
                        lifecycleScope.launch {
                            cancelAlarm(taskToRemove)
                            tasksDao.deleteTask(taskToRemove)
                            setResult(Activity.RESULT_OK)
                            updateTaskWidgets(this@TaskItemFormActivity)
                            finish()
                        }
                    }
                    .setNegativeButton(getString(R.string.dialog_button_no), null)
                    .show()
            }
        }
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

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_ACTION_TYPE = "extra_action_type"
    }
}
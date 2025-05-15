package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings

// Database Helper Class
class TaskDatabaseHelper(context: Context) : SQLiteOpenHelper(context, "tasks.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY, title TEXT, description TEXT, status TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS tasks")
        onCreate(db)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                TaskManagerScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskManagerScreen() {
    val context = LocalContext.current
    val dbHelper = remember { TaskDatabaseHelper(context) }
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var showDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }

    // Initially load all tasks
    var tasks by remember { mutableStateOf(getTasks(dbHelper)) }

    // Filter tasks based on search query
    val filteredTasks = tasks.filter { task ->
        task.title.contains(searchQuery.text, ignoreCase = true) || task.description.contains(searchQuery.text, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Notes App") },
                actions = {

                    // Search field
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search Notes") },
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add New Note")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // LazyColumn to display the task list and make it scrollable
            LazyColumn(
                modifier = Modifier
                    .weight(1f) // This ensures the list takes up available space
                    .fillMaxSize()
            ) {
                items(filteredTasks) { task ->
                    TaskCard(task, dbHelper, onUpdate = { tasks = getTasks(dbHelper) }, onEditTask = {
                        taskToEdit = it
                        showEditDialog = true
                    })
                }
            }

            if (showDialog) {
                AddTaskDialog(dbHelper, onDismiss = {
                    showDialog = false
                    tasks = getTasks(dbHelper)
                })
            }
            if (showEditDialog && taskToEdit != null) {
                EditTaskDialog(
                    dbHelper,
                    taskToEdit = taskToEdit!!,
                    onDismiss = {
                        showEditDialog = false
                        taskToEdit = null
                        tasks = getTasks(dbHelper)
                    }
                )
            }
        }
    }
}

@Composable
fun TaskCard(task: Task, dbHelper: TaskDatabaseHelper, onUpdate: () -> Unit, onEditTask: (Task) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = task.title, style = MaterialTheme.typography.titleLarge)
            Text(text = task.description, style = MaterialTheme.typography.bodyMedium)

            // Conditionally show status only if it is "Important"
            if (task.status == "Important") {
                Text(text = "Status: ${task.status}", style = MaterialTheme.typography.labelSmall)
            }

            Row {
                Button(onClick = {
                    updateTaskStatus(dbHelper, task.id, "Important")
                    onUpdate()
                }) {
                    Text("Mark as Important")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    deleteTask(dbHelper, task.id)
                    onUpdate()
                }) {
                    Text("Delete Note")
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Edit button
                IconButton(onClick = { onEditTask(task) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Task")
                }
            }
        }
    }
}

@Composable
fun AddTaskDialog(dbHelper: TaskDatabaseHelper, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Note") },
        text = {
            Column {
                TextField(value = title, onValueChange = { title = it }, placeholder = { Text("Title") })
                TextField(value = description, onValueChange = { description = it }, placeholder = { Text("Description") })
            }
        },
        confirmButton = {
            Button(onClick = {
                addTask(dbHelper, title, description)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditTaskDialog(dbHelper: TaskDatabaseHelper, taskToEdit: Task, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(taskToEdit.title) }
    var description by remember { mutableStateOf(taskToEdit.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Note") },
        text = {
            Column {
                TextField(value = title, onValueChange = { title = it }, placeholder = { Text("Title") })
                TextField(value = description, onValueChange = { description = it }, placeholder = { Text("Description") })
            }
        },
        confirmButton = {
            Button(onClick = {
                updateTask(dbHelper, taskToEdit.id, title, description)
                onDismiss()
            }) { Text("Save Changes") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// Data Model
data class Task(val id: Int, val title: String, val description: String, val status: String)

// Database Operations
fun getTasks(dbHelper: TaskDatabaseHelper): List<Task> {
    val db = dbHelper.readableDatabase
    val cursor = db.rawQuery("SELECT * FROM tasks", null)
    val tasks = mutableListOf<Task>()
    while (cursor.moveToNext()) {
        tasks.add(
            Task(
                cursor.getInt(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3)
            )
        )
    }
    cursor.close()
    return tasks
}

fun addTask(dbHelper: TaskDatabaseHelper, title: String, description: String) {
    val db = dbHelper.writableDatabase
    val values = ContentValues().apply {
        put("title", title)
        put("description", description)
        put("status", "pending")
    }
    db.insert("tasks", null, values)
}

fun updateTask(dbHelper: TaskDatabaseHelper, taskId: Int, title: String, description: String) {
    val db = dbHelper.writableDatabase
    val values = ContentValues().apply {
        put("title", title)
        put("description", description)
    }
    db.update("tasks", values, "id = ?", arrayOf(taskId.toString()))
}

fun updateTaskStatus(dbHelper: TaskDatabaseHelper, taskId: Int, status: String) {
    val db = dbHelper.writableDatabase
    val values = ContentValues().apply { put("status", status) }
    db.update("tasks", values, "id = ?", arrayOf(taskId.toString()))
}

fun deleteTask(dbHelper: TaskDatabaseHelper, taskId: Int) {
    val db = dbHelper.writableDatabase
    db.delete("tasks", "id = ?", arrayOf(taskId.toString()))
}

@Preview(showBackground = true)
@Composable
fun TaskManagerPreview() {
    MyApplicationTheme {
        TaskManagerScreen()
    }
}

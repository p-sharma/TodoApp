package com.example.todoapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.todoapp.data.TodoTask
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(viewModel: TodoViewModel = hiltViewModel()) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val isAtLimit by viewModel.isAtLimit.collectAsStateWithLifecycle()
    val dailyLimit by viewModel.dailyLimit.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var editingTaskId by remember { mutableStateOf<Long?>(null) }

    val lazyListState = rememberLazyListState()
    var displayedTasks by remember { mutableStateOf(emptyList<TodoTask>()) }
    var draggingItemKey by remember { mutableStateOf<Long?>(null) }
    var draggingItemOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(tasks) {
        if (draggingItemKey == null) displayedTasks = tasks
    }

    BackHandler(enabled = editingTaskId != null) {
        editingTaskId = null
    }

    fun submitTask() {
        viewModel.addTask(inputText)
        inputText = ""
    }

    if (showSettings) {
        LimitSettingsDialog(
            currentLimit = dailyLimit,
            onDismiss = { showSettings = false },
            onConfirm = { newLimit ->
                viewModel.setDailyLimit(newLimit)
                showSettings = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        bottomBar = {
            InputBar(
                text = inputText,
                onTextChange = { if (it.length <= MAX_TASK_LENGTH) inputText = it },
                onAdd = ::submitTask,
                isAtLimit = isAtLimit,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
            )
        }
    ) { innerPadding ->
        if (displayedTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "What do you want to focus on today?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(displayedTasks, key = { it.id }) { task ->
                    val isDraggingThis = task.id == draggingItemKey
                    val isDraggingAny = draggingItemKey != null

                    val dismissState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            if (task.id == editingTaskId) editingTaskId = null
                            viewModel.deleteTask(task)
                        }
                    }

                    val dragHandleModifier = Modifier.pointerInput(task.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { _ ->
                                draggingItemKey = task.id
                                draggingItemOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggingItemOffsetY += dragAmount.y

                                val currentIndex = displayedTasks.indexOfFirst { it.id == draggingItemKey }
                                if (currentIndex < 0) return@detectDragGesturesAfterLongPress

                                val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
                                val draggingItemLayout = visibleItems.firstOrNull { it.key == draggingItemKey }
                                    ?: return@detectDragGesturesAfterLongPress

                                val draggingCenter = draggingItemLayout.offset +
                                    draggingItemLayout.size / 2 +
                                    draggingItemOffsetY.toInt()

                                val targetItemLayout = visibleItems.firstOrNull { item ->
                                    item.key != draggingItemKey &&
                                        draggingCenter >= item.offset &&
                                        draggingCenter < item.offset + item.size
                                } ?: return@detectDragGesturesAfterLongPress

                                val targetIndex = displayedTasks.indexOfFirst {
                                    it.id == targetItemLayout.key as? Long
                                }
                                if (targetIndex < 0) return@detectDragGesturesAfterLongPress

                                val draggingTask = displayedTasks[currentIndex]
                                val targetTask = displayedTasks[targetIndex]
                                if (draggingTask.isCompleted != targetTask.isCompleted) return@detectDragGesturesAfterLongPress

                                val fromOffset = draggingItemLayout.offset
                                val toOffset = targetItemLayout.offset

                                val mutable = displayedTasks.toMutableList()
                                mutable.add(targetIndex, mutable.removeAt(currentIndex))
                                displayedTasks = mutable
                                draggingItemOffsetY -= (toOffset - fromOffset).toFloat()
                            },
                            onDragEnd = {
                                viewModel.reorderTasks(displayedTasks)
                                draggingItemKey = null
                                draggingItemOffsetY = 0f
                            },
                            onDragCancel = {
                                displayedTasks = tasks
                                draggingItemKey = null
                                draggingItemOffsetY = 0f
                            }
                        )
                    }

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = { DeleteBackground(dismissState.targetValue) },
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = !isDraggingAny,
                        modifier = Modifier
                            .zIndex(if (isDraggingThis) 1f else 0f)
                            .offset {
                                IntOffset(0, if (isDraggingThis) draggingItemOffsetY.roundToInt() else 0)
                            }
                    ) {
                        TaskRow(
                            task = task,
                            isEditing = task.id == editingTaskId,
                            isDragging = isDraggingThis,
                            onToggle = { viewModel.toggleTask(task) },
                            onStartEdit = { editingTaskId = task.id },
                            onConfirmEdit = { newText ->
                                viewModel.updateTaskText(task, newText)
                                editingTaskId = null
                            },
                            onCancelEdit = { editingTaskId = null },
                            dragHandleModifier = dragHandleModifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TodoTask,
    isEditing: Boolean,
    isDragging: Boolean,
    onToggle: () -> Unit,
    onStartEdit: () -> Unit,
    onConfirmEdit: (String) -> Unit,
    onCancelEdit: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
) {
    var editText by remember(task.id) { mutableStateOf(task.text) }
    val focusRequester = remember { FocusRequester() }
    val updatedIsEditing by rememberUpdatedState(isEditing)

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        } else {
            editText = task.text
        }
    }

    fun confirm() {
        val trimmed = editText.trim()
        if (trimmed.isEmpty()) onCancelEdit() else onConfirmEdit(trimmed)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
            )
            .alpha(if (task.isCompleted) 0.5f else 1f)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.isCompleted,
            onCheckedChange = { onToggle() }
        )

        if (isEditing) {
            val textStyle = MaterialTheme.typography.bodyLarge.merge(
                TextStyle(
                    color = LocalContentColor.current,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
            )
            BasicTextField(
                value = editText,
                onValueChange = { if (it.length <= MAX_TASK_LENGTH) editText = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (!focusState.hasFocus && updatedIsEditing) confirm()
                    },
                textStyle = textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                decorationBox = { innerTextField ->
                    Column {
                        innerTextField()
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        } else {
            Text(
                text = task.text,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .clickable(onClick = onStartEdit),
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
            )
        }

        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "Reorder",
            modifier = dragHandleModifier.padding(horizontal = 8.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeleteBackground(targetValue: SwipeToDismissBoxValue) {
    val triggered = targetValue == SwipeToDismissBoxValue.EndToStart
    val color by animateColorAsState(
        targetValue = if (triggered) MaterialTheme.colorScheme.error else Color.Transparent,
        label = "delete_bg"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(end = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (triggered) {
            Text(
                text = "Delete",
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit,
    isAtLimit: Boolean,
    modifier: Modifier = Modifier
) {
    val showCharCount = text.length >= MAX_TASK_LENGTH - CHAR_COUNT_WARNING_THRESHOLD
    val canAdd = text.isNotBlank() && !isAtLimit

    Surface(
        modifier = modifier,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (isAtLimit) {
                Text(
                    text = "You've reached your task limit for today",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Start)
                )
            } else if (showCharCount) {
                Text(
                    text = "${text.length}/$MAX_TASK_LENGTH",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (text.length == MAX_TASK_LENGTH)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add a task") },
                    singleLine = true,
                    enabled = !isAtLimit,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canAdd) onAdd() })
                )
                Button(
                    onClick = onAdd,
                    enabled = canAdd
                ) {
                    Text("Add")
                }
            }
        }
    }
}

@Composable
private fun LimitSettingsDialog(
    currentLimit: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var input by remember(currentLimit) { mutableStateOf(currentLimit.toString()) }
    val parsed = input.toIntOrNull()
    val isValid = parsed != null && parsed >= 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily task limit") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { new -> input = new.filter { it.isDigit() } },
                label = { Text("Max tasks per day") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { if (isValid) parsed?.let(onConfirm) }),
                singleLine = true,
                isError = !isValid
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = isValid) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

package es.zombielawng.nom.homeandi.ui.chat

import androidx.compose.ui.graphics.toArgb

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.zombielawng.nom.homeandi.ui.components.ChatBubble
import es.zombielawng.nom.homeandi.ui.components.SessionDrawerContent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.net.Uri
import androidx.compose.ui.draw.clip
import java.io.File
import java.util.UUID
import coil.compose.AsyncImage
import es.zombielawng.nom.homeandi.ui.components.AttachmentViewer
import es.zombielawng.nom.homeandi.data.remote.ModelData
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var inputTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var showAttachmentDialog by remember { mutableStateOf(false) }
    var showNewSessionModelDialog by remember { mutableStateOf(false) }
    var showSwitchModelDialog by remember { mutableStateOf(false) }

    // Scroll direction tracking
    var previousIndex by remember { mutableStateOf(listState.firstVisibleItemIndex) }
    var previousScrollOffset by remember { mutableStateOf(listState.firstVisibleItemScrollOffset) }
    
    // Launchers for attachments
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let { viewModel.addAttachment(it.toString()) } }
    )

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> uri?.let { viewModel.addAttachment(it.toString()) } }
    )

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                cameraImageUri?.let { viewModel.addAttachment(it.toString()) }
            }
        }
    )

    fun launchCamera() {
        val file = File(context.getExternalFilesDir(null), "Images")
        if (!file.exists()) file.mkdirs()
        val imageFile = File(file, "camera_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
        cameraImageUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                launchCamera()
            }
        }
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* No-op */ }
    )

    fun checkAndLaunchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Notification permission request for Android 13+
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val view = androidx.compose.ui.platform.LocalView.current
    val currentThemeBackground = MaterialTheme.colorScheme.background
    LaunchedEffect(state.messages.size, state.isGenerating) {
        if (state.isScreenshotsEnabled && state.messages.isNotEmpty() && !state.isGenerating) {
            kotlinx.coroutines.delay(800)
            if (view.width > 0 && view.height > 0) {
                try {
                    val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(currentThemeBackground.toArgb())
                    view.draw(canvas)
                    val thumbnail = android.graphics.Bitmap.createScaledBitmap(bitmap, 90, 120, true)
                    val file = java.io.File(context.filesDir, "screenshot_${state.activeSession?.id}.png")
                    java.io.FileOutputStream(file).use { out ->
                        thumbnail.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
                    }
                    state.activeSession?.id?.let { sessionId ->
                        viewModel.updateSessionScreenshot(sessionId, file.absolutePath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Scroll to bottom when messages update or generation starts
    LaunchedEffect(state.messages.size, state.isGenerating) {
        val totalItems = state.messages.size + (if (state.isGenerating) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    // Dismiss keyboard only on upward scroll (dragging finger down)
    LaunchedEffect(listState) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { (currentIndex, currentOffset) ->
                val isScrollingUpwards = if (currentIndex < previousIndex) {
                    true
                } else if (currentIndex == previousIndex) {
                    currentOffset < previousScrollOffset
                } else {
                    false
                }

                if (isScrollingUpwards && listState.isScrollInProgress) {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }

                previousIndex = currentIndex
                previousScrollOffset = currentOffset
            }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawerContent(
                sessions = state.sessions,
                activeSessionId = state.activeSession?.id,
                isScreenshotsEnabled = state.isScreenshotsEnabled,
                onSelectSession = { id ->
                    viewModel.selectSession(id)
                    scope.launch { drawerState.close() }
                },
                onNewSession = {
                    viewModel.createNewSession()
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = { id ->
                    viewModel.deleteSession(id)
                },
                onRenameSession = { id, title ->
                    viewModel.renameSession(id, title)
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                var isEditingName by remember { mutableStateOf(false) }
                var editedName by remember(state.activeSession?.title) {
                    mutableStateOf(state.activeSession?.title ?: "")
                }

                TopAppBar(
                    title = {
                        Column {
                            if (isEditingName) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                                ) {
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = editedName,
                                        onValueChange = { editedName = it },
                                        textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { 
                                            state.activeSession?.id?.let { sessionId ->
                                                if (editedName.isNotBlank()) {
                                                    viewModel.renameSession(sessionId, editedName)
                                                }
                                            }
                                            isEditingName = false
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Save Title",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { 
                                            editedName = state.activeSession?.title ?: ""
                                            isEditingName = false 
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Cancel Editing",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = state.activeSession?.title ?: "HomeAndI",
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { isEditingName = true },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "Rename Chat",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.clickable { 
                                    viewModel.fetchLoadedModels()
                                    showSwitchModelDialog = true 
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Model: ${state.activeSession?.modelName ?: "Default"}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Rounded.SwapHoriz,
                                    contentDescription = "Switch Model",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Rounded.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            viewModel.fetchLoadedModels()
                            showNewSessionModelDialog = true 
                        }) {
                            Icon(imageVector = Icons.Rounded.Add, contentDescription = "New Session")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(imageVector = Icons.Rounded.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
                    .imePadding()
            ) {
                // Error Alert Banner
                state.errorMessage?.let { errorText ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = errorText,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearError() }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss Error",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // Chat Messages List
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            ChatBubble(
                                message = message,
                                onAttachmentClick = { viewModel.openAttachment(it) }
                            )
                        }

                        if (state.isAnySessionGenerating) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = if (state.activeSession?.id == state.generatingSessionId) {
                                            val modelName = getCleanModelName(state.activeSession?.modelName)
                                            if (state.isWebSearchActive && state.isWebSearchEnabled) {
                                                "$modelName + online search is thinking..."
                                            } else {
                                                "$modelName is thinking..."
                                            }
                                        } else {
                                            "Another conversation is processing. Please wait until it has finished or manually stop it."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Top linear progress indicator for better visibility
                    if (state.isAnySessionGenerating) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .align(Alignment.TopCenter),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                }

                // Pending Attachment Thumbnails
                if (state.pendingAttachments.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.pendingAttachments) { path ->
                            Box {
                                AsyncImage(
                                    model = path,
                                    contentDescription = "Pending Attachment",
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.openAttachment(path) },
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { viewModel.removeAttachment(path) },
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.TopEnd)
                                        .background(MaterialTheme.colorScheme.error, RoundedCornerShape(10.dp))
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Input Bar Area
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Attachment Button
                        IconButton(onClick = { showAttachmentDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = "Add Multimodal Attachment",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Web Search Toggle
                        if (state.isWebSearchEnabled) {
                            IconButton(onClick = { viewModel.toggleWebSearch() }) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = "Google Grounded Search",
                                    tint = if (state.isWebSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // Text Prompt Input
                        OutlinedTextField(
                            value = inputTextFieldValue,
                            onValueChange = { inputTextFieldValue = it },
                            placeholder = { Text("How can HomeAndI help?") },
                            singleLine = false,
                            modifier = Modifier
                                .weight(1f)
                                .onPreviewKeyEvent { keyEvent ->
                                    val native = keyEvent.nativeKeyEvent
                                    val keyCode = native.keyCode
                                    // 66 = Enter, 160 = NumPad Enter, 10 = LineFeed
                                    val isEnter = (keyCode == 66 || keyCode == 160 || keyCode == 10)
                                    
                                    if (isEnter) {
                                        // Simulator-friendly modifier check (metaState bits + high level helpers)
                                        val hasModifier = native.isShiftPressed || native.isCtrlPressed || 
                                                         native.isAltPressed || native.isMetaPressed ||
                                                         (native.metaState and 0x1 != 0) || // Shift
                                                         (native.metaState and 0x1000 != 0)    // Ctrl
                                        
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            if (hasModifier) {
                                                // Manual newline insertion at cursor
                                                val text = inputTextFieldValue.text
                                                val selection = inputTextFieldValue.selection
                                                val before = text.substring(0, selection.start)
                                                val after = text.substring(selection.end)
                                                inputTextFieldValue = inputTextFieldValue.copy(
                                                    text = "$before\n$after",
                                                    selection = TextRange(selection.start + 1)
                                                )
                                            } else {
                                                // Plain Enter -> Send
                                                if (!state.isAnySessionGenerating && (inputTextFieldValue.text.isNotBlank() || state.pendingAttachments.isNotEmpty())) {
                                                    viewModel.sendMessage(inputTextFieldValue.text)
                                                    inputTextFieldValue = TextFieldValue("")
                                                }
                                            }
                                        }
                                        // Consume both Down and Up events for Enter to prevent default behavior
                                        return@onPreviewKeyEvent true
                                    }
                                    false
                                },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.None,
                                keyboardType = KeyboardType.Text
                            ),
                            maxLines = 4,
                            shape = MaterialTheme.shapes.medium
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Send or Stop Button
                        if (state.isAnySessionGenerating) {
                            IconButton(
                                onClick = {
                                    state.activeSession?.id?.let { viewModel.stopGeneration(it) }
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = "Stop Generating"
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    viewModel.sendMessage(inputTextFieldValue.text)
                                    inputTextFieldValue = TextFieldValue("")
                                },
                                enabled = (inputTextFieldValue.text.isNotBlank() || state.pendingAttachments.isNotEmpty()),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Send,
                                    contentDescription = "Send Message"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Attachment Options Dialog
    if (showAttachmentDialog) {
        AlertDialog(
            onDismissRequest = { showAttachmentDialog = false },
            title = { Text("Add Attachment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose how you want to add an attachment:")
                    
                    Button(
                        onClick = {
                            checkAndLaunchCamera()
                            showAttachmentDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Take Photo")
                    }

                    Button(
                        onClick = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            showAttachmentDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Rounded.Collections, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Select from Gallery")
                    }

                    Button(
                        onClick = {
                            filePickerLauncher.launch("*/*")
                            showAttachmentDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Rounded.FilePresent, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pick a File")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAttachmentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Attachment Viewer Overlay
    state.viewingAttachment?.let { path ->
        AttachmentViewer(
            path = path,
            onDismiss = { viewModel.closeAttachment() }
        )
    }

    // New Session Model Selection Dialog
    if (showNewSessionModelDialog) {
        ModelSelectionDialog(
            title = "Start New Chat",
            availableModels = state.availableModels,
            onModelSelected = { modelId ->
                viewModel.createNewSession(modelId = modelId)
                showNewSessionModelDialog = false
            },
            onLoadModel = { viewModel.loadModel(it) },
            onUnloadModel = { viewModel.unloadModel(it) },
            isModelActionLoading = state.isModelActionLoading,
            onDismiss = { showNewSessionModelDialog = false }
        )
    }

    // Mid-Conversation Model Switch Dialog
    if (showSwitchModelDialog) {
        ModelSelectionDialog(
            title = "Switch Model for this Chat",
            availableModels = state.availableModels,
            onModelSelected = { modelId ->
                state.activeSession?.id?.let { sessionId ->
                    viewModel.updateSessionModel(sessionId, modelId)
                }
                showSwitchModelDialog = false
            },
            onLoadModel = { viewModel.loadModel(it) },
            onUnloadModel = { viewModel.unloadModel(it) },
            isModelActionLoading = state.isModelActionLoading,
            onDismiss = { showSwitchModelDialog = false }
        )
    }
}

@Composable
fun ModelSelectionDialog(
    title: String,
    availableModels: List<ModelData>,
    onModelSelected: (String) -> Unit,
    onLoadModel: (String) -> Unit,
    onUnloadModel: (String) -> Unit,
    isModelActionLoading: Boolean,
    onDismiss: () -> Unit
) {
    val loaded = availableModels.filter { it.isLoaded }
    val unloaded = availableModels.filter { !it.isLoaded }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isModelActionLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (loaded.isNotEmpty()) {
                        item {
                            Text(
                                text = "Loaded Models (Active)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(loaded, key = { "loaded_${it.id}" }) { model ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Card(
                                    onClick = { onModelSelected(model.id) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isModelActionLoading,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(
                                            model.displayName ?: model.id,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                        Text(
                                            model.id,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                val instanceId = model.loadedInstances?.firstOrNull()?.id ?: model.id
                                IconButton(
                                    onClick = { onUnloadModel(instanceId) },
                                    enabled = !isModelActionLoading,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Eject,
                                        contentDescription = "Unload Model"
                                    )
                                }
                            }
                        }
                    }

                    if (unloaded.isNotEmpty()) {
                        item {
                            Text(
                                text = "Unloaded Models (Available)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(unloaded, key = { "unloaded_${it.id}" }) { model ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Card(
                                    onClick = { /* Unloaded models cannot be selected directly until loaded */ },
                                    modifier = Modifier.weight(1f),
                                    enabled = false,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(
                                            model.displayName ?: model.id,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            model.id,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { onLoadModel(model.id) },
                                    enabled = !isModelActionLoading,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Load Model"
                                    )
                                }
                            }
                        }
                    }

                    if (loaded.isEmpty() && unloaded.isEmpty()) {
                        item {
                            Text(
                                text = "No local models found on the server.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun getCleanModelName(modelId: String?): String {
    if (modelId.isNullOrBlank()) return "AI"
    val name = modelId.substringAfterLast('/')
    val cleaned = name.replace("-4bit", "", ignoreCase = true)
                      .replace("-instruct", "", ignoreCase = true)
                      .replace("-it", "", ignoreCase = true)
                      .replace("-preview", "", ignoreCase = true)
                      .replace("-chat", "", ignoreCase = true)
    
    val parts = cleaned.split('-').filter { it.isNotBlank() }
    if (parts.isEmpty()) return "AI"
    
    if (parts.size >= 2 && parts[0].equals("meta", ignoreCase = true) && parts[1].equals("llama", ignoreCase = true)) {
        return "Llama"
    }
    
    return parts[0].replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}


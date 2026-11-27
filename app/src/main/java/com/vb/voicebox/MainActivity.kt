
package com.vb.voicebox

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                VoiceboxApp()
            }
        }
    }
}

fun createBackup(
    prefs: android.content.SharedPreferences,
    groups: List<String>
): String {
    val backup = JSONObject()
    val groupArray = JSONArray()
    val messagesObject = JSONObject()

    groups.forEach { group ->
        groupArray.put(group)

        val savedMessages = prefs.getString(group, null)

        if (savedMessages != null) {
            try {
                messagesObject.put(
                    group,
                    JSONArray(savedMessages)
                )
            } catch (_: Exception) {
                messagesObject.put(group, JSONArray())
            }
        }
    }

    backup.put("app", "VB")
    backup.put("version", 1)
    backup.put("groups", groupArray)
    backup.put("messages", messagesObject)

    return backup.toString(2)
}

fun restoreBackup(
    prefs: android.content.SharedPreferences,
    groups: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    backupText: String
): Boolean {
    return try {
        val backup = JSONObject(backupText)
        val groupArray = backup.optJSONArray("groups")
            ?: return false

        val messagesObject = backup.optJSONObject("messages")
            ?: JSONObject()

        val restoredGroups = mutableListOf<String>()

        for (i in 0 until groupArray.length()) {
            val name = groupArray.getString(i).trim()

            if (
                name.isNotEmpty() &&
                restoredGroups.none {
                    it.equals(name, ignoreCase = true)
                }
            ) {
                restoredGroups.add(name)
            }
        }

        if (restoredGroups.isEmpty()) return false

        val editor = prefs.edit()

        val savedGroups = JSONArray()
        restoredGroups.forEach {
            savedGroups.put(it)
        }

        editor.putString("group_list", savedGroups.toString())

        restoredGroups.forEach { group ->
            val messageArray =
                messagesObject.optJSONArray(group)

            if (messageArray != null) {
                editor.putString(
                    group,
                    messageArray.toString()
                )
            }
        }

        editor.apply()

        groups.clear()
        groups.addAll(restoredGroups)

        true
    } catch (_: Exception) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceboxApp() {
    val context = LocalContext.current

    val prefs = remember(context) {
        context.getSharedPreferences(
            "vb_chat_messages",
            Context.MODE_PRIVATE
        )
    }

    val groups = remember(context) {
        mutableStateListOf<String>().apply {
            val saved = prefs.getString("group_list", null)

            if (saved == null) {
                addAll(
                    listOf(
                        "Family Group",
                        "Friends Group",
                        "VB Community"
                    )
                )

                val array = JSONArray()
                forEach { array.put(it) }

                prefs.edit()
                    .putString("group_list", array.toString())
                    .apply()
            } else {
                try {
                    val array = JSONArray(saved)

                    for (i in 0 until array.length()) {
                        add(array.getString(i))
                    }
                } catch (_: Exception) {
                    addAll(
                        listOf(
                            "Family Group",
                            "Friends Group",
                            "VB Community"
                        )
                    )
                }
            }
        }
    }

    fun saveGroups() {
        val array = JSONArray()
        groups.forEach { array.put(it) }

        prefs.edit()
            .putString("group_list", array.toString())
            .apply()
    }

    fun addGroup(name: String) {
        val cleanName = name.trim()

        if (
            cleanName.isNotEmpty() &&
            groups.none {
                it.equals(cleanName, ignoreCase = true)
            }
        ) {
            groups.add(cleanName)
            saveGroups()
        }
    }

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    var openedGroup by remember {
        mutableStateOf<String?>(null)
    }

    var backupStatus by remember {
        mutableStateOf("")
    }

    var pendingBackup by remember {
        mutableStateOf("")
    }

    val groupSnapshot = groups.toList()

    val backupLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(
                "application/json"
            )
        ) { uri: Uri? ->

            if (uri != null) {
                backupStatus = try {
                    context.contentResolver
                        .openOutputStream(uri)
                        ?.bufferedWriter()
                        ?.use {
                            it.write(pendingBackup)
                        }
                        ?: throw Exception("Could not save file")

                    "Backup saved successfully!"
                } catch (e: Exception) {
                    "Backup failed: ${e.message}"
                }
            }
        }

    val restoreLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->

            if (uri != null) {
                backupStatus = try {
                    val backupText =
                        context.contentResolver
                            .openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?: throw Exception("Could not open file")

                    if (
                        restoreBackup(
                            prefs,
                            groups,
                            backupText
                        )
                    ) {
                        openedGroup = null
                        selectedTab = 1
                        "Backup restored successfully!"
                    } else {
                        "Invalid or empty VB backup file."
                    }
                } catch (e: Exception) {
                    "Restore failed: ${e.message}"
                }
            }
        }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("VB - Voicebox")
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val tabs = listOf(
                    "Chats",
                    "Groups",
                    "Calls",
                    "Status",
                    "Profile"
                )

                val icons = listOf(
                    Icons.Filled.Chat,
                    Icons.Filled.Groups,
                    Icons.Filled.Call,
                    Icons.Filled.CameraAlt,
                    Icons.Filled.Person
                )

                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            openedGroup = null
                        },
                        icon = {
                            Icon(
                                icons[index],
                                contentDescription = tab
                            )
                        },
                        label = {
                            Text(tab)
                        }
                    )
                }
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (openedGroup != null) {
                GroupChatScreen(
                    groupName = openedGroup!!,
                    onBack = {
                        openedGroup = null
                    }
                )
            } else {
                when (selectedTab) {
                    0 -> ChatsScreen(
                        groups = groupSnapshot,
                        onGroupClick = {
                            openedGroup = it
                        }
                    )

                    1 -> GroupsScreen(
                        groups = groupSnapshot,
                        onCreate = {
                            addGroup(it)
                        },
                        onJoin = {
                            addGroup(it)
                        },
                        onGroupClick = {
                            openedGroup = it
                        }
                    )

                    2 -> SimpleScreen("Voice and video calls")

                    3 -> SimpleScreen("Personal and group status")

                    4 -> ProfileScreen(
                        backupStatus = backupStatus,
                        onBackup = {
                            pendingBackup = createBackup(
                                prefs,
                                groups.toList()
                            )
                            backupLauncher.launch("VB_Backup.json")
                        },
                        onRestore = {
                            restoreLauncher.launch(
                                arrayOf(
                                    "application/json",
                                    "text/*",
                                    "*/*"
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    backupStatus: String,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    Column {
        Text(
            "Your VB profile",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Backup and Restore",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onBackup,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save VB Backup")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Restore VB Backup")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (backupStatus.isNotEmpty()) {
            Text(
                backupStatus,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Keep your backup file private. It contains your saved group names and messages."
        )
    }
}

@Composable
fun ChatsScreen(
    groups: List<String>,
    onGroupClick: (String) -> Unit
) {
    Column {
        Text(
            "Welcome to VB",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text("Your voice. Your groups. Your world.")

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Recent conversations",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        groups.forEach { group ->
            GroupCard(
                name = group,
                message = "Group conversation"
            ) {
                onGroupClick(group)
            }
        }
    }
}

@Composable
fun GroupsScreen(
    groups: List<String>,
    onCreate: (String) -> Unit,
    onJoin: (String) -> Unit,
    onGroupClick: (String) -> Unit
) {
    var showCreate by remember {
        mutableStateOf(false)
    }

    var showJoin by remember {
        mutableStateOf(false)
    }

    var groupName by remember {
        mutableStateOf("")
    }

    Column {
        Text(
            "Your Groups",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                groupName = ""
                showCreate = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Create New Group")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                groupName = ""
                showJoin = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Join Group")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Your group conversations",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        groups.forEach { group ->
            GroupCard(
                name = group,
                message = "Open group conversation"
            ) {
                onGroupClick(group)
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = {
                showCreate = false
            },
            title = {
                Text("Create New Group")
            },
            text = {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = {
                        groupName = it
                    },
                    label = {
                        Text("Group Name")
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            onCreate(groupName)
                            showCreate = false
                            groupName = ""
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreate = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showJoin) {
        AlertDialog(
            onDismissRequest = {
                showJoin = false
            },
            title = {
                Text("Join Group")
            },
            text = {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = {
                        groupName = it
                    },
                    label = {
                        Text("Enter Group Name")
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            onJoin(groupName)
                            showJoin = false
                            groupName = ""
                        }
                    }
                ) {
                    Text("Join")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showJoin = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun GroupCard(
    name: String,
    message: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable {
                onClick()
            },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(message)

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "Tap to open conversation",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun GroupChatScreen(
    groupName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val prefs = remember(context) {
        context.getSharedPreferences(
            "vb_chat_messages",
            Context.MODE_PRIVATE
        )
    }

    val messages = remember(groupName) {
        mutableStateListOf<String>().apply {
            val saved = prefs.getString(groupName, null)

            if (saved != null) {
                try {
                    val array = JSONArray(saved)

                    for (i in 0 until array.length()) {
                        add(array.getString(i))
                    }
                } catch (_: Exception) {
                    add("Welcome to $groupName!")
                }
            } else {
                add("Welcome to $groupName!")
            }
        }
    }

    var messageText by remember {
        mutableStateOf("")
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TextButton(
            onClick = onBack
        ) {
            Text("← Back to groups")
        }

        Text(
            groupName,
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            "Group conversation",
            style = MaterialTheme.typography.bodySmall
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = {
                    messageText = it
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Write a message...")
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (messageText.isNotBlank()) {
                        messages.add(
                            "You: ${messageText.trim()}"
                        )

                        val array = JSONArray()
                        messages.forEach {
                            array.put(it)
                        }

                        prefs.edit()
                            .putString(
                                groupName,
                                array.toString()
                            )
                            .apply()

                        messageText = ""
                    }
                }
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
fun SimpleScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

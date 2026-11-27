
package com.vb.voicebox

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceboxApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var openedGroup by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("VB - Voicebox") }
            )
        },
        bottomBar = {
            NavigationBar {
                val tabs = listOf(
                    "Chats", "Groups", "Calls", "Status", "Profile"
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
                            Icon(icons[index], contentDescription = tab)
                        },
                        label = { Text(tab) }
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
                    onBack = { openedGroup = null }
                )
            } else {
                when (selectedTab) {
                    0 -> ChatsScreen { openedGroup = it }
                    1 -> GroupsScreen { openedGroup = it }
                    2 -> SimpleScreen("Voice and video calls")
                    3 -> SimpleScreen("Personal and group status")
                    4 -> SimpleScreen("Your VB profile")
                }
            }
        }
    }
}

@Composable
fun ChatsScreen(onGroupClick: (String) -> Unit) {
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

        GroupCard("Family Group", "Welcome to Voicebox!") {
            onGroupClick("Family Group")
        }

        GroupCard("Friends Group", "Let's talk together.") {
            onGroupClick("Friends Group")
        }

        GroupCard("VB Community", "Share your voice.") {
            onGroupClick("VB Community")
        }
    }
}

@Composable
fun GroupsScreen(onGroupClick: (String) -> Unit) {
    Column {
        Text(
            "Your Groups",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        GroupCard("Family Group", "Family conversations") {
            onGroupClick("Family Group")
        }

        GroupCard("Friends Group", "Friends and sharing") {
            onGroupClick("Friends Group")
        }

        GroupCard("VB Community", "Community discussions") {
            onGroupClick("VB Community")
        }
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
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
                    val jsonArray = JSONArray(saved)

                    for (i in 0 until jsonArray.length()) {
                        add(jsonArray.getString(i))
                    }
                } catch (_: Exception) {
                    add("Welcome to $groupName!")
                }
            } else {
                add("Welcome to $groupName!")
            }
        }
    }

    var messageText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {

        TextButton(onClick = onBack) {
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
                Card(modifier = Modifier.fillMaxWidth()) {
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
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Write a message...") },
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (messageText.isNotBlank()) {
                        messages.add("You: ${messageText.trim()}")

                        val jsonArray = JSONArray()
                        messages.forEach { jsonArray.put(it) }

                        prefs.edit()
                            .putString(groupName, jsonArray.toString())
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

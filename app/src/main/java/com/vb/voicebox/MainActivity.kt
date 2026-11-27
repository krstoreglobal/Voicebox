
package com.vb.voicebox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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

@Composable
fun VoiceboxApp() {
    var selectedTab by remember { mutableStateOf("Groups") }

    val tabs = listOf("Chats", "Groups", "Calls", "Status", "Profile")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VB – Voicebox") }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(tab.take(1)) },
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
            when (selectedTab) {
                "Groups" -> {
                    Text("Your Groups", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    listOf("Family Group", "Friends Group", "Work Group").forEach {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Text(it, modifier = Modifier.padding(20.dp))
                        }
                    }
                }
                "Chats" -> Text("Welcome to VB Chats")
                "Calls" -> Text("Group Voice & Video Calls")
                "Status" -> Text("Personal & Group Status")
                "Profile" -> Text("Your VB Profile")
            }
        }
    }
}


package com.vb.voicebox

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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

fun Context.findActivity(): Activity? {
    var current: Context? = this

    while (current != null) {
        if (current is Activity) return current
        current = (current as? ContextWrapper)?.baseContext
    }

    return null
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
                messagesObject.put(group, JSONArray(savedMessages))
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

        val messagesObject =
            backup.optJSONObject("messages") ?: JSONObject()

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

        restoredGroups.forEach { group ->
            savedGroups.put(group)

            val messageArray = messagesObject.optJSONArray(group)

            if (messageArray != null) {
                editor.putString(group, messageArray.toString())
            }
        }

        editor.putString("group_list", savedGroups.toString())
        editor.apply()

        groups.clear()
        groups.addAll(restoredGroups)

        true
    } catch (_: Exception) {
        false
    }
}

@Composable
fun VoiceboxApp() {
    val auth = remember { FirebaseAuth.getInstance() }

    var isSignedIn by remember {
        mutableStateOf(auth.currentUser != null)
    }

    if (!isSignedIn) {
        PhoneLoginScreen(
            onSignedIn = { isSignedIn = true }
        )
    } else {
        MainVBScreen()
    }
}

@Composable
fun PhoneLoginScreen(
    onSignedIn: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) {
        context.findActivity()
    }

    val auth = remember {
        FirebaseAuth.getInstance()
    }

    var phoneNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var otpSent by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val latestOnSignedIn by rememberUpdatedState(onSignedIn)

    val callbacks = remember(auth, activity) {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(
                credential: com.google.firebase.auth.PhoneAuthCredential
            ) {
                loading = true

                auth.signInWithCredential(credential)
                    .addOnCompleteListener { task ->
                        loading = false

                        if (task.isSuccessful) {
                            latestOnSignedIn()
                        } else {
                            errorMessage =
                                task.exception?.localizedMessage
                                    ?: "Phone sign-in failed."
                        }
                    }
            }

            override fun onVerificationFailed(
                e: com.google.firebase.FirebaseException
            ) {
                loading = false
                errorMessage =
                    e.localizedMessage ?: "Phone verification failed."
            }

            override fun onCodeSent(
                id: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                verificationId = id
                otpSent = true
                loading = false
                errorMessage = "Verification code sent. Check your SMS."
            }

            override fun onCodeAutoRetrievalTimeOut(id: String) {
                verificationId = id
                otpSent = true
                loading = false
            }
        }
    }

    fun sendCode() {
        val cleanNumber = phoneNumber.trim()

        if (
            !cleanNumber.startsWith("+") ||
            cleanNumber.length < 8 ||
            cleanNumber.length > 16
        ) {
            errorMessage =
                "Enter your full phone number with country code, such as +919876543210."
            return
        }

        if (activity == null) {
            errorMessage = "Unable to open phone verification."
            return
        }

        loading = true
        errorMessage = ""

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(cleanNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyCode() {
        val id = verificationId

        if (id == null) {
            errorMessage = "Please request a verification code first."
            return
        }

        if (otp.trim().length < 6) {
            errorMessage = "Enter the verification code."
            return
        }

        loading = true
        errorMessage = ""

        val credential =
            PhoneAuthProvider.getCredential(id, otp.trim())

        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                loading = false

                if (task.isSuccessful) {
                    latestOnSignedIn()
                } else {
                    errorMessage =
                        task.exception?.localizedMessage
                            ?: "Incorrect code. Please try again."
                }
            }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "VB",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                "Welcome to Voicebox",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Sign in with your phone number")

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = {
                    phoneNumber = it
                    errorMessage = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Phone number") },
                placeholder = { Text("+919876543210") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone
                ),
                singleLine = true,
                enabled = !loading && !otpSent
            )

            if (otpSent) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = otp,
                    onValueChange = {
                        otp = it.filter { character ->
                            character.isDigit()
                        }.take(6)
                        errorMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("SMS verification code") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true,
                    enabled = !loading
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!otpSent) {
                Button(
                    onClick = { sendCode() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) {
                    Text("Send verification code")
                }
            } else {
                Button(
                    onClick = { verifyCode() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) {
                    Text("Verify and sign in")
                }

                TextButton(
                    onClick = {
                        otp = ""
                        verificationId = null
                        otpSent = false
                        sendCode()
                    },
                    enabled = !loading
                ) {
                    Text("Change number / Send another code")
                }
            }

            if (loading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Use your full international phone number, including the + country code.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainVBScreen() {
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
                        val name = array.getString(i)

                        if (none { it.equals(name, true) }) {
                            add(name)
                        }
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

    fun addGroup(name: String): Boolean {
        val cleanName = name.trim()

        if (
            cleanName.isEmpty() ||
            groups.any { it.equals(cleanName, ignoreCase = true) }
        ) {
            return false
        }

        groups.add(cleanName)
        saveGroups()
        return true
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var openedGroup by remember { mutableStateOf<String?>(null) }
    var backupStatus by remember { mutableStateOf("") }
    var pendingBackup by remember { mutableStateOf("") }

    val backupLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri: Uri? ->
            if (uri != null) {
                backupStatus = try {
                    context.contentResolver
                        .openOutputStream(uri)
                        ?.bufferedWriter()
                        ?.use { it.write(pendingBackup) }
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

                    if (restoreBackup(prefs, groups, backupText)) {
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
                title = { Text("VB - Voicebox") }
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
                    0 -> ChatsScreen(
                        groups = groups,
                        onGroupClick = { openedGroup = it }
                    )

                    1 -> GroupsScreen(
                        groups = groups,
                        onCreate = { addGroup(it) },
                        onJoin = { addGroup(it) },
                        onGroupClick = { openedGroup = it }
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

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(groups, key = { it }) { group ->
                GroupCard(
                    name = group,
                    message = "Group conversation"
                ) {
                    onGroupClick(group)
                }
            }
        }
    }
}

@Composable
fun GroupsScreen(
    groups: List<String>,
    onCreate: (String) -> Boolean,
    onJoin: (String) -> Boolean,
    onGroupClick: (String) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Column {
        Text(
            "Your Groups",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                groupName = ""
                errorMessage = ""
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
                errorMessage = ""
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

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(groups, key = { it }) { group ->
                GroupCard(
                    name = group,
                    message = "Open group conversation"
                ) {
                    onGroupClick(group)
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create New Group") },
            text = {
                Column {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = {
                            groupName = it
                            errorMessage = ""
                        },
                        label = { Text("Group Name") },
                        singleLine = true
                    )

                    if (errorMessage.isNotEmpty()) {
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            if (onCreate(groupName)) {
                                showCreate = false
                                groupName = ""
                                errorMessage = ""
                            } else {
                                errorMessage =
                                    "This group already exists or the name is empty."
                            }
                        } else {
                            errorMessage = "Please enter a group name."
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showJoin) {
        AlertDialog(
            onDismissRequest = { showJoin = false },
            title = { Text("Join Group") },
            text = {
                Column {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = {
                            groupName = it
                            errorMessage = ""
                        },
                        label = { Text("Enter Group Name") },
                        singleLine = true
                    )

                    if (errorMessage.isNotEmpty()) {
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            if (onJoin(groupName)) {
                                showJoin = false
                                groupName = ""
                                errorMessage = ""
                            } else {
                                errorMessage =
                                    "This group already exists or the name is empty."
                            }
                        } else {
                            errorMessage = "Please enter a group name."
                        }
                    }
                ) {
                    Text("Join")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoin = false }) {
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
            .clickable { onClick() },
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

    var messageText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
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
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(12.dp)
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
                placeholder = {
                    Text("Write a message...")
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (messageText.isNotBlank()) {
                        messages.add("You: ${messageText.trim()}")

                        val array = JSONArray()
                        messages.forEach { array.put(it) }

                        prefs.edit()
                            .putString(groupName, array.toString())
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

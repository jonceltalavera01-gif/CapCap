package com.darkhorses.RideSafe.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

// Define valid skill levels as a constant
val VALID_SKILL_LEVELS = listOf("Beginner", "Intermediate", "Advanced", "Expert")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    userName: String
) {
    var email by remember { mutableStateOf("Loading...") }
    var bikeType by remember { mutableStateOf("Loading...") }
    var skillLevel by remember { mutableStateOf("Loading...") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // State for edit mode
    var isEditingProfilePicture by remember { mutableStateOf(false) }
    var isEditingRiderInfo by remember { mutableStateOf(false) }

    // Temporary state for editing
    var tempBikeType by remember { mutableStateOf(bikeType) }
    var tempSkillLevel by remember { mutableStateOf(skillLevel) }

    // Validation errors
    var bikeTypeError by remember { mutableStateOf<String?>(null) }
    var skillLevelError by remember { mutableStateOf<String?>(null) }

    val db = FirebaseFirestore.getInstance()
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    // Clear messages after a delay
    LaunchedEffect(successMessage, errorMessage) {
        if (successMessage != null || errorMessage != null) {
            kotlinx.coroutines.delay(3000)
            successMessage = null
            errorMessage = null
        }
    }

    // Fetches user data from Firestore with error handling
    LaunchedEffect(userName) {
        fetchUserData(
            userName = userName,
            db = db,
            onStart = { isLoading = true },
            onSuccess = { userEmail, userBikeType, userSkillLevel ->
                email = userEmail
                bikeType = userBikeType
                skillLevel = userSkillLevel
                tempBikeType = userBikeType
                tempSkillLevel = userSkillLevel
                errorMessage = null
            },
            onError = { error ->
                errorMessage = error
                email = "Error loading data"
                bikeType = "Error"
                skillLevel = "Error"
            },
            onComplete = { isLoading = false }
        )
    }

    // Function to handle profile picture change with error handling
    fun onChangeProfilePicture() {
        try {
            isEditingProfilePicture = true
            // In a real app, you would open image picker here
            // For now, we'll just simulate with a message
            println("Profile picture change requested")

            // Simulate image picker result
            handleProfilePictureUpdate(
                scope = scope,
                db = db,
                userName = userName,
                onSuccess = {
                    successMessage = "Profile picture updated successfully"
                    isEditingProfilePicture = false
                },
                onError = { error ->
                    errorMessage = "Failed to update profile picture: $error"
                    isEditingProfilePicture = false
                }
            )
        } catch (e: Exception) {
            errorMessage = "Unexpected error: ${e.message}"
            isEditingProfilePicture = false
        }
    }

    // Function to handle editing rider info
    fun onEditRiderInfo() {
        try {
            isEditingRiderInfo = true
            // Set temp values to current values
            tempBikeType = bikeType
            tempSkillLevel = skillLevel
            // Clear any previous validation errors
            bikeTypeError = null
            skillLevelError = null
        } catch (e: Exception) {
            errorMessage = "Error entering edit mode: ${e.message}"
            isEditingRiderInfo = false
        }
    }

    // Validate rider info fields
    fun validateRiderInfo(): Boolean {
        var isValid = true

        // Validate Bike Type
        if (tempBikeType.isBlank()) {
            bikeTypeError = "Bike type cannot be empty"
            isValid = false
        } else if (tempBikeType.length < 2) {
            bikeTypeError = "Bike type must be at least 2 characters"
            isValid = false
        } else if (tempBikeType.length > 50) {
            bikeTypeError = "Bike type must be less than 50 characters"
            isValid = false
        } else {
            bikeTypeError = null
        }

        // Validate Skill Level using the constant
        if (tempSkillLevel.isBlank()) {
            skillLevelError = "Skill level cannot be empty"
            isValid = false
        } else if (!VALID_SKILL_LEVELS.contains(tempSkillLevel)) {
            skillLevelError = "Please select a valid skill level from the dropdown"
            isValid = false
        } else {
            skillLevelError = null
        }

        return isValid
    }

    // Function to save rider info to Firestore with comprehensive error handling
    fun onSaveRiderInfo() {
        // Validate inputs first
        if (!validateRiderInfo()) {
            errorMessage = "Please fix the validation errors"
            return
        }

        scope.launch {
            try {
                // Check if values actually changed
                if (tempBikeType == bikeType && tempSkillLevel == skillLevel) {
                    isEditingRiderInfo = false
                    successMessage = "No changes to save"
                    return@launch
                }

                // First get the user document
                val querySnapshot = try {
                    db.collection("users")
                        .whereEqualTo("name", userName)
                        .get()
                        .await()
                } catch (e: Exception) {
                    errorMessage = "Failed to fetch user data: ${e.message}"
                    return@launch
                }

                val userDoc = querySnapshot.documents.firstOrNull()
                if (userDoc == null) {
                    errorMessage = "User document not found"
                    return@launch
                }

                if (userDoc.id.isEmpty()) {
                    errorMessage = "Invalid user document ID"
                    return@launch
                }

                // Update the document
                try {
                    userDoc.reference.update(
                        mapOf(
                            "bikeType" to tempBikeType,
                            "skillLevel" to tempSkillLevel
                        )
                    ).await()
                } catch (e: Exception) {
                    errorMessage = "Failed to update Firestore: ${e.message}"
                    return@launch
                }

                // Update local state
                bikeType = tempBikeType
                skillLevel = tempSkillLevel
                isEditingRiderInfo = false
                successMessage = "Rider information updated successfully"
                errorMessage = null

            } catch (e: Exception) {
                errorMessage = "Unexpected error while saving: ${e.message}"
            }
        }
    }

    // Function to cancel editing
    fun onCancelEdit() {
        try {
            isEditingRiderInfo = false
            isEditingProfilePicture = false
            // Reset temp values
            tempBikeType = bikeType
            tempSkillLevel = skillLevel
            // Clear validation errors
            bikeTypeError = null
            skillLevelError = null
            errorMessage = null
            successMessage = null
        } catch (e: Exception) {
            errorMessage = "Error canceling edit: ${e.message}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Profile",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF333333)),
                actions = {
                    IconButton(onClick = {
                        try {
                            navController.navigate("settings")
                        } catch (e: Exception) {
                            errorMessage = "Navigation error: ${e.message}"
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp)
            )
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFAAB7AE))
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF506D45),
                    modifier = Modifier.size(48.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFAAB7AE))
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Success message if any
                if (successMessage != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFE8F5E9)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = successMessage!!,
                                    color = Color(0xFF2E7D32),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // Error message if any
                if (errorMessage != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFEBEE)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage!!,
                                    color = Color(0xFFD32F2F),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // Top spacing
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // Profile Card
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Profile picture with edit button overlay
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE8ECEA)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Profile Picture",
                                        modifier = Modifier.size(64.dp),
                                        tint = Color(0xFF506D45)
                                    )
                                }
                                // Edit profile picture button
                                IconButton(
                                    onClick = { onChangeProfilePicture() },
                                    enabled = !isEditingProfilePicture,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(32.dp)
                                        .background(
                                            color = if (isEditingProfilePicture) Color.Gray else Color(0xFF506D45),
                                            shape = CircleShape
                                        )
                                ) {
                                    if (isEditingProfilePicture) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit profile picture",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = userName,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )

                            Text(
                                text = email,
                                fontSize = 14.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Stats Section
                item {
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            value = "12",
                            label = "Rides",
                            icon = Icons.Default.History
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            value = "450",
                            label = "Miles",
                            icon = Icons.AutoMirrored.Filled.DirectionsBike
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            value = "8",
                            label = "Events",
                            icon = Icons.Default.Star
                        )
                    }
                }

                // Rider Information Header
                item {
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Rider Information",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF333333)
                        )

                        if (!isEditingRiderInfo) {
                            Button(
                                onClick = { onEditRiderInfo() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF506D45)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Edit",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            Row {
                                TextButton(
                                    onClick = { onCancelEdit() },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Color.Gray
                                    )
                                ) {
                                    Text("Cancel", fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onSaveRiderInfo() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF506D45)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "Save",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Info Rows (with editing capability)
                item {
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isEditingRiderInfo) {
                        // Editable fields with validation errors
                        EditableProfileInfoRow(
                            icon = Icons.AutoMirrored.Filled.DirectionsBike,
                            label = "Bike Type",
                            value = tempBikeType,
                            onValueChange = {
                                tempBikeType = it
                                // Clear error when user starts typing
                                bikeTypeError = null
                            },
                            error = bikeTypeError,
                            isError = bikeTypeError != null
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Skill Level Dropdown
                        SkillLevelDropdown(
                            selectedSkillLevel = tempSkillLevel,
                            onSkillLevelSelected = { selectedLevel ->
                                tempSkillLevel = selectedLevel
                                skillLevelError = null
                            },
                            error = skillLevelError,
                            isError = skillLevelError != null
                        )

                        // Hint for valid skill levels
                        if (skillLevelError != null) {
                            Text(
                                text = "Please select from: ${VALID_SKILL_LEVELS.joinToString(", ")}",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 32.dp, top = 4.dp, end = 16.dp)
                            )
                        }
                    } else {
                        // Read-only display
                        ProfileInfoRow(
                            icon = Icons.AutoMirrored.Filled.DirectionsBike,
                            label = "Bike Type",
                            value = bikeType
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ProfileInfoRow(
                            icon = Icons.Default.Star,
                            label = "Skill Level",
                            value = skillLevel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SkillLevelDropdown(
    selectedSkillLevel: String,
    onSkillLevelSelected: (String) -> Unit,
    error: String? = null,
    isError: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val icon = Icons.Default.Star

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) Color(0xFFFFEBEE) else Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon container
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (isError) Color(0xFFFFCDD2) else Color(0xFFE8ECEA),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = "Skill Level",
                        tint = if (isError) Color(0xFFD32F2F) else Color(0xFF506D45),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "Skill Level",
                        fontSize = 12.sp,
                        color = if (isError) Color(0xFFD32F2F) else Color.Gray
                    )

                    // Dropdown button
                    Box {
                        OutlinedTextField(
                            value = selectedSkillLevel,
                            onValueChange = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isError) Color(0xFFD32F2F) else Color(0xFF506D45),
                                unfocusedBorderColor = if (isError) Color(0xFFD32F2F) else Color.LightGray,
                                focusedTextColor = Color(0xFF333333),
                                unfocusedTextColor = Color(0xFF333333),
                                errorBorderColor = Color(0xFFD32F2F),
                                errorTextColor = Color(0xFFD32F2F)
                            ),
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { expanded = true }) {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Dropdown",
                                        tint = if (isError) Color(0xFFD32F2F) else Color(0xFF506D45)
                                    )
                                }
                            },
                            isError = isError
                        )

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            VALID_SKILL_LEVELS.forEach { level ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            level,
                                            fontWeight = if (level == selectedSkillLevel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        onSkillLevelSelected(level)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Show error message if any
            if (error != null) {
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color = Color(0xFFD32F2F),
                    modifier = Modifier.padding(start = 64.dp, top = 4.dp)
                )
            }
        }
    }
}

// Helper function to fetch user data with error handling
suspend fun fetchUserData(
    userName: String,
    db: FirebaseFirestore,
    onStart: () -> Unit,
    onSuccess: (email: String, bikeType: String, skillLevel: String) -> Unit,
    onError: (String) -> Unit,
    onComplete: () -> Unit
) {
    onStart()
    try {
        val querySnapshot = try {
            db.collection("users")
                .whereEqualTo("name", userName)
                .get()
                .await()
        } catch (e: Exception) {
            onError("Network error: ${e.message}")
            onComplete()
            return
        }

        if (querySnapshot.isEmpty) {
            onSuccess("User not found", "Not specified", "Not specified")
        } else {
            val userDoc = querySnapshot.documents.firstOrNull()
            if (userDoc == null) {
                onError("User document is null")
                onComplete()
                return
            }

            val email = try {
                userDoc.getString("email") ?: "No email found"
            } catch (e: Exception) {
                "Error parsing email"
            }

            val bikeType = try {
                userDoc.getString("bikeType") ?: "Not specified"
            } catch (e: Exception) {
                "Error parsing bike type"
            }

            val skillLevel = try {
                userDoc.getString("skillLevel") ?: "Not specified"
            } catch (e: Exception) {
                "Error parsing skill level"
            }

            onSuccess(email, bikeType, skillLevel)
        }
    } catch (e: Exception) {
        onError("Unexpected error: ${e.message}")
    } finally {
        onComplete()
    }
}

// Helper function to handle profile picture update with error handling
fun handleProfilePictureUpdate(
    scope: CoroutineScope,
    db: FirebaseFirestore,
    userName: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    scope.launch {
        try {
            // Simulate network delay
            kotlinx.coroutines.delay(1000)

            // In a real app, you would:
            // 1. Pick image from gallery/camera
            // 2. Compress and upload to Firebase Storage
            // 3. Get download URL
            // 4. Update user document with new profile picture URL

            // Simulate random success/failure for demo
            if (Math.random() > 0.2) { // 80% success rate
                onSuccess()
            } else {
                onError("Simulated upload failure")
            }
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error")
        }
    }
}

@Composable
fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = Color(0xFFE8ECEA),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = Color(0xFF506D45),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF333333)
                )
            }
        }
    }
}

@Composable
fun EditableProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
    isError: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) Color(0xFFFFEBEE) else Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon container
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (isError) Color(0xFFFFCDD2) else Color(0xFFE8ECEA),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (isError) Color(0xFFD32F2F) else Color(0xFF506D45),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        label,
                        fontSize = 12.sp,
                        color = if (isError) Color(0xFFD32F2F) else Color.Gray
                    )

                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isError) Color(0xFFD32F2F) else Color(0xFF506D45),
                            unfocusedBorderColor = if (isError) Color(0xFFD32F2F) else Color.LightGray,
                            focusedTextColor = Color(0xFF333333),
                            unfocusedTextColor = Color(0xFF333333),
                            errorBorderColor = Color(0xFFD32F2F),
                            errorTextColor = Color(0xFFD32F2F)
                        ),
                        singleLine = true,
                        isError = isError
                    )
                }
            }

            // Show error message if any
            if (error != null) {
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color = Color(0xFFD32F2F),
                    modifier = Modifier.padding(start = 64.dp, top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: ImageVector
) {
    Card(
        modifier = modifier
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color(0xFF506D45),
                modifier = Modifier.size(24.dp)
            )

            Text(
                value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                label,
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}
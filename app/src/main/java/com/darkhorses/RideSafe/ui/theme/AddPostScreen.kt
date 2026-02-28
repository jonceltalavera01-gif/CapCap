package com.darkhorses.RideSafe.ui.theme

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPostScreen(navController: NavController, userName: String) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    var description by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf("Morning Ride") }
    var distance by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Create Post",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF333333)
                )

            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFAAB7AE))  // Match the background color from Homepage
                .padding(innerPadding)  // Use innerPadding to offset from top bar
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),  // Adjust this value for spacing below top bar
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Description TextField
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("What's on your mind?") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF06402B),
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color(0xFF06402B),
                    cursorColor = Color(0xFF06402B)
                )
            )

            // Activity TextField
            OutlinedTextField(
                value = activity,
                onValueChange = { activity = it },
                label = { Text("Activity (e.g., Morning Ride)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF06402B),
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color(0xFF06402B),
                    cursorColor = Color(0xFF06402B)
                )
            )

            // Distance TextField
            OutlinedTextField(
                value = distance,
                onValueChange = { distance = it },
                label = { Text("Distance (e.g., 10 km)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF06402B),
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color(0xFF06402B),
                    cursorColor = Color(0xFF06402B)
                )
            )

            // Post Button
            Button(
                onClick = {
                    if (description.isNotBlank()) {
                        isLoading = true
                        val post = hashMapOf(
                            "userName" to userName,
                            "description" to description,
                            "activity" to activity,
                            "distance" to distance,
                            "timestamp" to System.currentTimeMillis(),
                            "likes" to 0,
                            "comments" to 0,
                            "likedBy" to emptyList<String>(),
                            "status" to "pending"
                        )


                        db.collection("posts").add(post)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Post shared!", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                            .addOnFailureListener {
                                isLoading = false
                                Toast.makeText(context, "Error sharing post", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(context, "Please add a description", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF06402B),
                    disabledContainerColor = Color.Gray
                ),
                enabled = !isLoading,
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Post",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
package com.mynkralus.mynassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MynAssistantColors = darkColorScheme(
    primary = Color(0xFF8B9CFF),
    onPrimary = Color(0xFF10142C),
    secondary = Color(0xFF9DD9D2),
    background = Color(0xFF0B0D14),
    surface = Color(0xFF151925),
    onBackground = Color(0xFFF2F3FA),
    onSurface = Color(0xFFF2F3FA)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = MynAssistantColors) {
                MynAssistantApp()
            }
        }
    }
}

@Composable
private fun MynAssistantApp() {
    var command by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "MynAssistant",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Kişisel asistanın için yeni başlangıç.",
                    fontSize = 15.sp,
                    color = Color(0xFFA9B0C4)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171C2A))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "HAZIRLIK AŞAMASI",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Komut merkezi",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Arayüz hazır. Bu ilk sürüm komutları henüz çalıştırmaz; önce temel uygulamayı test ediyoruz.",
                        color = Color(0xFFB8BED0),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Ne yapmamı istersin?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = {
                        command = it
                        feedback = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Bir komut yaz...") },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = false,
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (command.isNotBlank()) {
                                feedback = "Komut alındı. Çalıştırma özelliği sonraki aşamada eklenecek."
                            }
                        }
                    )
                )
                Button(
                    onClick = {
                        feedback = if (command.isBlank()) {
                            "Önce bir komut yaz."
                        } else {
                            "Komut alındı. Çalıştırma özelliği sonraki aşamada eklenecek."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Komutu gönder", fontWeight = FontWeight.SemiBold)
                }
                feedback?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF151925), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MynAssistant", color = Color(0xFFBEC5D8), fontSize = 13.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("•", color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Android 0.1.0", color = Color(0xFFBEC5D8), fontSize = 13.sp)
            }
        }
    }
}

package com.xiaomi.parts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.xiaomi.parts.navigation.PartsNavGraph
import com.xiaomi.parts.navigation.Routes
import com.xiaomi.parts.ui.theme.XiaomiPartsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialRoute = when (intent?.action) {
            "com.xiaomi.parts.THERMAL" -> Routes.THERMAL
            "com.xiaomi.parts.FPS_METER" -> Routes.FPS
            "com.xiaomi.parts.SATURATION" -> Routes.SATURATION
            "com.xiaomi.parts.CLEAR_SPEAKER" -> Routes.SPEAKER
            "com.xiaomi.parts.DOZE" -> Routes.DOZE
            else -> Routes.HOME
        }

        setContent {
            XiaomiPartsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    PartsNavGraph(startDestination = initialRoute)
                }
            }
        }
    }
}

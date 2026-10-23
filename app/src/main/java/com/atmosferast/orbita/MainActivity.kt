package com.atmosferast.orbita

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.atmosferast.orbita.data.remote.RemoteChanges
import com.atmosferast.orbita.ui.navigation.OrbitaApp
import com.atmosferast.orbita.ui.theme.OrbitaTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var remoteChanges: RemoteChanges

    /** Set when the app goes to the background; turning the screen does not count. */
    private var wasInBackground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light-only theme: keep dark system bar icons regardless of the system setting.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            OrbitaTheme {
                OrbitaApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Back from the background: the data may have changed on another device meanwhile.
        if (wasInBackground) remoteChanges.refreshAll()
        wasInBackground = false
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) wasInBackground = true
    }
}

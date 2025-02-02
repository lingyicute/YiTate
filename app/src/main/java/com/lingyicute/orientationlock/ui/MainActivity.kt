package com.lingyicute.orientationlock.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingyicute.orientationlock.R
import com.lingyicute.orientationlock.utils.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        onOrientationSelected = { orientation ->
                            if (orientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED &&
                                !PermissionUtils.isDrawOverlaysPermissionGranted(this)
                            ) {
                                PermissionUtils.requestDrawOverlaysPermission(this)
                            } else {
                                viewModel.setOrientation(orientation)
                            }
                        },
                        onExcludeAppsClicked = {
                            // TODO: 实现排除应用功能
                        },
                        onAboutClicked = {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://github.com/lingyicute/YiTate/blob/main/README.md")
                                })
                            } catch (e: ActivityNotFoundException) {
                                // Ignore
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOrientationSelected: (Int) -> Unit,
    onExcludeAppsClicked: () -> Unit,
    onAboutClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOrientation by viewModel.orientation.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 顶部工具栏
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            actions = {
                IconButton(onClick = onExcludeAppsClicked) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.exclude_apps)
                    )
                }
                IconButton(onClick = onAboutClicked) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.about)
                    )
                }
            }
        )

        // 主要内容
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrientationButton(
                text = stringResource(R.string.orientation_default),
                isSelected = currentOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                onClick = { onOrientationSelected(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
            )

            OrientationButton(
                text = stringResource(R.string.orientation_full_sensor),
                isSelected = currentOrientation == ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
                onClick = { onOrientationSelected(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR) }
            )

            OrientationButton(
                text = stringResource(R.string.orientation_landscape),
                isSelected = currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                onClick = { onOrientationSelected(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) }
            )

            OrientationButton(
                text = stringResource(R.string.orientation_reverse_landscape),
                isSelected = currentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
                onClick = { onOrientationSelected(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) }
            )

            OrientationButton(
                text = stringResource(R.string.orientation_sensor_landscape),
                isSelected = currentOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                onClick = { onOrientationSelected(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) }
            )

            OrientationButton(
                text = stringResource(R.string.orientation_portrait),
                isSelected = currentOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                onClick = { onOrientationSelected(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) }
            )

            OrientationButton(
                text = stringResource(R.string.orientation_reverse_portrait),
                isSelected = currentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                onClick = { onOrientationSelected(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) }
            )

            OrientationButton(
                text = stringResource(R.string.orientation_sensor_portrait),
                isSelected = currentOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
                onClick = { onOrientationSelected(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT) }
            )

            if (isServiceRunning) {
                Text(
                    text = stringResource(R.string.service_running),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun OrientationButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    ) {
        Text(text = text)
    }
} 
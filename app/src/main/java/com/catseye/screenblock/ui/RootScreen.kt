package com.catseye.screenblock.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catseye.screenblock.R
import com.catseye.screenblock.viewmodel.RootScreenEvent
import com.catseye.screenblock.viewmodel.RootViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.mrhwsn.composelock.ComposeLock
import com.mrhwsn.composelock.ComposeLockCallback
import com.mrhwsn.composelock.Dot
import kotlinx.coroutines.delay

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RootScreen(
    rootViewModel: RootViewModel,
//    backStackEntry: NavBackStackEntry
) {
    val context = LocalContext.current

    var isGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val state by rootViewModel.state.collectAsStateWithLifecycle()

//    resultLauncher.launch(Manifest.permission.SYSTEM_ALERT_WINDOW)
    val runtimePermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            POST_NOTIFICATIONS
        ),
        onPermissionsResult = { result ->

        }
    )

    LaunchedEffect(Unit) {
        runtimePermissions.launchMultiplePermissionRequest()
    }

    val settingsIntent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Many OEMs don’t return a result; re-check explicitly.
        isGranted = Settings.canDrawOverlays(context)
        if (isGranted) {
            Log.d("PermissionCheck", "Permission granted")
        } else {
            // todo: toast?
            Log.d("PermissionCheck", "Permission denied")

        }
    }

    var isDialogOpened by rememberSaveable { mutableStateOf(false) }

    if(isDialogOpened) {
        SetKeyDialog(
            onKeyEntered = { rootViewModel.onEvent(RootScreenEvent.CreateKeyPattern(it)) },
            onDismiss = { isDialogOpened = false }
        )
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .background(colorScheme.surface)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ButtonsBlock(
            isPermissionEnabled = isGranted,
            onEnablePermission = {
                if (!isGranted) {
                    Log.d("PermissionCheck", "asking for permission")
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    settingsIntent.launch(intent)
                } else {
                    Log.d("PermissionCheck", "Permission already acquired")
                }
            },
            onDialogOpen = { isDialogOpened = !isDialogOpened },
            isPatternSet = state.isKeyEstablished
        )
        SwitchBlock(
            isPermissionEnabled = isGranted,
            isBubbleEnabled = state.isOverlayActive && state.isKeyEstablished,
            onRunService = {
                rootViewModel.onEvent(RootScreenEvent.ToggleFloatingBubble(it))
            }
        )
    }
}

@Composable
fun ButtonsBlock(
    isPermissionEnabled: Boolean,
    isPatternSet: Boolean,
    onEnablePermission: () -> Unit,
    onDialogOpen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(width = 1.dp, color = colorScheme.outline, shape = RoundedCornerShape(16.dp)),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(100))
                .background(colorScheme.secondaryContainer)
        ) {
            Icon(
                modifier = Modifier
                    .padding(8.dp)
                    .size(50.dp),
                tint = colorScheme.secondary,
                painter = painterResource(R.drawable.android_icon),
                contentDescription = null
            )
        }

        Text(
            modifier = Modifier
                .padding(16.dp),
            text = "Надайте доступ",
            style = typography.titleLarge,
            color = colorScheme.inverseSurface
        )

        ButtonWithText(
            buttonText = if(isPermissionEnabled) "Доступ надано" else "Надати доступ",
            desc = "Для роботи додатку, необхідно надати доступ відображення поверх інших додатків",
            isButtonEnabled = !isPermissionEnabled,
            onClick = onEnablePermission
        )

        ButtonWithText(
            buttonText = if(isPatternSet) "Змінити ключ" else "Створити ключ",
            desc = "Для зняття блокування екрану, необхідно встановити графічний ключ",
            onClick = onDialogOpen
        )



    }

}

@Composable
fun ButtonWithText(
    buttonText: String,
    desc: String,
    isButtonEnabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier
                .padding(8.dp),
            text = desc,
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center

        )

        Button(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            enabled = isButtonEnabled,
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                contentColor = colorScheme.surface,
                containerColor = colorScheme.onSurface,
                disabledContentColor = colorScheme.onSurface,
                disabledContainerColor = colorScheme.inverseOnSurface,
            ),
            border = if(!isButtonEnabled) BorderStroke(width = 1.dp, color = colorScheme.onSurface) else null
        ) {
            Text(
                text = buttonText,
                style = typography.bodyMedium
            ) // change text based on permission status
        }
    }
}

// switched on for when bubble is up

@Composable
fun SwitchBlock(
    isPermissionEnabled: Boolean,
    isBubbleEnabled: Boolean,
    onRunService: (Boolean) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(width = 1.dp, color = colorScheme.outline, shape = RoundedCornerShape(16.dp)),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(

            ) {
                Text(
                    modifier = Modifier
                        .padding(4.dp),
                    text = "Увімкнути режим",
                    style = typography.titleMedium,
                    color = colorScheme.inverseSurface

                )

                Text(
                    modifier = Modifier
                        .padding(4.dp),
                    text = "Увімкнути режим блокування екрану",
                    style = typography.bodyMedium,
                    color = colorScheme.inverseSurface

                )
            }

            SwitchButton(
                isPermissionEnabled = isPermissionEnabled,
                isBubbleEnabled = isBubbleEnabled,
                onRunService = onRunService
            )
        }
    }
}

@Composable
fun SwitchButton(
    isPermissionEnabled: Boolean,
    isBubbleEnabled: Boolean,
    onRunService: (Boolean) -> Unit
) {

    Switch(
        enabled = isPermissionEnabled,
        checked = isBubbleEnabled,
        onCheckedChange = {
            onRunService(it)
        },
        thumbContent = if(isBubbleEnabled) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        } else {
            {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        },
        colors = SwitchDefaults.colors(
            checkedIconColor = colorScheme.onSurface,
            checkedTrackColor = colorScheme.onSurface,
            checkedThumbColor = colorScheme.surface,
            checkedBorderColor = colorScheme.surface,
            uncheckedIconColor = colorScheme.surface,
            uncheckedTrackColor = colorScheme.surface,
            uncheckedThumbColor = colorScheme.onSurface,
            uncheckedBorderColor = colorScheme.onSurface
        )
    )
}

@Composable
fun SetKeyDialog(
    onKeyEntered: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {

    //
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ComposeLock(
            onKeyEntered = onKeyEntered,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun ComposeLock(
    modifier: Modifier = Modifier,
    onKeyEntered: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(0.3f)),
        contentAlignment = Alignment.Center
    ) {

        ComposeLock(
            modifier = Modifier
                .size(300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(0.5f)),
            dimension = 3,
            sensitivity = 100f,
            dotsColor = Color.White,
            dotsSize = 20f,
            linesColor = Color.White,
            linesStroke = 30f,
            animationDuration = 200,
            animationDelay = 100,
            callback = object : ComposeLockCallback {
                override fun onStart(dot: Dot) {
                    Log.d("KeyLog", "start")
                }

                override fun onDotConnected(dot: Dot) {
                    Log.d("KeyLog", "connect")
                }

                override fun onResult(result: List<Dot>) {
                    val res = result.map { it.id }
                    onKeyEntered(res)
                    onDismiss()
                }
            }
        )
    }
}

@Composable
fun TimeoutComposeLock(
    modifier: Modifier = Modifier,
    onKeyEntered: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
    isPatternVisible: Boolean
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(0.3f)),
        contentAlignment = Alignment.Center
    ) {

        var actionTimeout by remember { mutableStateOf<Dot?>(null) }

        if(isPatternVisible) {
            LaunchedEffect(true) {
//                Log.d("actionTimeout", "actionTimeout: $actionTimeout")
                if(actionTimeout == null) {
                    delay(5000)
                    onDismiss()
                }
            }
        }

        ComposeLock(
            modifier = Modifier
                .size(300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(0.5f)),
            dimension = 3,
            sensitivity = 100f,
            dotsColor = Color.White,
            dotsSize = 20f,
            linesColor = Color.White,
            linesStroke = 30f,
            animationDuration = 200,
            animationDelay = 100,
            callback = object : ComposeLockCallback {
                override fun onStart(dot: Dot) {
                    Log.d("KeyLog", "start")
                    actionTimeout = dot
                }

                override fun onDotConnected(dot: Dot) {
                    Log.d("KeyLog", "connect")
                    actionTimeout = dot
                }

                override fun onResult(result: List<Dot>) {
                    val res = result.map { it.id }
                    onKeyEntered(res)
                    onDismiss()
                }
            }
        )
    }
}


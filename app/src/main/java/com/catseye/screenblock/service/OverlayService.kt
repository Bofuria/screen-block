package com.catseye.screenblock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.catseye.screenblock.MainActivity
import com.catseye.screenblock.R
import com.catseye.screenblock.navigation.ROOT_URI
import com.catseye.screenblock.ui.OverlayScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.catseye.screenblock.data.KeyManager
import com.catseye.screenblock.ui.TimeoutComposeLock
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private const val APP_UPDATE_PENDING_INTENT_REQUEST_CODE = 1001

@AndroidEntryPoint
class OverlayService() : LifecycleService(),
    SavedStateRegistryOwner
{

    @Inject
    lateinit var keyManager: KeyManager

    private val ssrController = SavedStateRegistryController.create(this)
    override val savedStateRegistry get() = ssrController.savedStateRegistry

    private lateinit var windowManager: WindowManager

    private val _isOverlayActive = MutableStateFlow(false)
    val isOverlayActive = _isOverlayActive.asStateFlow()

    private var _isPatternVisible = MutableStateFlow(false)
    val isPatternVisible = _isPatternVisible.asStateFlow()

    private var overlayView: ComposeView? = null
    private var overlayBubbleView: ComposeView? = null
    private var overlayLockView: ComposeView? = null

    private lateinit var bubbleLp: WindowManager.LayoutParams
    private lateinit var overlayLp: WindowManager.LayoutParams
    private lateinit var lockLp: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()

        ssrController.performAttach()
        ssrController.performRestore(loadSavedState())

        createNotificationChannel()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        overlayLp.gravity = Gravity.TOP
        overlayView = getOverlayView()
        overlayView?.visibility = View.GONE // hide initially

        bubbleLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
//            x = bubbleStartX; y = bubbleStartY
        }

        lockLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
                if(Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        overlayBubbleView = getBubbleView(
            onDrag = { dx, dy ->
                bubbleLp.x = dx
                bubbleLp.y = dy
                windowManager.updateViewLayout(overlayBubbleView, bubbleLp)
            },
            onClick = {
                _isPatternVisible.value = true
                overlayLockView?.visibility = View.VISIBLE
//
            }
        )

        overlayLockView = getLockView(
            onKeyEntered = { list ->
                lifecycleScope.launch {
                    val keyMatched = keyManager.compareKey(list)
                    Log.d("KeyMatch", "keyMatched: $keyMatched")
                    disableBlock(keyMatched)
                    _isPatternVisible.value = false
                    overlayLockView?.visibility = View.GONE
                }
            },
            onDismiss = {
                _isPatternVisible.value = false
                overlayLockView?.visibility = View.GONE
//                windowManager.removeView(overlayLockView)
            }
        )

        overlayLockView?.visibility = View.GONE

        windowManager.addView(overlayView, overlayLp)
        windowManager.addView(overlayBubbleView, bubbleLp)
        windowManager.addView(overlayLockView, lockLp)

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(ROOT_URI),
            this,
            MainActivity::class.java
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            APP_UPDATE_PENDING_INTENT_REQUEST_CODE,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { PendingIntent.FLAG_IMMUTABLE } else { PendingIntent.FLAG_UPDATE_CURRENT }
        )

        startForeground(NOTIF_ID,
            buildNotification(pendingIntent),
        )
    }

    private fun enableBlock() {
        overlayView?.visibility = View.VISIBLE
        overlayLp.flags = overlayLp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        windowManager.updateViewLayout(overlayView, overlayLp)
        _isOverlayActive.value = true
    }

    private fun disableBlock(isKeyMatched: Boolean) {
        if(isKeyMatched) {
            _isOverlayActive.value = false
            overlayView?.visibility = View.GONE
            overlayLp.flags = overlayLp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            windowManager.updateViewLayout(overlayView, overlayLp)
        } else {
            _isPatternVisible.value = false
        }
    }

    fun getOverlayView(): ComposeView? {
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)

            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {

//                val lifecycle = LocalLifecycleOwner.current.lifecycle
//
//                ResumeListener(lifecycle) {
//                    disableBlock(true)
//                }

                // todo: pass the callback for screen block component
                // displays the bubble and the block overlay. Clicking the bubble only shows/hides the
                // block overlay, to turn off the service user either clicks notification or
                // manually navigates back while the block is off
                OverlayScreen()
            }
        }
    }

    fun getBubbleView(
        onDrag: (dx: Int, dy: Int) -> Unit,
        onClick: () -> Unit
    ): ComposeView? {
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)

            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                val lockState by isOverlayActive.collectAsStateWithLifecycle()

                var offsetX by rememberSaveable { mutableIntStateOf(0) }
                var offsetY by rememberSaveable { mutableIntStateOf(0) }

                var isRecentlyClicked by rememberSaveable { mutableStateOf(false) }
                var isIconTransparent by rememberSaveable { mutableStateOf(true) }

                LaunchedEffect(lockState) {
                    if(lockState) {
                        isIconTransparent = false
                        delay(3000)
                        isIconTransparent = true
                    } else {

                    }
                }

                LaunchedEffect(isRecentlyClicked) {
                    Log.d("Click", "isRecentlyClicked: $isRecentlyClicked")
                    if(isRecentlyClicked) {
                        isIconTransparent = false
//                        isRecentlyClicked = true
                        delay(3000)
                        isRecentlyClicked = false
                        isIconTransparent = true

                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .alpha(if(isIconTransparent) 0.5f else 1f)
                        .background(if(!lockState) colorScheme.errorContainer else colorScheme.surface)
                ) {
                    Icon(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(40.dp)
                            .alpha(if(isIconTransparent) 0.5f else 1f)
                            .pointerInput(Unit) {
                                detectDragGestures { change, drag ->
                                    offsetX += drag.x.roundToInt()
                                    offsetY += drag.y.roundToInt()
                                    change.consume()
                                    onDrag(offsetX, offsetY) // send deltas
                                }
                            }
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {
                                    if(!lockState) {
                                        enableBlock()
                                    } else if(isRecentlyClicked) {
                                        Log.d("OverlayCall", "onClick for key pattern")
                                        onClick()
                                    }
                                    isRecentlyClicked = true
                                }
                            ),
                        painter = painterResource(if(lockState) R.drawable.lock_icon else R.drawable.lock_open_icon),
                        contentDescription = "",
                        tint = if(lockState) colorScheme.error else colorScheme.onSurface
                    )
                }
            }
        }
    }

    fun getLockView(
        onKeyEntered: (List<Int>) -> Unit,
        onDismiss: () -> Unit
    ): ComposeView? {
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {

                val patternVisible by isPatternVisible.collectAsStateWithLifecycle()

                TimeoutComposeLock(
                    modifier = Modifier,
                    onKeyEntered = onKeyEntered,
                    onDismiss = onDismiss,
                    isPatternVisible = patternVisible
                )
            }
        }
    }

    override fun onDestroy() {
        val out = Bundle()
        ssrController.performSave(out)
        saveSavedState(out)

        super.onDestroy()
        overlayBubbleView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        overlayView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        overlayLockView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        overlayLockView = null
        overlayView = null
        overlayBubbleView = null

        stopForeground(STOP_FOREGROUND_REMOVE)
//        stopSelf() <- only if stop from inside
        // s
    }

    private fun buildNotification(pendingIntent: PendingIntent): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.lock_icon)
            .setContentTitle("Блокування активно")
            .setContentText("Натисніть, щоб відключити")
            // close service?
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // automatically dismisses notification when tapped
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Screen block control"
            val description = "Displays notification to control the block screen overlay"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
            mChannel.description = description
            val notManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notManager.createNotificationChannel(mChannel)
        }
    }

    // --- simple persistence for the saved-state bundle ---
    private fun saveSavedState(b: Bundle) {
        val p = android.os.Parcel.obtain()
        p.writeBundle(b)
        val bytes = p.marshall()
        p.recycle()
        getSharedPreferences("overlay_state", MODE_PRIVATE)
            .edit()
            .putString("ssr", Base64.encodeToString(bytes, Base64.DEFAULT))
            .apply()
    }

    private fun loadSavedState(): Bundle? {
        val s = getSharedPreferences("overlay_state", MODE_PRIVATE)
            .getString("ssr", null) ?: return null
        val data = Base64.decode(s, Base64.DEFAULT)
        val p = android.os.Parcel.obtain()
        p.unmarshall(data, 0, data.size)
        p.setDataPosition(0)
        val b = p.readBundle(javaClass.classLoader)
        p.recycle()
        return b
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "overlay_fgs"
    }
}





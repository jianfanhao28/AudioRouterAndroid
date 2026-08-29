package com.jian.audioRouter

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSessionManager: MediaSessionManager

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshWebState()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioManager =
            getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mediaSessionManager =
            getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

        requestPermissionsIfNeeded()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = true

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            addJavascriptInterface(
                NativeBridge(),
                "NativeBridge"
            )

            loadUrl("file:///android_asset/index.html")
        }

        setContentView(webView)
    }

    override fun onResume() {
        super.onResume()

        if (::webView.isInitialized) {
            refreshWebState()
        }
    }

    private fun refreshWebState() {
        if (!::webView.isInitialized) return

        webView.postDelayed({
            webView.evaluateJavascript(
                "window.refreshNativeState && window.refreshNativeState();",
                null
            )
        }, 150)
    }

    private fun requestPermissionsIfNeeded() {

        val required = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            required += Manifest.permission.BLUETOOTH_SCAN
            required += Manifest.permission.BLUETOOTH_CONNECT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required += Manifest.permission.POST_NOTIFICATIONS
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissions.launch(missing.toTypedArray())
        }
    }

    /*
     * 检查当前应用是否已经获得通知使用权。
     *
     * Android 没有：
     * getEnabledNotificationListenerPackages()
     *
     * 所以这里读取系统的
     * Settings.Secure.ENABLED_NOTIFICATION_LISTENERS
     */
    private fun notificationAccessEnabled(): Boolean {

        val enabledListeners =
            Settings.Secure.getString(
                contentResolver,
                "enabled_notification_listeners"
            ) ?: return false

        return enabledListeners
            .split(":")
            .mapNotNull {
                ComponentName.unflattenFromString(it)
            }
            .any {
                it.packageName == packageName
            }
    }

    private fun activeMediaController(): MediaController? {

        if (!notificationAccessEnabled()) {
            return null
        }

        return try {

            val listenerComponent = ComponentName(
                this,
                MediaNotificationListener::class.java
            )

            val sessions =
                mediaSessionManager.getActiveSessions(
                    listenerComponent
                )

            /*
             * 优先 Apple Music
             */
            sessions.firstOrNull {
                it.packageName == "com.apple.android.music"
            }
                ?: sessions.firstOrNull {
                    it.packageName.contains(
                        "apple",
                        ignoreCase = true
                    )
                }
                ?: sessions.firstOrNull()

        } catch (_: SecurityException) {

            null
        }
    }

    private fun bitmapDataUri(
        bitmap: Bitmap?
    ): String? {

        if (bitmap == null) {
            return null
        }

        return try {

            val scaled =
                Bitmap.createScaledBitmap(
                    bitmap,
                    512,
                    512,
                    true
                )

            val output =
                ByteArrayOutputStream()

            scaled.compress(
                Bitmap.CompressFormat.JPEG,
                88,
                output
            )

            "data:image/jpeg;base64," +
                    Base64.getEncoder()
                        .encodeToString(
                            output.toByteArray()
                        )

        } catch (_: Exception) {

            null
        }
    }

    inner class NativeBridge {

        @JavascriptInterface
        fun getConnectedOutputs(): String {

            val result = JSONArray()

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return result.toString()
            }

            audioManager
                .getDevices(
                    AudioManager.GET_DEVICES_OUTPUTS
                )
                .forEach { device ->

                    if (isBluetoothAudio(device)) {

                        result.put(
                            JSONObject().apply {

                                put(
                                    "id",
                                    device.id
                                )

                                put(
                                    "name",
                                    device.productName
                                        ?.toString()
                                        ?.ifBlank {
                                            "Bluetooth 音响"
                                        }
                                        ?: "Bluetooth 音响"
                                )

                                put(
                                    "type",
                                    audioTypeName(
                                        device.type
                                    )
                                )
                            }
                        )
                    }
                }

            return result.toString()
        }

        @JavascriptInterface
        fun getMediaState(): String {

            val controller =
                activeMediaController()

            val result =
                JSONObject().apply {

                    put(
                        "accessEnabled",
                        notificationAccessEnabled()
                    )

                    put(
                        "available",
                        controller != null
                    )

                    put(
                        "packageName",
                        controller?.packageName ?: ""
                    )
                }

            val metadata =
                controller?.metadata

            if (metadata != null) {

                result.put(
                    "title",
                    metadata.getString(
                        MediaMetadata.METADATA_KEY_TITLE
                    ) ?: ""
                )

                result.put(
                    "artist",
                    metadata.getString(
                        MediaMetadata.METADATA_KEY_ARTIST
                    )
                        ?: metadata.getString(
                            MediaMetadata.METADATA_KEY_ALBUM_ARTIST
                        )
                        ?: ""
                )

                result.put(
                    "album",
                    metadata.getString(
                        MediaMetadata.METADATA_KEY_ALBUM
                    ) ?: ""
                )

                result.put(
                    "duration",
                    metadata.getLong(
                        MediaMetadata.METADATA_KEY_DURATION
                    )
                )

                bitmapDataUri(
                    metadata.getBitmap(
                        MediaMetadata.METADATA_KEY_ART
                    )
                )?.let {

                    result.put(
                        "artwork",
                        it
                    )

                } ?: bitmapDataUri(
                    metadata.getBitmap(
                        MediaMetadata.METADATA_KEY_ALBUM_ART
                    )
                )?.let {

                    result.put(
                        "artwork",
                        it
                    )
                }
            }

            controller
                ?.playbackState
                ?.let { state ->

                    result.put(
                        "playing",
                        state.state ==
                                android.media.session.PlaybackState.STATE_PLAYING
                    )

                    result.put(
                        "position",
                        state.position
                    )
                }

            return result.toString()
        }

        @JavascriptInterface
        fun openMediaAccess() {

            startActivity(
                Intent(
                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                )
            )
        }

        @JavascriptInterface
        fun openBluetoothSettings() {

            startActivity(
                Intent(
                    Settings.ACTION_BLUETOOTH_SETTINGS
                )
            )
        }

        @JavascriptInterface
        fun playPause() {

            val controller =
                activeMediaController()

            if (controller != null) {

                val playing =
                    controller.playbackState?.state ==
                            android.media.session.PlaybackState.STATE_PLAYING

                if (playing) {
                    controller.transportControls.pause()
                } else {
                    controller.transportControls.play()
                }

            } else {

                openMediaAccess()
            }
        }

        @JavascriptInterface
        fun next() {

            activeMediaController()
                ?.transportControls
                ?.skipToNext()
                ?: openMediaAccess()
        }

        @JavascriptInterface
        fun previous() {

            activeMediaController()
                ?.transportControls
                ?.skipToPrevious()
                ?: openMediaAccess()
        }

        private fun isBluetoothAudio(
            device: AudioDeviceInfo
        ): Boolean {

            return when (device.type) {

                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> true

                else -> false
            }
        }

        private fun audioTypeName(
            type: Int
        ): String {

            return when (type) {

                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ->
                    "A2DP"

                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_BLE_HEADSET ->
                    "LE Audio"

                AudioDeviceInfo.TYPE_BLUETOOTH_SCO ->
                    "Bluetooth"

                else ->
                    "Bluetooth"
            }
        }
    }

    override fun onDestroy() {

        if (::webView.isInitialized) {
            webView.destroy()
        }

        super.onDestroy()
    }
}

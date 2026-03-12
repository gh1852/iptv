package com.jons.iptv

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jons.iptv.data.AppUpdateRepository
import com.jons.iptv.data.CategoryChannels
import com.jons.iptv.data.Channel
import com.jons.iptv.data.ChannelRepository
import com.jons.iptv.databinding.ActivityMainBinding
import com.jons.iptv.input.MainKeyEventRouter
import com.jons.iptv.playback.PlayerEngineCoordinator
import com.jons.iptv.ui.GroupedChannelAdapter
import com.jons.iptv.ui.dialog.PlaybackFailureDialogCoordinator
import com.jons.iptv.ui.menu.MenuFocusCoordinator
import com.jons.iptv.update.AppUpdateCoordinator
import kotlinx.coroutines.launch
import java.util.LinkedHashMap

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val BACK_PRESS_EXIT_WINDOW_MS = 2_000L
        private const val STARTUP_MENU_AUTO_HIDE_DELAY_MS = 1_500L
        private const val MIN_SPLASH_DURATION_MS = 2000L
        private const val PREF_LAST_CHANNEL = "last_channel_store"
        private const val KEY_LAST_CHANNEL_CATEGORY = "last_channel_category"
        private const val KEY_LAST_CHANNEL_NAME = "last_channel_name"
    }

    private lateinit var binding: ActivityMainBinding

    private val repository = ChannelRepository()
    private val appUpdateRepository = AppUpdateRepository()
    private val groupedChannelAdapter = GroupedChannelAdapter { channel ->
        onChannelSelected(channel)
    }

    private lateinit var playbackFailureDialogCoordinator: PlaybackFailureDialogCoordinator
    private lateinit var appUpdateCoordinator: AppUpdateCoordinator
    private lateinit var menuFocusCoordinator: MenuFocusCoordinator
    private lateinit var playerEngineCoordinator: PlayerEngineCoordinator
    private lateinit var keyEventRouter: MainKeyEventRouter
    private var hasDeferredUpdateCheck = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashStartTime = SystemClock.uptimeMillis()
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            SystemClock.uptimeMillis() - splashStartTime < MIN_SPLASH_DURATION_MS
        }
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository.preloadChannels()
        enableFullscreenIfPhone()

        playbackFailureDialogCoordinator = PlaybackFailureDialogCoordinator(this) { channel ->
            playChannel(channel, 0)
        }
        playerEngineCoordinator = PlayerEngineCoordinator(
            activity = this,
            binding = binding,
            groupedChannelAdapter = groupedChannelAdapter,
            onShowPlaybackFailureDialog = { channel -> showPlaybackFailureDialog(channel) },
            onDismissPlaybackFailureDialog = { dismissPlaybackFailureDialogIfShowing() },
            logTag = TAG
        )
        appUpdateCoordinator = AppUpdateCoordinator(
            activity = this,
            appUpdateRepository = appUpdateRepository,
            logTag = TAG
        )
        menuFocusCoordinator = MenuFocusCoordinator(
            binding = binding,
            groupedChannelAdapter = groupedChannelAdapter,
            currentChannelProvider = { playerEngineCoordinator.getCurrentChannel() }
        )
        keyEventRouter = MainKeyEventRouter(
            backPressExitWindowMs = BACK_PRESS_EXIT_WINDOW_MS,
            isMenuVisible = { menuVisible() },
            hideMenu = { moveFocusToPlayer -> hideMenu(moveFocusToPlayer) },
            playNextChannel = { playerEngineCoordinator.playNextChannel() },
            playPreviousChannel = { playerEngineCoordinator.playPreviousChannel() },
            handleMenuConfirmKey = { handleMenuConfirmKey() },
            finishActivity = { finish() },
            showPressBackAgainToast = {
                Toast.makeText(
                    this,
                    getString(R.string.press_back_again_to_exit),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        playerEngineCoordinator.initPlayerEngine()
        initList()
        initMenuInteractions()
        loadChannels()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableFullscreenIfPhone()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (keyEventRouter.handle(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleMenuConfirmKey(): Boolean {
        return menuFocusCoordinator.handleMenuConfirmKey(currentFocus)
    }

    private fun toggleMenuVisibility(moveFocusToPlayerWhenHide: Boolean) {
        menuFocusCoordinator.toggleMenuVisibility(moveFocusToPlayerWhenHide)
    }

    private fun menuVisible(): Boolean {
        return menuFocusCoordinator.isMenuVisible()
    }

    private fun showMenu() {
        menuFocusCoordinator.showMenu()
    }

    private fun hideMenu(moveFocusToPlayer: Boolean = true) {
        menuFocusCoordinator.hideMenu(moveFocusToPlayer)
    }

    private fun enableFullscreenIfPhone() {
        if (isTvDevice()) return

        val appWindow = window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(appWindow, false)
            appWindow.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            return
        }

        @Suppress("DEPRECATION")
        appWindow.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
    }

    private fun isTvDevice(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun initList() {
        binding.groupedChannelRecycler.layoutManager = LinearLayoutManager(this)
        binding.groupedChannelRecycler.adapter = groupedChannelAdapter
        binding.loadingContainer.visibility = View.VISIBLE
        binding.menuContent.visibility = View.GONE
    }

    private fun initMenuInteractions() {
        binding.playerContainer.setOnClickListener {
            toggleMenuVisibility(moveFocusToPlayerWhenHide = true)
        }
    }

    private fun loadChannels() {
        lifecycleScope.launch {
            runCatching { repository.getChannels() }
                .onSuccess { channels ->
                    binding.loadingContainer.visibility = View.GONE
                    binding.menuContent.visibility = View.VISIBLE

                    val groupedChannels = buildGroupedChannels(channels)
                    val initialChannel = resolveInitialChannel(groupedChannels)
                    groupedChannelAdapter.submitGroups(groupedChannels, initialChannel?.category)

                    if (initialChannel != null) {
                        playChannel(initialChannel, 0)
                        showMenu()
                        binding.menuContainer.postDelayed({
                            if (menuVisible() && !isFinishing && !isDestroyed) {
                                hideMenu(moveFocusToPlayer = true)
                            }
                        }, STARTUP_MENU_AUTO_HIDE_DELAY_MS)
                    } else {
                        showMenu()
                    }

                    triggerUpdateCheckAfterFirstRender()
                }
                .onFailure {
                    binding.loadingContainer.visibility = View.GONE
                    binding.menuContent.visibility = View.VISIBLE
                    Toast.makeText(this@MainActivity, getString(R.string.load_failed), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun buildGroupedChannels(channels: List<Channel>): List<CategoryChannels> {
        val grouped = LinkedHashMap<String, MutableList<Channel>>()
        channels.forEach { channel ->
            val category = channel.category.ifBlank { getString(R.string.category_other) }
            grouped.getOrPut(category) { mutableListOf() }.add(channel)
        }
        return grouped.map { (category, groupedChannels) ->
            CategoryChannels(category = category, channels = groupedChannels)
        }
    }

    private fun onChannelSelected(channel: Channel) {
        playChannel(channel, 0)
        hideMenu(moveFocusToPlayer = true)
    }

    private fun resolveInitialChannel(groupedChannels: List<CategoryChannels>): Channel? {
        val fallback = groupedChannels.firstOrNull()?.channels?.firstOrNull()
        val saved = loadLastChannelRef() ?: return fallback
        val (savedCategory, savedName) = saved

        return groupedChannels
            .asSequence()
            .flatMap { it.channels.asSequence() }
            .firstOrNull { it.category == savedCategory && it.name == savedName }
            ?: fallback
    }

    private fun saveLastChannel(channel: Channel) {
        val category = channel.category.trim()
        val name = channel.name.trim()
        if (category.isEmpty() || name.isEmpty()) return

        getSharedPreferences(PREF_LAST_CHANNEL, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_CHANNEL_CATEGORY, category)
            .putString(KEY_LAST_CHANNEL_NAME, name)
            .apply()
    }

    private fun loadLastChannelRef(): Pair<String, String>? {
        val prefs = getSharedPreferences(PREF_LAST_CHANNEL, Context.MODE_PRIVATE)
        val category = prefs.getString(KEY_LAST_CHANNEL_CATEGORY, null)?.trim().orEmpty()
        val name = prefs.getString(KEY_LAST_CHANNEL_NAME, null)?.trim().orEmpty()
        if (category.isEmpty() || name.isEmpty()) return null
        return category to name
    }

    private fun playChannel(channel: Channel, streamIndex: Int) {
        saveLastChannel(channel)
        playerEngineCoordinator.playChannel(channel, streamIndex)
    }


    private fun showPlaybackFailureDialog(channel: Channel) {
        playbackFailureDialogCoordinator.showPlaybackFailureDialog(channel)
    }

    private fun dismissPlaybackFailureDialogIfShowing() {
        playbackFailureDialogCoordinator.dismissIfShowing()
    }


    private fun triggerUpdateCheckAfterFirstRender() {
        if (hasDeferredUpdateCheck) return
        hasDeferredUpdateCheck = true
        lifecycleScope.launch {
            checkUpdateSilently()
        }
    }

    private fun checkUpdateSilently() {
        appUpdateCoordinator.checkUpdateSilently()
    }

    override fun onResume() {
        super.onResume()
        appUpdateCoordinator.resumePendingUpdateIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        playerEngineCoordinator.onStart()
    }

    override fun onPause() {
        playerEngineCoordinator.onPause()
        keyEventRouter.resetBackPressWindow()
        super.onPause()
    }

    override fun onStop() {
        playerEngineCoordinator.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        playbackFailureDialogCoordinator.dismissImmediately()
        appUpdateCoordinator.dismissIfShowing()
        playerEngineCoordinator.release()
        super.onDestroy()
    }
}

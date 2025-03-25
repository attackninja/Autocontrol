/*
 * autocontrol14 - Version 4.0
 * Copyright © 2025 Z.chao Zhang
 * Licensed under Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0).
 * Free for non-commercial use only. Credit TouchWizard. See https://creativecommons.org/licenses/by-nc/4.0/.
 * Provided "as is," use at your own risk.
 */
package com.example.autocontrol14

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FloatingWindow : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var timerView: View
    private var currentDirectoryUri: Uri? = null
    private lateinit var fileListText: TextView
    private lateinit var statusText: TextView
    private lateinit var timerText: TextView
    private var timerJob: Job? = null
    private lateinit var statusReceiver: BroadcastReceiver
    private lateinit var localBroadcastManager: LocalBroadcastManager

    companion object {
        private const val LICENSE = """
            autocontrol14 - Version 4.0
            Copyright © 2025 Z.chao Zhang
            
            Licensed under Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0):
            - Free to share and adapt for non-commercial use only.
            - Credit Z.chao Zhang and link to https://creativecommons.org/licenses/by-nc/4.0/.
            - Commercial use is prohibited without permission.
            
            THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND. USE AT YOUR OWN RISK.
            Full license: https://creativecommons.org/licenses/by-nc/4.0/
            Contact: tempest0012@gmail.com
        """
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            stopSelf()
            return
        }

        // 初始化悬浮窗
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        windowManager.addView(floatingView, params)

        // 初始化计时器窗口
        timerView = LayoutInflater.from(this).inflate(R.layout.timer_window, null)
        val timerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 400
            y = 0
        }
        windowManager.addView(timerView, timerParams)

        // 初始化控件
        fileListText = floatingView.findViewById(R.id.file_list_text)
        statusText = floatingView.findViewById(R.id.status_text)
        timerText = timerView.findViewById(R.id.timer_text)

        // 设置按钮监听器
        floatingView.findViewById<Button>(R.id.start_button).setOnClickListener {
            if (currentDirectoryUri != null) {
                startPlayback(currentDirectoryUri!!)
            } else {
                updateStatus("未选择目录，使用默认文件")
                startPlaybackFromAssets("touch_recording_3.txt")
            }
        }
        floatingView.findViewById<Button>(R.id.stop_button).setOnClickListener {
            stopPlayback()
        }
        floatingView.findViewById<Button>(R.id.select_button).setOnClickListener { selectDirectory() }

        // 初始化广播接收器
        statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val status = intent?.getStringExtra("STATUS")
                Log.d("FloatingWindow", "Local broadcast received with status: $status")
                if (status != null) {
                    updateStatus(status)
                    Log.d("FloatingWindow", "Status updated to: $status")
                } else {
                    Log.w("FloatingWindow", "Received null status in local broadcast")
                }
            }
        }

        // 使用 LocalBroadcastManager 注册接收器
        localBroadcastManager = LocalBroadcastManager.getInstance(this)
        localBroadcastManager.registerReceiver(statusReceiver, IntentFilter("com.example.autocontrol14.STATUS_UPDATE"))
        Log.d("FloatingWindow", "Local broadcast receiver registered with action: com.example.autocontrol14.STATUS_UPDATE")
        Log.d("FloatingWindow", LICENSE)
        startTimer()
    }

    private fun selectDirectory() {
        val intent = Intent(this, FilePickerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("DIRECTORY_URI")?.let { uriString ->
            currentDirectoryUri = Uri.parse(uriString)
            updateStatus("已选择目录: autocontroltouch")
            fileListText.text = "autocontroltouch"
        }
        return START_STICKY
    }

    private fun startPlayback(directoryUri: Uri) {
        val intent = Intent(this, TouchRecordService::class.java).apply {
            putExtra("ACTION", "PLAYBACK")
            putExtra("DIRECTORY_URI", directoryUri.toString())
        }
        startService(intent)
        Log.d("FloatingWindow", "Started playback for directory: $directoryUri")
    }

    private fun startPlaybackFromAssets(fileName: String) {
        val intent = Intent(this, TouchRecordService::class.java).apply {
            putExtra("ACTION", "PLAYBACK")
            putExtra("FILE_NAME", fileName)
            putExtra("FROM_ASSETS", true)
        }
        startService(intent)
        updateStatus("执行: $fileName (from assets)")
        Log.d("FloatingWindow", "执行: $fileName (from assets)")
    }

    private fun stopPlayback() {
        val intent = Intent(this, TouchRecordService::class.java).apply {
            putExtra("ACTION", "STOP")
        }
        startService(intent)
        Log.d("FloatingWindow", "回放已停止")
    }

    private fun updateStatus(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            statusText.text = message
            Log.d("FloatingWindow", "UI updated with status: $message")
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                for (i in 1..10) {
                    timerText.text = "${i}s"
                    delay(1000)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
        if (::timerView.isInitialized) {
            windowManager.removeView(timerView)
        }
        timerJob?.cancel()
        localBroadcastManager.unregisterReceiver(statusReceiver)
        Log.d("FloatingWindow", "Service destroyed, local broadcast receiver unregistered")
    }
}
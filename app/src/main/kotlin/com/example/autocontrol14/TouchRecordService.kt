/*
 * autocontrol14 - Version 4.0
 * Copyright © 2025 Z.chao Zhang
 * Licensed under Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0).
 * Free for non-commercial use only. Credit TouchWizard. See https://creativecommons.org/licenses/by-nc/4.0/.
 * Provided "as is," use at your own risk.
 */
package com.example.autocontrol14

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class TouchRecordService : Service() {
    private var playbackJob: Job? = null
    private var isPlaying = false

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TouchRecordService", LICENSE)
        val action = intent?.getStringExtra("ACTION")
        Log.d("TouchRecordService", "onStartCommand called with action: $action")
        when (action) {
            "PLAYBACK" -> {
                val directoryUriString = intent.getStringExtra("DIRECTORY_URI")
                val fromAssets = intent.getBooleanExtra("FROM_ASSETS", false)
                if (fromAssets) {
                    val fileName = intent.getStringExtra("FILE_NAME") ?: "touch_recording_3.txt"
                    startPlaybackFromAssets(fileName)
                } else if (directoryUriString != null) {
                    startPlaybackFromDirectory(Uri.parse(directoryUriString))
                } else {
                    Log.w("TouchRecordService", "No directory URI provided for playback")
                }
            }
            "STOP" -> stopPlayback()
        }
        return START_STICKY
    }

    private fun startPlaybackFromAssets(fileName: String) {
        stopPlayback()
        playbackJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                isPlaying = true
                val inputStream = assets.open(fileName)
                playEvents(inputStream, fileName)
            } catch (e: Exception) {
                Log.e("TouchRecordService", "Assets playback error: ${e.message}", e)
            } finally {
                isPlaying = false
            }
        }
    }

    private fun startPlaybackFromDirectory(directoryUri: Uri) {
        stopPlayback()
        playbackJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                isPlaying = true
                val mainFileInfo = getFirstFileInDirectory(directoryUri)
                if (mainFileInfo == null) {
                    Log.e("TouchRecordService", "No files found in directory")
                    sendStatusUpdate("错误: 目录中无文件")
                    return@launch
                }
                val (mainFileUri, mainFileName) = mainFileInfo
                Log.d("TouchRecordService", "Using $mainFileName as main file")
                val inputStream = contentResolver.openInputStream(mainFileUri)
                val mainContent = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                val fileList = parseMainFile(mainContent)
                fileList.forEach { subFile ->
                    if (!isPlaying) return@launch
                    val subFileUri = findFileUri(directoryUri, subFile)
                    if (subFileUri != null) {
                        Log.d("TouchRecordService", "Playing back $subFile from $subFileUri")
                        playSingleFile(subFileUri, subFile)
                    } else {
                        Log.w("TouchRecordService", "Subfile $subFile not found in directory")
                        sendStatusUpdate("错误: 子文件 $subFile 未找到")
                    }
                }
            } catch (e: Exception) {
                Log.e("TouchRecordService", "Playback error: ${e.message}", e)
                sendStatusUpdate("错误: 回放失败")
            } finally {
                isPlaying = false
                sendStatusUpdate("回放已完成")
            }
        }
    }

    private fun parseMainFile(content: String): List<String> {
        val lines = content.lines()
        val fileLines = lines.dropWhile { !it.startsWith("files:") }.drop(1)
        val fileList = fileLines.map { it.trim() }.filter { it.isNotEmpty() }
        Log.d("TouchRecordService", "Parsed files: $fileList")
        return fileList
    }

    private fun getFirstFileInDirectory(directoryUri: Uri): Pair<Uri, String>? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, DocumentsContract.getTreeDocumentId(directoryUri))
        contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            if (cursor.moveToFirst()) {
                val name = cursor.getString(nameIndex)
                val documentId = cursor.getString(idIndex)
                val fileUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentId)
                return fileUri to name
            }
        }
        return null
    }

    private fun findFileUri(directoryUri: Uri, fileName: String): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, DocumentsContract.getTreeDocumentId(directoryUri))
        contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)
                if (name == fileName) {
                    val documentId = cursor.getString(idIndex)
                    return DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentId)
                }
            }
        }
        return null
    }

    private suspend fun playSingleFile(uri: Uri, fileName: String) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val events = parseEvents(inputStream, fileName)
            if (events.isNotEmpty() && isPlaying) {
                sendStatusUpdate("执行: $fileName")
                Log.d("TouchRecordService", "Sending status update: 执行: $fileName")
                dispatchGesture(events)
                val totalDuration = events.maxOfOrNull { it.time }?.minus(events.minOf { it.time })?.times(1000)?.toLong() ?: 1000L
                delay(totalDuration)
                Log.d("TouchRecordService", "Completed playback of $fileName, waited $totalDuration ms")
            } else {
                Log.w("TouchRecordService", "No events loaded from $fileName or playback stopped")
            }
        } catch (e: Exception) {
            Log.e("TouchRecordService", "Error playing $fileName: ${e.message}", e)
            sendStatusUpdate("错误: $fileName 回放失败")
        }
    }

    private fun parseEvents(inputStream: java.io.InputStream?, fileName: String): List<TouchEvent> {
        val events = mutableListOf<TouchEvent>()
        inputStream ?: return events
        val reader = BufferedReader(InputStreamReader(inputStream))
        var lineCount = 0

        reader.useLines { lines ->
            lines.forEach { line ->
                lineCount++
                val parts = line.split(":")
                when (parts.size) {
                    5 -> { // DOWN 事件
                        try {
                            val action = parts[0]
                            val pointerId = parts[1].toInt()
                            val x = parts[2].toFloat()
                            val y = parts[3].toFloat()
                            val time = parts[4].toDouble()
                            if (action == "DOWN") {
                                events.add(TouchEvent(action, pointerId, x, y, time))
                            }
                        } catch (e: Exception) {
                            Log.e("TouchRecordService", "Error parsing DOWN line $lineCount in $fileName: $line, ${e.message}")
                        }
                    }
                    6 -> { // MOVE 或 UP 事件
                        try {
                            val action = parts[0]
                            val pointerId = parts[1].toInt()
                            val x = parts[2].toFloat()
                            val y = parts[3].toFloat()
                            val time = parts[5].toDouble()
                            if (action == "MOVE" || action == "UP") {
                                events.add(TouchEvent(action, pointerId, x, y, time))
                            }
                        } catch (e: Exception) {
                            Log.e("TouchRecordService", "Error parsing MOVE/UP line $lineCount in $fileName: $line, ${e.message}")
                        }
                    }
                    else -> Log.w("TouchRecordService", "Skipping line $lineCount in $fileName with ${parts.size} parts: $line")
                }
            }
        }
        Log.d("TouchRecordService", "Loaded ${events.size} touch events from $fileName, total lines: $lineCount")
        return events
    }

    private fun playEvents(inputStream: java.io.InputStream?, fileName: String) {
        val events = parseEvents(inputStream, fileName)
        if (events.isNotEmpty() && isPlaying) {
            sendStatusUpdate("执行: $fileName")
            Log.d("TouchRecordService", "Sending status update: 执行: $fileName")
            dispatchGesture(events)
        }
    }

    private fun dispatchGesture(events: List<TouchEvent>) {
        val intent = Intent(this, MyAccessibilityService::class.java).apply {
            putExtra("ACTION", "PLAYBACK")
            putParcelableArrayListExtra("EVENTS", ArrayList(events))
        }
        startService(intent)
        Log.d("TouchRecordService", "Service intent sent with ${events.size} events")
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        isPlaying = false
        Log.d("TouchRecordService", "Playback stopped")
        sendStatusUpdate("回放已停止")
        val intent = Intent(this, MyAccessibilityService::class.java).apply {
            putExtra("ACTION", "STOP")
        }
        startService(intent)
    }

    private fun sendStatusUpdate(message: String) {
        val intent = Intent("com.example.autocontrol14.STATUS_UPDATE").apply {
            putExtra("STATUS", message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("TouchRecordService", "Local broadcast sent with status: $message")
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }
}
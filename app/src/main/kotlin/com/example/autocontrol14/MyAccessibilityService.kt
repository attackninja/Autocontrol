/*
 * autocontrol14 - Version 4.0
 * Copyright © 2025 Z.chao Zhang
 * Licensed under Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0).
 * Free for non-commercial use only. Credit TouchWizard. See https://creativecommons.org/licenses/by-nc/4.0/.
 * Provided "as is," use at your own risk.
 */
package com.example.autocontrol14

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MyAccessibilityService : AccessibilityService() {

    private lateinit var replayReceiver: BroadcastReceiver
    private var playbackScope: CoroutineScope? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d("MyAccessibilityService", "Accessibility event: ${event?.eventType}")
    }

    override fun onInterrupt() {
        Log.d("MyAccessibilityService", "Service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("MyAccessibilityService", "Service connected")

        replayReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra("ACTION")) {
                    "STOP" -> {
                        Log.d("MyAccessibilityService", "Received STOP command via broadcast")
                        stopPlayback()
                    }
                    "PLAYBACK" -> {
                        val events = intent.getParcelableArrayListExtra<TouchEvent>("EVENTS")
                        if (events != null) {
                            Log.d("MyAccessibilityService", "Received broadcast with ${events.size} events")
                            performExactGesturePlayback(events)
                        } else {
                            Log.w("MyAccessibilityService", "No events received in broadcast")
                        }
                    }
                    else -> {
                        Log.w("MyAccessibilityService", "Unknown action received in broadcast: ${intent?.getStringExtra("ACTION")}")
                    }
                }
            }
        }
        registerReceiver(replayReceiver, IntentFilter("com.example.autocontrol14.REPLAY_GESTURE"), RECEIVER_NOT_EXPORTED)
        Log.d("MyAccessibilityService", "Broadcast receiver registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.getStringExtra("ACTION")) {
                "PLAYBACK" -> {
                    val events = it.getParcelableArrayListExtra<TouchEvent>("EVENTS")
                    if (events != null) {
                        Log.d("MyAccessibilityService", "Received service intent with ${events.size} events")
                        performExactGesturePlayback(events)
                    } else {
                        Log.w("MyAccessibilityService", "No events received in service intent")
                    }
                }
                "STOP" -> {
                    Log.d("MyAccessibilityService", "Received STOP command via service intent")
                    stopPlayback()
                }
                else -> {
                    Log.w("MyAccessibilityService", "Unknown action received in service intent: ${it.getStringExtra("ACTION")}")
                }
            }
        } ?: Log.w("MyAccessibilityService", "onStartCommand: intent is null")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(replayReceiver)
        stopPlayback()
        Log.d("MyAccessibilityService", "Service destroyed")
    }

    private fun performExactGesturePlayback(events: List<TouchEvent>) {
        if (events.isEmpty()) {
            Log.w("MyAccessibilityService", "No events to playback")
            return
        }
        stopPlayback()
        playbackScope = CoroutineScope(Dispatchers.Main)
        playbackScope?.launch {
            Log.d("MyAccessibilityService", "Starting gesture playback")
            val gesturesById = mutableMapOf<Int, MutableList<MutableList<TouchEvent>>>()

            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels.toFloat()
            val screenHeight = displayMetrics.heightPixels.toFloat()
            Log.d("MyAccessibilityService", "Screen size: ${screenWidth}x${screenHeight}")

            events.forEach { event ->
                val gestureLists = gesturesById.getOrPut(event.pointerId) { mutableListOf() }
                val currentGesture = gestureLists.lastOrNull { it.isNotEmpty() && it.last().action != "UP" }
                when (event.action) {
                    "DOWN" -> gestureLists.add(mutableListOf(event))
                    "MOVE", "UP" -> currentGesture?.add(event)
                }
            }

            val baseTime = events.first().time

            gesturesById.forEach { (pointerId, gestureLists) ->
                gestureLists.forEachIndexed { index, gesture ->
                    if (gesture.isEmpty() || gesture.first().action != "DOWN") {
                        Log.w("MyAccessibilityService", "Invalid gesture $index for id=$pointerId")
                        return@forEachIndexed
                    }

                    val startEvent = gesture.first()
                    val endEvent = gesture.last()
                    val path = Path()
                    val clampedStartX = startEvent.x.coerceIn(0f, screenWidth)
                    val clampedStartY = startEvent.y.coerceIn(0f, screenHeight)
                    path.moveTo(clampedStartX, clampedStartY)

                    gesture.filter { it.action == "MOVE" }.forEach { moveEvent ->
                        val clampedX = moveEvent.x.coerceIn(0f, screenWidth)
                        val clampedY = moveEvent.y.coerceIn(0f, screenHeight)
                        path.lineTo(clampedX, clampedY)
                    }
                    val clampedEndX = endEvent.x.coerceIn(0f, screenWidth)
                    val clampedEndY = endEvent.y.coerceIn(0f, screenHeight)
                    path.lineTo(clampedEndX, clampedEndY)

                    val duration = ((endEvent.time - startEvent.time) * 1000).toLong()
                    Log.d("MyAccessibilityService", "Gesture $index for id=$pointerId: from ($clampedStartX, $clampedStartY) to ($clampedEndX, $clampedEndY), duration=$duration ms")

                    val strokes = mutableListOf<GestureDescription.StrokeDescription>()
                    if (duration > 10000) {
                        val segmentDuration = 10000L
                        var currentTime = 0L
                        var segmentPath = Path()
                        segmentPath.moveTo(clampedStartX, clampedStartY)

                        gesture.forEach { event ->
                            val eventTime = ((event.time - startEvent.time) * 1000).toLong()
                            val clampedX = event.x.coerceIn(0f, screenWidth)
                            val clampedY = event.y.coerceIn(0f, screenHeight)

                            if (eventTime > currentTime + segmentDuration) {
                                strokes.add(GestureDescription.StrokeDescription(segmentPath, 0, segmentDuration))
                                currentTime += segmentDuration
                                segmentPath = Path()
                                segmentPath.moveTo(clampedX, clampedY)
                            }
                            segmentPath.lineTo(clampedX, clampedY)
                        }
                        val remainingDuration = duration - currentTime
                        if (remainingDuration > 0) {
                            strokes.add(GestureDescription.StrokeDescription(segmentPath, 0, remainingDuration))
                        }
                    } else {
                        strokes.add(GestureDescription.StrokeDescription(path, 0, duration))
                    }

                    val startDelay = ((startEvent.time - baseTime) * 1000).toLong()
                    if (startDelay > 0) {
                        Log.d("MyAccessibilityService", "Delaying $startDelay ms before gesture $index for id=$pointerId")
                        delay(startDelay)
                    }

                    val builder = GestureDescription.Builder()
                    strokes.forEach { builder.addStroke(it) }
                    val gestureDesc = builder.build()

                    Log.d("MyAccessibilityService", "Dispatching gesture $index for id=$pointerId")
                    dispatchGesture(gestureDesc, object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            Log.d("MyAccessibilityService", "Gesture $index for id=$pointerId completed")
                        }
                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            Log.w("MyAccessibilityService", "Gesture $index for id=$pointerId cancelled")
                        }
                    }, null)
                }
            }
            Log.d("MyAccessibilityService", "Gesture playback finished")
        }
    }

    private fun stopPlayback() {
        playbackScope?.cancel("Stopped by user")
        playbackScope = null
        Log.d("MyAccessibilityService", "Playback stopped")
    }
}
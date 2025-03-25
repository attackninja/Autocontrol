/*
 * autocontrol14 - Version 4.0
 * Copyright © 2025 Z.chao Zhang
 * Licensed under Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0).
 * Free for non-commercial use only. Credit TouchWizard. See https://creativecommons.org/licenses/by-nc/4.0/.
 * Provided "as is," use at your own risk.
 */
package com.example.autocontrol14

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } else {
            startService(Intent(this, FloatingWindow::class.java))
            finish()
        }
    }
}

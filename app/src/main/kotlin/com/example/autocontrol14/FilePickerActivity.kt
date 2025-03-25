/*
 * autocontrol14 - Version 4.0
 * Copyright © 2025 Z.chao Zhang
 * Licensed under Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0).
 * Free for non-commercial use only. Credit TouchWizard. See https://creativecommons.org/licenses/by-nc/4.0/.
 * Provided "as is," use at your own risk.
 */
package com.example.autocontrol14

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class FilePickerActivity : AppCompatActivity() {
    companion object {
        private const val SELECT_DIRECTORY_REQUEST = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, SELECT_DIRECTORY_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SELECT_DIRECTORY_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val intent = Intent(this, FloatingWindow::class.java).apply {
                    putExtra("DIRECTORY_URI", uri.toString())
                }
                startService(intent)
            }
        }
        finish()
    }
}
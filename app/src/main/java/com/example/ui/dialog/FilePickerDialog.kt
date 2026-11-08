package com.example.ui.dialog

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.leanback.widget.VerticalGridView
import com.example.R
import com.example.ui.adapter.FilePickerAdapter
import com.example.ui.leanback.LeanbackUtils
import java.io.File

class FilePickerDialog(
    private val context: Context,
    private val onFileSelected: (File) -> Unit
) {

    private val dialog: Dialog = Dialog(context, R.style.Theme_ZenTV_Dialog)
    private var currentDir: File
    private val adapter = FilePickerAdapter { file ->
        if (file.isDirectory) {
            navigateTo(file)
        } else {
            dialog.dismiss()
            onFileSelected(file)
        }
    }

    private lateinit var tvPath: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var rvFiles: VerticalGridView

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_file_picker, null)
        dialog.setContentView(view)

        tvPath = view.findViewById(R.id.tv_current_path)
        tvEmpty = view.findViewById(R.id.tv_empty_folder)
        rvFiles = view.findViewById(R.id.rv_file_list)

        LeanbackUtils.setupVerticalGrid(rvFiles, numColumns = 1)
        rvFiles.adapter = adapter

        val btnUp: Button = view.findViewById(R.id.btn_folder_up)
        val btnDownloads: Button = view.findViewById(R.id.btn_quick_downloads)
        val btnStorage: Button = view.findViewById(R.id.btn_quick_storage)
        val btnCancel: Button = view.findViewById(R.id.btn_picker_cancel)

        LeanbackUtils.applyFocusHighlight(btnUp, scale = 1.05f)
        LeanbackUtils.applyFocusHighlight(btnDownloads, scale = 1.05f)
        LeanbackUtils.applyFocusHighlight(btnStorage, scale = 1.05f)
        LeanbackUtils.applyFocusHighlight(btnCancel, scale = 1.05f)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnUp.setOnClickListener {
            val parent = currentDir.parentFile
            if (parent != null && parent.canRead()) {
                navigateTo(parent)
            }
        }

        btnDownloads.setOnClickListener {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloads != null && downloads.exists()) {
                navigateTo(downloads)
            } else {
                navigateTo(File("/sdcard/Download"))
            }
        }

        btnStorage.setOnClickListener {
            val storage = Environment.getExternalStorageDirectory()
            if (storage != null && storage.exists()) {
                navigateTo(storage)
            } else {
                navigateTo(File("/sdcard"))
            }
        }

        // Determine starting directory
        val initialDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        currentDir = when {
            initialDownloads != null && initialDownloads.exists() && initialDownloads.canRead() -> initialDownloads
            File("/sdcard/Download").exists() -> File("/sdcard/Download")
            File("/sdcard").exists() -> File("/sdcard")
            else -> context.filesDir
        }
    }

    fun show() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val readPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            if (readPerm != PackageManager.PERMISSION_GRANTED && context is Activity) {
                ActivityCompat.requestPermissions(
                    context,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1001
                )
            }
        }
        dialog.show()
        navigateTo(currentDir)
    }

    private fun navigateTo(dir: File) {
        currentDir = dir
        tvPath.text = dir.absolutePath

        // Show all files (excluding hidden dotfiles)
        val files = dir.listFiles { f ->
            !f.isHidden
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()

        if (files.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvFiles.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvFiles.visibility = View.VISIBLE
            adapter.submitList(files)
        }
    }
}

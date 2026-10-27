package com.example.ui.dialog

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.example.R
import java.io.File

class AddPlaylistDialog(
    private val context: Context,
    private val onAddUrl: (name: String, url: String) -> Unit,
    private val onAddFile: (name: String, file: File) -> Unit
) {

    private val dialog: Dialog = Dialog(context, R.style.Theme_ZenTV_Dialog)

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_playlist, null)
        dialog.setContentView(view)

        val etName: EditText = view.findViewById(R.id.et_dialog_playlist_name)
        val etUrl: EditText = view.findViewById(R.id.et_dialog_playlist_url)
        val btnBrowse: Button = view.findViewById(R.id.btn_dialog_browse_file)
        val btnCancel: Button = view.findViewById(R.id.btn_dialog_cancel)
        val btnAddUrl: Button = view.findViewById(R.id.btn_dialog_add_url)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnAddUrl.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(context, "Please enter a valid M3U stream URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val name = etName.text.toString().trim().ifEmpty {
                try {
                    val uri = android.net.Uri.parse(url)
                    uri.lastPathSegment?.removeSuffix(".m3u")?.removeSuffix(".m3u8")?.ifEmpty { "IPTV Playlist" } ?: "IPTV Playlist"
                } catch (_: Exception) {
                    "IPTV Playlist"
                }
            }
            dialog.dismiss()
            onAddUrl(name, url)
        }

        btnBrowse.setOnClickListener {
            dialog.dismiss()
            FilePickerDialog(context) { selectedFile ->
                val name = etName.text.toString().trim().ifEmpty {
                    selectedFile.nameWithoutExtension.ifEmpty { "Local Playlist" }
                }
                onAddFile(name, selectedFile)
            }.show()
        }
    }

    fun show() {
        dialog.show()
    }
}

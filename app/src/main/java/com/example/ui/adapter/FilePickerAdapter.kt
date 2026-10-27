package com.example.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import java.io.File

class FilePickerAdapter(
    private val onFileClick: (File) -> Unit
) : RecyclerView.Adapter<FilePickerAdapter.FileViewHolder>() {

    private val items = mutableListOf<File>()

    fun submitList(newList: List<File>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file_row, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.iv_file_icon)
        private val tvName: TextView = itemView.findViewById(R.id.tv_file_name)
        private val tvSub: TextView = itemView.findViewById(R.id.tv_file_sub)

        fun bind(file: File) {
            tvName.text = file.name

            if (file.isDirectory) {
                ivIcon.setImageResource(R.drawable.ic_folder)
                val count = file.listFiles()?.size ?: 0
                tvSub.text = "Directory • $count items"
            } else {
                ivIcon.setImageResource(R.drawable.ic_file)
                val kb = (file.length() / 1024).coerceAtLeast(1)
                tvSub.text = "M3U File • ${kb} KB"
            }

            itemView.setOnClickListener {
                onFileClick(file)
            }
        }
    }
}

package com.phinmaed.uplug

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SectionHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val title: TextView = view.findViewById(R.id.sectionHeaderText)
}
package com.phinmaed.uplug

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

class NewsAdapter(
    private val items: List<NewsListItem>,
    private val onPostClick: (Post) -> Unit,
    private val onAuthorClick: (Post) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SECTION = 0
        private const val TYPE_MONTH = 1
        private const val TYPE_POST = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is NewsListItem.SectionHeader -> TYPE_SECTION
            is NewsListItem.MonthHeader -> TYPE_MONTH
            is NewsListItem.PostRow -> TYPE_POST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SECTION -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_news_section_header, parent, false)
                SectionHeaderViewHolder(view)
            }

            TYPE_MONTH -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_month_header, parent, false)
                MonthHeaderViewHolder(view)
            }

            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.home_post_item, parent, false)
                PostAdapter.PostViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is NewsListItem.SectionHeader -> {
                (holder as SectionHeaderViewHolder).title.text = item.title
            }

            is NewsListItem.MonthHeader -> {
                (holder as MonthHeaderViewHolder).title.text = item.title
            }

            is NewsListItem.PostRow -> {
                val post = item.post
                val postHolder = holder as PostAdapter.PostViewHolder

                postHolder.userName.text = post.authorName
                postHolder.postWall.text = post.wall.uppercase()
                postHolder.postTime.text = formatTime(post.createdAt)
                postHolder.postTitle.text = post.title
                postHolder.postContent.text = post.content
                postHolder.pinIcon.visibility = if (post.pinned) View.VISIBLE else View.GONE

                when (post.wall.lowercase()) {
                    "official", "cite" -> postHolder.wallIcon.setImageResource(R.drawable.cite)
                    else -> postHolder.wallIcon.setImageResource(R.drawable.profile)
                }

                Glide.with(postHolder.userProfilePic.context)
                    .load(post.photoUrl)
                    .placeholder(R.drawable.profile)
                    .error(R.drawable.profile)
                    .circleCrop()
                    .into(postHolder.userProfilePic)

                postHolder.authorClickableArea.setOnClickListener {
                    onAuthorClick(post)
                }

                postHolder.itemView.setOnClickListener {
                    onPostClick(post)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class SectionHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.sectionHeaderText)
    }

    class MonthHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.monthHeaderText)
    }

    private fun formatTime(timestamp: Timestamp?): String {
        if (timestamp == null) return ""

        val now = System.currentTimeMillis()
        val postTime = timestamp.toDate().time
        val diff = now - postTime

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "${seconds}s"
            minutes < 60 -> "${minutes}m"
            hours < 24 -> "${hours}h"
            days == 1L -> "Yesterday"
            days < 7 -> "${days}d"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(timestamp.toDate())
        }
    }
}
package com.phinmaed.uplug

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

class PinnedAdapter(
    posts: List<Post>,
    private val onClick: (Post) -> Unit,
    private val onAuthorClick: (Post) -> Unit = {}
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private val pinnedPosts = posts.filter { it.pinned }

    fun getRealCount(): Int = pinnedPosts.size

    fun getRealPosition(position: Int): Int {
        if (pinnedPosts.isEmpty()) return 0
        return position % pinnedPosts.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostAdapter.PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.home_post_item, parent, false)

        view.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        return PostAdapter.PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostAdapter.PostViewHolder, position: Int) {
        if (pinnedPosts.isEmpty()) return

        val realPosition = position % pinnedPosts.size
        val post = pinnedPosts[realPosition]

        holder.userName.text = post.authorName
        holder.postWall.text = post.wall.uppercase()
        holder.postTime.text = formatTime(post.createdAt)
        holder.postTitle.text = post.title
        holder.postContent.text = post.content

        when (post.wall.lowercase()) {
            "official", "cite" -> holder.wallIcon.setImageResource(R.drawable.cite)
            else -> holder.wallIcon.setImageResource(R.drawable.profile)
        }

        loadAuthorPhoto(post.photoUrl, holder.userProfilePic)

        holder.authorClickableArea.setOnClickListener {
            onAuthorClick(post)
        }

        holder.itemView.setOnClickListener {
            onClick(post)
        }
    }

    override fun getItemCount(): Int {
        return when {
            pinnedPosts.isEmpty() -> 0
            pinnedPosts.size == 1 -> 1
            else -> Int.MAX_VALUE
        }
    }

    private fun loadAuthorPhoto(photoUrl: String?, imageView: ImageView) {
        if (photoUrl.isNullOrBlank()) {
            Glide.with(imageView.context)
                .load(R.drawable.profile)
                .circleCrop()
                .into(imageView)
            return
        }

        Glide.with(imageView.context)
            .load(photoUrl)
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .circleCrop()
            .into(imageView)
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
            else -> {
                val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
                sdf.format(timestamp.toDate())
            }
        }
    }
}
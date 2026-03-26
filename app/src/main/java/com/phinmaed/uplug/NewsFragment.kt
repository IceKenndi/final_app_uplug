package com.phinmaed.uplug

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class NewsFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var newsRecycler: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var emptyStateText: TextView
    private lateinit var emptyStateContainer: LinearLayout
    private lateinit var newsSkeleton: LinearLayout

    private lateinit var filterOfficial: TextView
    private lateinit var filterDepartment: TextView

    private lateinit var btnCreatePost: TextView
    private lateinit var createPostCard: CardView
    private lateinit var btnWallOfficial: TextView
    private lateinit var btnWallDepartment: TextView

    private lateinit var inputPostTitle: EditText
    private lateinit var inputPostContent: EditText
    private lateinit var btnSubmitPost: TextView

    private var departmentWall: String = ""
    private var currentFilter: String = "official"

    private val items = mutableListOf<NewsListItem>()

    private var currentRole: String = ""
    private var canPostDept: Boolean = false
    private var selectedCreateWall: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_news, container, false)

        newsRecycler = view.findViewById(R.id.newsRecycler)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        emptyStateText = view.findViewById(R.id.emptyStateText)
        emptyStateContainer = view.findViewById(R.id.emptyStateContainer)
        newsSkeleton = view.findViewById(R.id.newsSkeleton)

        filterOfficial = view.findViewById(R.id.filterOfficial)
        filterDepartment = view.findViewById(R.id.filterDepartment)

        btnCreatePost = view.findViewById(R.id.btnCreatePost)
        createPostCard = view.findViewById(R.id.createPostCard)
        btnWallOfficial = view.findViewById(R.id.btnWallOfficial)
        btnWallDepartment = view.findViewById(R.id.btnWallDepartment)

        inputPostTitle = view.findViewById(R.id.inputPostTitle)
        inputPostContent = view.findViewById(R.id.inputPostContent)
        btnSubmitPost = view.findViewById(R.id.btnSubmitPost)

        newsRecycler.layoutManager = LinearLayoutManager(requireContext())
        newsRecycler.isNestedScrollingEnabled = true

        filterOfficial.setOnClickListener {
            currentFilter = "official"
            updateFilterUi()
            resetCreatePostChip()
            loadPosts()
        }

        filterDepartment.setOnClickListener {
            currentFilter = "department"
            updateFilterUi()
            resetCreatePostChip()
            loadPosts()
        }

        swipeRefresh.setOnRefreshListener {
            loadPosts()
        }

        btnCreatePost.setOnClickListener {
            if (!canUseCreatePost()) {
                toast("You don’t have permission to create posts.")
                return@setOnClickListener
            }

            setChipState(filterOfficial, false)
            setChipState(filterDepartment, false)
            setChipState(btnCreatePost, true)

            if (canPostOfficial() && canPostDepartment()) {
                selectedCreateWall = if (currentFilter == "department") departmentWall else "official"
                setChipState(btnWallOfficial, selectedCreateWall == "official")
                setChipState(btnWallDepartment, selectedCreateWall == departmentWall)
            } else if (canPostDepartment()) {
                selectedCreateWall = departmentWall
                setChipState(btnWallOfficial, false)
                setChipState(btnWallDepartment, true)
            }

            createPostCard.visibility = View.VISIBLE
            swipeRefresh.visibility = View.GONE
        }

        btnWallOfficial.setOnClickListener {
            if (!canPostOfficial()) {
                toast("Only faculty can post on the official wall.")
                return@setOnClickListener
            }

            selectedCreateWall = "official"
            setChipState(btnWallOfficial, true)
            setChipState(btnWallDepartment, false)
        }

        btnWallDepartment.setOnClickListener {
            if (!canPostDepartment()) {
                toast("You don’t have permission to post on the department wall.")
                return@setOnClickListener
            }

            selectedCreateWall = departmentWall
            setChipState(btnWallDepartment, true)
            setChipState(btnWallOfficial, false)
        }

        btnSubmitPost.setOnClickListener {
            submitPost()
        }

        (activity as? MainActivity)?.pendingNewsFilterFromHome?.let { incomingFilter ->
            currentFilter = incomingFilter
            (activity as? MainActivity)?.pendingNewsFilterFromHome = null
        }

        updateFilterUi()
        loadUserDepartment()

        return view
    }

    private fun showSkeleton() {
        newsSkeleton.visibility = View.VISIBLE
        newsRecycler.visibility = View.GONE
        emptyStateContainer.visibility = View.GONE
        swipeRefresh.visibility = View.VISIBLE
    }

    private fun showContent() {
        newsSkeleton.visibility = View.GONE
        emptyStateContainer.visibility = View.GONE
        newsRecycler.visibility = View.VISIBLE
        swipeRefresh.visibility = View.VISIBLE
    }

    private fun showEmptyState(message: String = "No posts found.") {
        emptyStateText.text = message
        newsSkeleton.visibility = View.GONE
        newsRecycler.visibility = View.GONE
        emptyStateContainer.visibility = View.VISIBLE
        swipeRefresh.visibility = View.VISIBLE
    }

    private fun setChipState(chip: TextView, selected: Boolean) {
        chip.setBackgroundResource(
            if (selected) R.drawable.filter_chip_selected
            else R.drawable.filter_chip_unselected
        )
        chip.setTextColor(if (selected) Color.WHITE else Color.parseColor("#333333"))
    }

    private fun resetCreatePostChip() {
        setChipState(btnCreatePost, false)
        createPostCard.visibility = View.GONE
        swipeRefresh.visibility = View.VISIBLE
    }

    private fun updateFilterUi() {
        setChipState(filterOfficial, currentFilter == "official")
        setChipState(filterDepartment, currentFilter == "department")
    }

    private fun loadUserDepartment() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                departmentWall = doc.getString("deptWall")?.trim()?.lowercase().orEmpty()
                currentRole = doc.getString("role")?.trim()?.lowercase().orEmpty()
                canPostDept = doc.getBoolean("canPostDept") == true

                updateCreatePostAccess()
                loadPosts()
            }
            .addOnFailureListener {
                swipeRefresh.isRefreshing = false
                showEmptyState("Couldn’t load posts.")
            }
    }

    private fun canPostOfficial(): Boolean {
        return canPostDept && currentRole == "faculty"
    }

    private fun canPostDepartment(): Boolean {
        return canPostDept && departmentWall.isNotBlank()
    }

    private fun canUseCreatePost(): Boolean {
        return canPostDept
    }

    private fun updateCreatePostAccess() {
        val canCreate = canUseCreatePost()

        btnCreatePost.visibility = if (canCreate) View.VISIBLE else View.GONE
        createPostCard.visibility = View.GONE

        btnWallOfficial.visibility = if (canPostOfficial()) View.VISIBLE else View.GONE
        btnWallDepartment.visibility = if (canPostDepartment()) View.VISIBLE else View.GONE

        when {
            canPostOfficial() && canPostDepartment() -> {
                selectedCreateWall = if (currentFilter == "department") departmentWall else "official"
                setChipState(btnWallOfficial, selectedCreateWall == "official")
                setChipState(btnWallDepartment, selectedCreateWall == departmentWall)
            }

            canPostDepartment() -> {
                selectedCreateWall = departmentWall
                setChipState(btnWallOfficial, false)
                setChipState(btnWallDepartment, true)
            }

            else -> {
                selectedCreateWall = ""
                setChipState(btnWallOfficial, false)
                setChipState(btnWallDepartment, false)
            }
        }
    }

    private fun submitPost() {
        val me = auth.currentUser
        if (me == null) {
            toast("Please sign in again.")
            return
        }

        val title = inputPostTitle.text?.toString()?.trim().orEmpty()
        val content = inputPostContent.text?.toString()?.trim().orEmpty()
        val targetWall = selectedCreateWall.trim().lowercase()

        if (title.isBlank()) {
            toast("Please enter a title.")
            return
        }

        if (content.isBlank()) {
            toast("Please enter content.")
            return
        }

        if (targetWall.isBlank()) {
            toast("Please choose a wall.")
            return
        }

        if (targetWall == "official" && !canPostOfficial()) {
            toast("Only faculty can post on the official wall.")
            return
        }

        if (targetWall != "official" && (!canPostDepartment() || targetWall != departmentWall)) {
            toast("You don’t have permission to post on the department wall.")
            return
        }

        btnSubmitPost.isEnabled = false
        btnSubmitPost.alpha = 0.7f
        btnSubmitPost.text = "Posting..."

        val postData = hashMapOf(
            "align" to "left",
            "authorEmail" to (me.email ?: ""),
            "authorName" to (me.displayName ?: ""),
            "authorUid" to me.uid,
            "content" to content,
            "createdAt" to FieldValue.serverTimestamp(),
            "pinned" to false,
            "pinnedAt" to null,
            "title" to title,
            "wall" to targetWall
        )

        db.collection("posts")
            .add(postData)
            .addOnSuccessListener {
                btnSubmitPost.isEnabled = true
                btnSubmitPost.alpha = 1f
                btnSubmitPost.text = "Post"
                toast("Post created.")
                inputPostTitle.setText("")
                inputPostContent.setText("")
                resetCreatePostChip()
                loadPosts()
            }
            .addOnFailureListener { e ->
                toast("Failed to create post: ${e.message}")
            }
            .addOnCompleteListener {
                btnSubmitPost.isEnabled = true
            }
    }

    private fun loadPosts() {
        val wall = if (currentFilter == "official") "official" else departmentWall

        if (wall.isBlank()) {
            items.clear()
            bindItems()
            showEmptyState("No posts found.")
            swipeRefresh.isRefreshing = false
            return
        }

        if (!swipeRefresh.isRefreshing) {
            showSkeleton()
        }
        swipeRefresh.isRefreshing = true

        val query = db.collection("posts")
            .whereEqualTo("wall", wall)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(80)

        query.get()
            .addOnSuccessListener { snap ->
                val rawPosts = snap.documents.mapNotNull { it.toPost() }

                attachAuthorProfilePhotos(rawPosts) { hydratedPosts ->
                    buildFlatNewsItems(hydratedPosts)
                    bindItems()

                    if (items.isEmpty()) {
                        showEmptyState("No posts found.")
                    } else {
                        showContent()
                    }

                    swipeRefresh.isRefreshing = false
                }
            }
            .addOnFailureListener {
                items.clear()
                bindItems()
                showEmptyState("Couldn’t load posts.")
                swipeRefresh.isRefreshing = false
            }
    }

    private fun buildFlatNewsItems(posts: List<Post>) {
        items.clear()

        val pinnedPosts = posts
            .filter { it.pinned }
            .sortedByDescending {
                it.pinnedAt?.toDate()?.time
                    ?: it.createdAt?.toDate()?.time
                    ?: 0L
            }

        val regularPosts = posts
            .filter { !it.pinned }
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }

        if (pinnedPosts.isNotEmpty()) {
            items.add(NewsListItem.SectionHeader("Pinned"))
            pinnedPosts.forEach { post ->
                items.add(NewsListItem.PostRow(post))
            }
        }

        if (regularPosts.isNotEmpty()) {
            items.add(NewsListItem.SectionHeader("Posts"))

            val monthFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            var lastMonthKey: String? = null

            regularPosts.forEach { post ->
                val monthKey = post.createdAt?.toDate()?.let { monthFormatter.format(it) } ?: "Unknown"

                if (monthKey != lastMonthKey) {
                    items.add(NewsListItem.MonthHeader(monthKey))
                    lastMonthKey = monthKey
                }

                items.add(NewsListItem.PostRow(post))
            }
        }
    }

    private fun bindItems() {
        newsRecycler.adapter = NewsAdapter(
            items = items,
            onPostClick = { openFullPost(it) },
            onAuthorClick = { showPublicProfileDialog(it) }
        )
    }

    private fun attachAuthorProfilePhotos(posts: List<Post>, onDone: (List<Post>) -> Unit) {
        val uniqueUids = posts.mapNotNull { it.authorUid.takeIf { uid -> uid.isNotBlank() } }.distinct()

        if (uniqueUids.isEmpty()) {
            onDone(posts)
            return
        }

        val tasks = uniqueUids.map { uid ->
            db.collection("publicProfiles").document(uid).get()
        }

        Tasks.whenAllComplete(tasks)
            .addOnSuccessListener { results ->
                val photoMap = mutableMapOf<String, String>()

                for (i in results.indices) {
                    val task = results[i]
                    val uid = uniqueUids[i]

                    if (task.isSuccessful) {
                        val doc = task.result as? DocumentSnapshot
                        if (doc != null && doc.exists()) {
                            photoMap[uid] = doc.getString("photoURL") ?: ""
                        }
                    }
                }

                val updated = posts.map { post ->
                    post.copy(photoUrl = photoMap[post.authorUid] ?: "")
                }

                onDone(updated)
            }
            .addOnFailureListener {
                onDone(posts)
            }
    }

    private fun DocumentSnapshot.toPost(): Post? {
        return toObject(Post::class.java)?.copy(id = id)
    }

    private fun openFullPost(post: Post) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = layoutInflater.inflate(R.layout.home_post_item, null)
        val holder = PostAdapter.PostViewHolder(view)

        holder.userName.text = post.authorName
        holder.postWall.text = post.wall.uppercase()
        holder.postTitle.text = post.title
        holder.postContent.text = post.content
        holder.postTime.text = formatTime(post.createdAt)
        holder.pinIcon.visibility = if (post.pinned) View.VISIBLE else View.GONE

        Glide.with(requireContext())
            .load(post.photoUrl)
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .circleCrop()
            .into(holder.userProfilePic)

        when (post.wall.lowercase()) {
            "official", "cite" -> holder.wallIcon.setImageResource(R.drawable.cite)
            else -> holder.wallIcon.setImageResource(R.drawable.profile)
        }

        holder.authorClickableArea.setOnClickListener {
            dialog.dismiss()
            showPublicProfileDialog(post)
        }

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showPublicProfileDialog(post: Post) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_public_profile)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val profileImage = dialog.findViewById<ImageView>(R.id.dialogProfileImage)
        val profileName = dialog.findViewById<TextView>(R.id.dialogProfileName)
        val profileSubtitle = dialog.findViewById<TextView>(R.id.dialogProfileSubtitle)
        val btnMessage = dialog.findViewById<TextView>(R.id.btnMessage)
        val btnClose = dialog.findViewById<TextView>(R.id.btnClose)

        profileName.text = post.authorName
        profileSubtitle.text = "PHINMA User"

        Glide.with(requireContext())
            .load(post.photoUrl)
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .circleCrop()
            .into(profileImage)

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        btnMessage.setOnClickListener {
            dialog.dismiss()
            createOrOpenThread(post)
        }

        dialog.show()

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun createOrOpenThread(post: Post) {
        val myUid = auth.currentUser?.uid
        val otherUid = post.authorUid

        if (myUid.isNullOrBlank() || otherUid.isBlank() || myUid == otherUid) return

        val threadId = buildThreadId(myUid, otherUid)

        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("threadId", threadId)
            putExtra("otherUid", otherUid)
            putExtra("otherUserName", post.authorName)
            putExtra("otherUserPhotoUrl", post.photoUrl)
        }
        startActivity(intent)
    }

    private fun buildThreadId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
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

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
package com.phinmaed.uplug

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PeopleAdapter
    private lateinit var searchBar: EditText
    private lateinit var emptyChatsText: TextView
    private lateinit var emptyChatsContainer: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var filterRecent: TextView
    private lateinit var filterFaculty: TextView

    private val recentPeople = mutableListOf<PersonItem>()
    private val facultyPeople = mutableListOf<PersonItem>()
    private val displayItems = mutableListOf<PersonItem>()

    private val db by lazy { FirebaseFirestore.getInstance() }
    private var currentFilter = "recent"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_chats, container, false)

        recyclerView = view.findViewById(R.id.contactsRecyclerView)
        searchBar = view.findViewById(R.id.searchBar)
        emptyChatsText = view.findViewById(R.id.emptyChatsText)
        emptyChatsContainer = view.findViewById(R.id.emptyChatsContainer)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        filterRecent = view.findViewById(R.id.filterRecent)
        filterFaculty = view.findViewById(R.id.filterFaculty)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = PeopleAdapter(displayItems) { person -> openChat(person) }
        recyclerView.adapter = adapter

        filterRecent.setOnClickListener {
            currentFilter = "recent"
            updateFilterUi()
            rebuildDisplayItems(searchBar.text?.toString().orEmpty())
        }

        filterFaculty.setOnClickListener {
            currentFilter = "faculty"
            updateFilterUi()
            rebuildDisplayItems(searchBar.text?.toString().orEmpty())
        }

        swipeRefresh.setOnRefreshListener {
            loadAllChatSources()
        }

        setupSearch()
        updateFilterUi()
        loadMyProfilePic()
        loadAllChatSources()

        return view
    }

    override fun onResume() {
        super.onResume()
        swipeRefresh.isRefreshing = true
        loadAllChatSources()
    }

    private fun loadMyProfilePic() {
        val myUid = FirebaseAuth.getInstance().uid ?: return

        db.collection("publicProfiles").document(myUid).get()
            .addOnSuccessListener { doc ->
                val photoUrl = doc.getString("photoURL") ?: ""

            }
    }

    private fun loadAllChatSources() {
        swipeRefresh.isRefreshing = true

        loadRecentThreads(
            onDone = {
                loadFacultyDirectory(
                    onDone = {
                        rebuildDisplayItems(searchBar.text?.toString().orEmpty())
                        swipeRefresh.isRefreshing = false
                    },
                    onFail = {
                        rebuildDisplayItems(searchBar.text?.toString().orEmpty())
                        swipeRefresh.isRefreshing = false
                    }
                )
            },
            onFail = {
                loadFacultyDirectory(
                    onDone = {
                        rebuildDisplayItems(searchBar.text?.toString().orEmpty())
                        swipeRefresh.isRefreshing = false
                    },
                    onFail = {
                        rebuildDisplayItems(searchBar.text?.toString().orEmpty())
                        swipeRefresh.isRefreshing = false
                    }
                )
            }
        )
    }

    private fun loadRecentThreads(onDone: () -> Unit, onFail: () -> Unit) {
        val myUid = FirebaseAuth.getInstance().uid ?: run {
            onFail()
            return
        }

        db.collection("threads")
            .whereArrayContains("participants", myUid)
            .get()
            .addOnSuccessListener { threadSnap ->
                val threadItems = mutableListOf<ThreadTemp>()

                for (doc in threadSnap.documents) {
                    val participants = doc.get("participants") as? List<*>
                    val lastMessage = doc.getString("lastMessage") ?: ""
                    val updatedAt = doc.getTimestamp("updatedAt")

                    if (participants != null) {
                        val otherUid = participants
                            .mapNotNull { it as? String }
                            .firstOrNull { it != myUid }

                        if (!otherUid.isNullOrBlank()) {
                            threadItems.add(
                                ThreadTemp(
                                    threadId = doc.id,
                                    otherUid = otherUid,
                                    lastMessage = lastMessage,
                                    updatedAt = updatedAt
                                )
                            )
                        }
                    }
                }

                if (threadItems.isEmpty()) {
                    recentPeople.clear()
                    onDone()
                    return@addOnSuccessListener
                }

                loadProfilesForRecent(threadItems, onDone, onFail)
            }
            .addOnFailureListener {
                onFail()
            }
    }

    private fun loadProfilesForRecent(
        threadItems: List<ThreadTemp>,
        onDone: () -> Unit,
        onFail: () -> Unit
    ) {
        val tasks = threadItems.map { thread ->
            db.collection("publicProfiles").document(thread.otherUid).get()
        }

        Tasks.whenAllComplete(tasks)
            .addOnSuccessListener { results ->
                recentPeople.clear()

                for (i in results.indices) {
                    val task = results[i]
                    val thread = threadItems[i]

                    if (task.isSuccessful) {
                        val doc = task.result as? DocumentSnapshot
                        if (doc != null && doc.exists()) {
                            recentPeople.add(
                                PersonItem(
                                    uid = thread.otherUid,
                                    displayName = doc.getString("displayName") ?: "Unknown",
                                    photoURL = doc.getString("photoURL") ?: "",
                                    lastMessage = thread.lastMessage,
                                    updatedAt = thread.updatedAt,
                                    threadId = thread.threadId
                                )
                            )
                        }
                    }
                }

                recentPeople.sortByDescending { it.updatedAt?.seconds ?: 0L }
                onDone()
            }
            .addOnFailureListener {
                onFail()
            }
    }

    private fun loadFacultyDirectory(onDone: () -> Unit, onFail: () -> Unit) {
        val myUid = FirebaseAuth.getInstance().uid ?: run {
            onFail()
            return
        }

        db.collection("publicProfiles")
            .whereEqualTo("role", "faculty")
            .get()
            .addOnSuccessListener { snap ->
                facultyPeople.clear()

                val facultyDocs = snap.documents.filter { it.id != myUid }

                facultyPeople.addAll(
                    facultyDocs.map { doc ->
                        val uid = doc.id
                        val existingRecent = recentPeople.firstOrNull { it.uid == uid }

                        val displayName =
                            doc.getString("displayName")
                                ?: doc.getString("name")
                                ?: "Faculty"

                        val photoURL =
                            doc.getString("photoURL")
                                ?: ""

                        PersonItem(
                            uid = uid,
                            displayName = displayName,
                            photoURL = photoURL,
                            lastMessage = existingRecent?.lastMessage ?: "Message faculty",
                            updatedAt = existingRecent?.updatedAt,
                            threadId = existingRecent?.threadId ?: buildThreadId(myUid, uid)
                        )
                    }.distinctBy { it.uid }
                )

                facultyPeople.sortBy { it.displayName.lowercase() }
                onDone()
            }
            .addOnFailureListener {
                onFail()
            }
    }

    private fun setupSearch() {
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                rebuildDisplayItems(s?.toString().orEmpty())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
    }

    private fun rebuildDisplayItems(query: String) {
        val q = query.trim().lowercase()

        val recentFiltered = if (q.isBlank()) {
            recentPeople.toList()
        } else {
            recentPeople.filter {
                it.displayName.lowercase().contains(q) ||
                        it.lastMessage.lowercase().contains(q)
            }
        }

        val facultyFiltered = if (q.isBlank()) {
            facultyPeople.toList()
        } else {
            facultyPeople.filter {
                it.displayName.lowercase().contains(q) ||
                        it.lastMessage.lowercase().contains(q)
            }
        }

        displayItems.clear()

        when (currentFilter) {
            "faculty" -> displayItems.addAll(facultyFiltered)
            else -> displayItems.addAll(recentFiltered)
        }

        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        emptyChatsText.text = if (currentFilter == "faculty") {
            "No faculty found"
        } else {
            "No conversations yet"
        }

        emptyChatsContainer.visibility = if (displayItems.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (displayItems.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateFilterUi() {
        setChipState(filterRecent, currentFilter == "recent")
        setChipState(filterFaculty, currentFilter == "faculty")
    }

    private fun setChipState(chip: TextView, selected: Boolean) {
        chip.setBackgroundResource(
            if (selected) R.drawable.filter_chip_selected
            else R.drawable.filter_chip_unselected
        )
        chip.setTextColor(if (selected) Color.WHITE else Color.parseColor("#445046"))
    }

    private fun openChat(person: PersonItem) {
        val myUid = FirebaseAuth.getInstance().uid ?: return
        val threadId = buildThreadId(myUid, person.uid)

        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("threadId", threadId)
            putExtra("otherUid", person.uid)
            putExtra("otherUserName", person.displayName)
            putExtra("otherUserPhotoUrl", person.photoURL)
        }
        startActivity(intent)
    }

    private fun buildThreadId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }

    data class ThreadTemp(
        val threadId: String,
        val otherUid: String,
        val lastMessage: String,
        val updatedAt: Timestamp?
    )

    data class PersonItem(
        val uid: String,
        val displayName: String,
        val photoURL: String,
        val lastMessage: String,
        val updatedAt: Timestamp?,
        val threadId: String
    )

    class PeopleAdapter(
        private val items: List<PersonItem>,
        private val onClick: (PersonItem) -> Unit
    ) : RecyclerView.Adapter<PeopleAdapter.PersonVH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_preview, parent, false)
            return PersonVH(view)
        }

        override fun onBindViewHolder(holder: PersonVH, position: Int) {
            val person = items[position]

            holder.chatName.text = person.displayName

            var preview = person.lastMessage.ifBlank { "Start conversation" }

            val myUid = FirebaseAuth.getInstance().uid ?: ""

            val isMeLastSender = person.threadId.startsWith("${myUid}_")

            if (isMeLastSender && preview != "Start conversation") {
                preview = "You: $preview"
            }

            holder.chatLastMessage.text = preview
            holder.chatTime.text = formatInboxTime(person.updatedAt)

            val isUnread = !isMeLastSender

            if (isUnread) {
                holder.chatLastMessage.setTextColor(android.graphics.Color.BLACK)
                holder.chatLastMessage.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                holder.chatLastMessage.setTextColor(android.graphics.Color.parseColor("#7A8377"))
                holder.chatLastMessage.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            Glide.with(holder.itemView.context)
                .load(person.photoURL)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .circleCrop()
                .into(holder.chatAvatar)

            holder.itemView.setOnClickListener { onClick(person) }
        }

        override fun getItemCount(): Int = items.size

        class PersonVH(view: View) : RecyclerView.ViewHolder(view) {
            val chatAvatar: ImageView = view.findViewById(R.id.chatAvatar)
            val chatName: TextView = view.findViewById(R.id.chatName)
            val chatLastMessage: TextView = view.findViewById(R.id.chatLastMessage)
            val chatTime: TextView = view.findViewById(R.id.chatTime)
        }

        private fun formatInboxTime(timestamp: Timestamp?): String {
            if (timestamp == null) return ""

            val date = timestamp.toDate()
            val now = Date()
            val diff = now.time - date.time

            val minutes = diff / (1000 * 60)
            val hours = diff / (1000 * 60 * 60)
            val days = diff / (1000 * 60 * 60 * 24)

            return when {
                minutes < 1 -> "Now"
                minutes < 60 -> "${minutes}m"
                hours < 24 -> "${hours}h"
                days == 1L -> "Yesterday"
                days < 7 -> "${days}d"
                else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
            }
        }
    }
}
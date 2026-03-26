package com.phinmaed.uplug

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class DepartmentSetupFragment : Fragment(R.layout.fragment_department_setup) {

    private var selected: departmentData? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerView)
        val nextBtn = view.findViewById<Button>(R.id.nextBtn)

        val departments = listOf(
            departmentData("CEA", "College of Engineering and Architecture", R.drawable.cea),
            departmentData("COL", "College of Law", R.drawable.ic_profile),
            departmentData("CITE", "College of Information Technology Education", R.drawable.cite),
            departmentData("CAHS", "College of Allied Health Sciences", R.drawable.cahs),
            departmentData("CMA", "College of Management and Accountancy", R.drawable.cma),
            departmentData("CELA", "College of Education and Liberal Arts", R.drawable.cela),
            departmentData("CCJE", "College of Criminal Justice Education", R.drawable.ccje),
            departmentData("CAS", "College of Arts and Sciences", R.drawable.cas)
        )

        nextBtn.isEnabled = false

        recycler.layoutManager = GridLayoutManager(requireContext(), 2)
        recycler.adapter = DepartmentAdapter(departments) { picked ->
            selected = picked
            nextBtn.isEnabled = true
        }

        nextBtn.setOnClickListener {
            val me = FirebaseAuth.getInstance().currentUser
            if (me == null) {
                toast("Please login again.")
                requireActivity().finish()
                return@setOnClickListener
            }

            val choice = selected
            if (choice == null) {
                toast("Please select a department")
                return@setOnClickListener
            }

            val deptWall = choice.departmentName.trim().lowercase()

            // Save ONLY dept fields (fits your rules: user can update own doc)
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(me.uid)
                .set(
                    mapOf(
                        "deptWall" to deptWall,
                        "deptName" to choice.departmentFullName.trim(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .addOnSuccessListener {
                    startActivity(Intent(requireContext(), MainActivity::class.java))
                    requireActivity().finish()
                }
                .addOnFailureListener { e ->
                    toast("Failed to save department: ${e.message}")
                }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
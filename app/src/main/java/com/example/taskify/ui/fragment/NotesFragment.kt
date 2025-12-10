package com.example.taskify.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.taskify.databinding.FragmentNotesBinding
import com.example.taskify.databinding.DialogAddNoteBinding
import com.example.taskify.ui.adapter.NoteAdapter
import com.example.taskify.ui.viewmodel.MainViewModel

class NotesFragment : Fragment() {
    private var _binding: FragmentNotesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by viewModels()
    private lateinit var noteAdapter: NoteAdapter



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupListeners()

        viewModel.loadNotes()
    }

    private fun setupRecyclerView() {
        noteAdapter = NoteAdapter(
            onNoteClick = { note ->
                showEditNoteDialog(note)
            },
            onDeleteClick = { note ->
                showDeleteConfirmation(note)
            }
        )

        binding.rvNotes.apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            adapter = noteAdapter
        }
    }

    private fun setupObservers() {
        viewModel.notes.observe(viewLifecycleOwner) { notes ->
            noteAdapter.submitList(notes)
            binding.tvEmptyState.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setupListeners() {
        binding.fabAddNote.setOnClickListener {
            showAddNoteDialog()
        }
    }


    private fun showAddNoteDialog() {
        val dialogBinding = DialogAddNoteBinding.inflate(layoutInflater)

        val alertDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        alertDialog.show()

        dialogBinding.btnSaveNote.setOnClickListener {
            val title = dialogBinding.etTitle.text.toString().trim()
            val content = dialogBinding.etContent.text.toString().trim()

            if (title.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "Judul catatan tidak boleh kosong!", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                viewModel.addNote(title, content)

                alertDialog.dismiss()
            }
        }
    }

    private fun showEditNoteDialog(note: com.example.taskify.data.model.Note) {
        val dialogBinding = DialogAddNoteBinding.inflate(layoutInflater)
        dialogBinding.etTitle.setText(note.title)
        dialogBinding.etContent.setText(note.content)
        dialogBinding.dialogTitle.text = "Edit Catatan" 

        val alertDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnSaveNote.setOnClickListener {
            val title = dialogBinding.etTitle.text.toString()
            val content = dialogBinding.etContent.text.toString()

            if (title.isNotEmpty()) {
                viewModel.updateNote(
                    note.copy(
                        title = title,
                        content = content
                    )
                )
            }
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    private fun showDeleteConfirmation(note: com.example.taskify.data.model.Note) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Catatan")
            .setMessage("Apakah Anda yakin ingin menghapus catatan ini?")
            .setPositiveButton("Hapus") { _, _ ->
                viewModel.deleteNote(note)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
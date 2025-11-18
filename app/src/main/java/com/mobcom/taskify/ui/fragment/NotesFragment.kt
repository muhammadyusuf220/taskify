package com.mobcom.taskify.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mobcom.taskify.databinding.FragmentNotesBinding
import com.mobcom.taskify.databinding.DialogAddNoteBinding
import com.mobcom.taskify.ui.adapter.NoteAdapter
import com.mobcom.taskify.ui.viewmodel.MainViewModel

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

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Tambah Catatan Baru")
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan") { _, _ ->
                val title = dialogBinding.etTitle.text.toString()
                val content = dialogBinding.etContent.text.toString()

                if (title.isNotEmpty()) {
                    viewModel.addNote(title, content)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showEditNoteDialog(note: com.mobcom.taskify.data.model.Note) {
        val dialogBinding = DialogAddNoteBinding.inflate(layoutInflater)
        dialogBinding.etTitle.setText(note.title)
        dialogBinding.etContent.setText(note.content)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Catatan")
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan") { _, _ ->
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
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showDeleteConfirmation(note: com.mobcom.taskify.data.model.Note) {
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
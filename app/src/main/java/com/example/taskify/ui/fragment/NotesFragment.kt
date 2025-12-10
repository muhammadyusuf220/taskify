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

    // Di file NotesFragment.kt

    private fun showAddNoteDialog() {
        val dialogBinding = DialogAddNoteBinding.inflate(layoutInflater)

        // 1. Buat Dialog TANPA tombol Bawaan MaterialAlertDialogBuilder
        // Hapus .setPositiveButton() dan .setNegativeButton()
        val alertDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            // .setPositiveButton("Simpan", null) // <-- Hapus ini
            // .setNegativeButton("Batal", null)  // <-- Hapus ini
            .create()

        // 2. Tampilkan Dialog
        alertDialog.show()

        // 3. Atur aksi untuk tombol Simpan (btn_save_note) yang ada di layout kustom
        dialogBinding.btnSaveNote.setOnClickListener {
            val title = dialogBinding.etTitle.text.toString().trim()
            val content = dialogBinding.etContent.text.toString().trim()

            // VALIDASI: Judul wajib diisi
            if (title.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "Judul catatan tidak boleh kosong!", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                // Jika valid, simpan
                viewModel.addNote(title, content)

                // Tutup dialog manual
                alertDialog.dismiss()
            }
        }
    }

    private fun showEditNoteDialog(note: com.example.taskify.data.model.Note) {
        val dialogBinding = DialogAddNoteBinding.inflate(layoutInflater)
        dialogBinding.etTitle.setText(note.title)
        dialogBinding.etContent.setText(note.content)
        dialogBinding.dialogTitle.text = "Edit Catatan" // Perbarui judul

        val alertDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            // Hapus .setPositiveButton() dan .setNegativeButton()
            .create()

        // Atur aksi untuk tombol Simpan (btn_save_note)
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

        // Jika Anda ingin menambahkan tombol "Batal" di layout kustom,
        // Anda harus menambahkannya di dialog_add_note.xml terlebih dahulu,
        // lalu atur aksi di sini:
        // dialogBinding.btnCancel.setOnClickListener { alertDialog.dismiss() }

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
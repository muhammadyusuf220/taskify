package com.example.taskify.ui.fragment

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.taskify.R
import com.example.taskify.databinding.FragmentDashboardBinding
import com.example.taskify.databinding.DialogAddTaskBinding
import com.example.taskify.ui.adapter.TaskAdapter
import com.example.taskify.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*


class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by viewModels()
    private lateinit var taskAdapter: TaskAdapter


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupListeners()

        viewModel.loadTasks()
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            onTaskClick = { task -> showEditTaskDialog(task) },
            onTaskCheckChanged = { task, isChecked ->
                viewModel.toggleTaskCompletion(task, isChecked)
            },
            onDeleteClick = { task -> showDeleteConfirmation(task) }
        )

        binding.rvTasks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = taskAdapter
        }
    }

    private fun setupObservers() {
        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            taskAdapter.submitList(tasks)
            binding.tvEmptyState.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setupListeners() {
        binding.fabAddTask.setOnClickListener {
            showAddTaskDialog()
        }
    }


    private fun showAddTaskDialog() {
        val dialogBinding = DialogAddTaskBinding.inflate(layoutInflater)
        var selectedDate = ""

        dialogBinding.btnSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    selectedDate = dateFormat.format(calendar.time)
                    dialogBinding.btnSelectDate.text = selectedDate
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val alertDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        alertDialog.show()

        dialogBinding.btnSaveTask.setOnClickListener {
            val title = dialogBinding.etTitle.text.toString().trim()
            val description = dialogBinding.etDescription.text.toString().trim()

            if (title.isEmpty()) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Judul tugas tidak boleh kosong!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else if (selectedDate.isEmpty()) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Tanggal tugas harus dipilih!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                viewModel.addTask(title, description, selectedDate)

                alertDialog.dismiss()
            }
        }
    }

    private fun showEditTaskDialog(task: com.example.taskify.data.model.Task) {
        val dialogBinding = DialogAddTaskBinding.inflate(layoutInflater)
        dialogBinding.etTitle.setText(task.title)
        dialogBinding.etDescription.setText(task.description)
        dialogBinding.btnSelectDate.text = task.due_date
        dialogBinding.dialogTitle.text = "Edit Tugas"

        var selectedDate = task.due_date

        dialogBinding.btnSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    selectedDate = dateFormat.format(calendar.time)
                    dialogBinding.btnSelectDate.text = selectedDate
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val alertDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        alertDialog.show()

        dialogBinding.btnSaveTask.setOnClickListener {
            val title = dialogBinding.etTitle.text.toString()
            val description = dialogBinding.etDescription.text.toString()

            if (title.isNotEmpty()) {
                viewModel.updateTask(
                    task.copy(
                        title = title,
                        description = description,
                        due_date = selectedDate
                    )
                )
                alertDialog.dismiss()
            } else {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Judul tidak boleh kosong",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }

        }
    }

        private fun showDeleteConfirmation(task: com.example.taskify.data.model.Task) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Hapus Tugas")
                .setMessage("Apakah Anda yakin ingin menghapus tugas ini?")
                .setPositiveButton("Hapus") { _, _ ->
                    viewModel.deleteTask(task)
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }
    }

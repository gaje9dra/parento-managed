package com.parento.managed.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class EnrollmentViewModelFactory(
    private val repository: EnrollmentRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(EnrollmentViewModel::class.java))
        return EnrollmentViewModel(repository) as T
    }
}

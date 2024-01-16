package com.lorraine.hiremequick.presentation.employer.add

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorraine.hiremequick.data.model.JobPosting
import com.lorraine.hiremequick.data.model.RequestState
import com.lorraine.hiremequick.data.repository.FirebaseAttachmentLogRepo
import com.lorraine.hiremequick.utils.Contants.ADD_SCREEN_ARGUMENT_KEY
import com.lorraine.hiremequick.utils.toInstant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class AddLogViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    var _uiState = MutableStateFlow(UiState())
        private set
    var uiState = _uiState.asStateFlow()

    init {
        getAttachmentLogIdArgument()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            fetchSelectedAttachmentLog()
        }
    }

    private fun getAttachmentLogIdArgument() {
        _uiState.update {
            it.copy(
                selectedJobPostingId = savedStateHandle.get<String>(
                    key = ADD_SCREEN_ARGUMENT_KEY,
                ),
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun fetchSelectedAttachmentLog() {
        Log.d("Fetch selected", "fetchSelectedid : ${_uiState.value.selectedJobPostingId}")
        if (_uiState.value.selectedJobPostingId != null) {
            viewModelScope.launch {
                val result =
                    FirebaseAttachmentLogRepo.getSelectedJob(jobId = _uiState.value.selectedJobPostingId!!)
                when (result) {
                    is RequestState.Error -> TODO()
                    RequestState.Idle -> TODO()
                    RequestState.Loading -> TODO()
                    is RequestState.Success -> {
                        Log.d("AddLogVm", "fetchSelectedAttachmentLog:${result.data.title} ")
                        setSelectedLog(attachmentLog = result.data)
                        setTitle(title = result.data.title)
                        setDescription(description = result.data.description)
                    }
                }
            }
        }
    }

    private fun setSelectedLog(attachmentLog: JobPosting) {
        _uiState.update {
            it.copy(
                selectedJobPosting = attachmentLog,
            )
        }
    }

    fun setTitle(title: String) {
        _uiState.update {
            it.copy(
                title = title,
            )
        }
    }

    fun setDescription(description: String) {
        _uiState.update {
            it.copy(
                description = description,
            )
        }
    }

    fun updateDateTime(zonedDateTime: ZonedDateTime) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            _uiState.update {
                it.copy(
                    updatedDateTime = zonedDateTime.toLocalDateTime().toInstant(),
                )
            }
        }
    }

    fun upsertLog(
        attachmentLog: JobPosting,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.selectedJobPostingId != null) {
                updateAttachmentLog(
                    attachmentLog = attachmentLog,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            } else {
                insertAttachmentLog(
                    attachmentLog = attachmentLog,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            }
        }
    }

    fun updateLog(
        attachmentLog: JobPosting,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            updateAttachmentLog(
                attachmentLog = attachmentLog,
                onSuccess = onSuccess,
                onError = onError,
            )
        }
    }

    private suspend fun insertAttachmentLog(
        attachmentLog: JobPosting,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val result =
            FirebaseAttachmentLogRepo.insertJob(
                jobPosting = attachmentLog.apply {
                    if (_uiState.value.updatedDateTime != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            datePosted = _uiState.value.updatedDateTime?.toEpochMilli() ?: 0L
                        }
                    }
                },
            )
        if (result is RequestState.Success) {
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        } else if (result is RequestState.Error) {
            withContext(Dispatchers.Main) {
                onError(result.error.message.toString())
            }
        }
    }

    private suspend fun updateAttachmentLog(
        attachmentLog: JobPosting,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val result =
            FirebaseAttachmentLogRepo.updateJobPosting(
                jobPosting = attachmentLog.apply {
                    jobId = _uiState.value.selectedJobPostingId!!
                    datePosted = if (_uiState.value.updatedDateTime != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            _uiState.value.updatedDateTime?.toEpochMilli() ?: 0L
                        } else {
                            TODO("VERSION.SDK_INT < O")
                        }
                    } else {
                        _uiState.value.selectedJobPosting!!.datePosted
                    }
                },
            )
        if (result is RequestState.Success) {
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        } else if (result is RequestState.Error) {
            withContext(Dispatchers.Main) {
                onError(result.error.message.toString())
            }
        }
    }

    fun deleteAttachmentLog(
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.selectedJobPostingId != null) {
                val result =
                    FirebaseAttachmentLogRepo.deleteJobPosting(id = _uiState.value.selectedJobPostingId!!)
                if (result is RequestState.Success) {
                    withContext(Dispatchers.Main) {
                        uiState.value.selectedJobPosting?.let {
                        }
                        onSuccess()
                    }
                } else if (result is RequestState.Error) {
                    withContext(Dispatchers.Main) {
                        onError(result.error.message.toString())
                    }
                }
            }
        }
    }
}

data class UiState(
    val selectedJobPostingId: String? = null,
    val selectedJobPosting: JobPosting? = null,
    val title: String = "",
    val description: String = "",
    val updatedDateTime: Instant? = null,
)
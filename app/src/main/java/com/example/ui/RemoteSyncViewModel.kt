package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.UploadTask
import com.example.onlinepull.AssessmentSystemClient
import com.example.onlinepull.RemoteProjectSummary
import com.example.onlinepull.RemoteSyncRepository
import com.example.onlinepull.RemoteUploadRepository
import com.example.onlinepull.SyncReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RemoteSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val client = AssessmentSystemClient()
    private val syncRepository = RemoteSyncRepository(application.applicationContext, client)
    private val uploadRepository = RemoteUploadRepository(application.applicationContext, client)

    private val _projects = MutableStateFlow<List<RemoteProjectSummary>>(emptyList())
    val projects: StateFlow<List<RemoteProjectSummary>> = _projects.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _report = MutableStateFlow<SyncReport?>(null)
    val report: StateFlow<SyncReport?> = _report.asStateFlow()
    private val _uploadMessage = MutableStateFlow<String?>(null)
    val uploadMessage: StateFlow<String?> = _uploadMessage.asStateFlow()

    private val _uploadTasks = MutableStateFlow<List<UploadTask>>(emptyList())
    val uploadTasks: StateFlow<List<UploadTask>> = _uploadTasks.asStateFlow()

    fun loadProjects() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try { _projects.value = client.listProjects() }
            catch (e: Exception) { _error.value = e.message ?: "项目列表加载失败" }
            finally { _busy.value = false }
        }
    }

    fun sync(project: RemoteProjectSummary) {
        if (_busy.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            _error.value = null
            _report.value = null
            try {
                val result = syncRepository.sync(project) { _progress.value = it }
                _report.value = result
                _uploadTasks.value = uploadRepository.tasksOnce(result.localProjectId)
            } catch (e: Exception) { _error.value = e.message ?: "同步失败" }
            finally { _busy.value = false; _progress.value = "" }
        }
    }

    fun upload(stableKey: String) {
        if (_busy.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            _uploadMessage.value = null
            try {
                _uploadMessage.value = uploadRepository.upload(stableKey).message
                _report.value?.localProjectId?.let { _uploadTasks.value = uploadRepository.tasksOnce(it) }
            } catch (e: Exception) { _uploadMessage.value = e.message ?: "上传失败" }
            finally { _busy.value = false }
        }
    }

    fun uploadAll() {
        if (_busy.value) return
        val localProjectId = _report.value?.localProjectId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            _uploadMessage.value = null
            try {
                val results = uploadRepository.uploadAll(localProjectId)
                val successCount = results.count { it.status == "success" }
                _uploadMessage.value = "批量上传完成：成功 " + successCount + "/" + results.size
                _uploadTasks.value = uploadRepository.tasksOnce(localProjectId)
            } catch (e: Exception) { _uploadMessage.value = e.message ?: "批量上传失败" }
            finally { _busy.value = false }
        }
    }

    fun clearError() { _error.value = null }
}

package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.onlinepull.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RemoteSyncViewModel @JvmOverloads constructor(
    application: Application,
    private val store: McpCredentials = McpTokenStore(application),
    private val connect: (String) -> InventorySource = { AssessmentSystemClient(McpClient(it)) }
) : AndroidViewModel(application) {
    private var client: InventorySource? = null
    private val _hasToken = MutableStateFlow(false)
    val hasToken = _hasToken.asStateFlow()
    private val _connectionStatus = MutableStateFlow("未配置连接")
    val connectionStatus = _connectionStatus.asStateFlow()
    private val _credentialError = MutableStateFlow<String?>(null)
    val credentialError = _credentialError.asStateFlow()
    private val _credentialRevision = MutableStateFlow(0)
    val credentialRevision = _credentialRevision.asStateFlow()
    private val _projects = MutableStateFlow<List<RemoteProjectSummary>>(emptyList())
    val projects = _projects.asStateFlow()
    private val _projectsLoaded = MutableStateFlow(false)
    val projectsLoaded = _projectsLoaded.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _progress = MutableStateFlow("")
    val progress = _progress.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _report = MutableStateFlow<SyncReport?>(null)
    val report = _report.asStateFlow()

    init {
        _busy.value = true
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { store.read() }
                _hasToken.value = saved != null
                client = saved?.let(connect)
                _connectionStatus.value = if (saved == null) "未配置连接" else "凭据已保存，尚未验证"
            } catch (error: Exception) { _credentialError.value = RemoteSyncRepository.safeError(error) }
            finally { _busy.value = false }
        }
    }

    fun saveCredential(input: String) {
        if (_busy.value) return
        _busy.value = true
        _credentialError.value = null
        viewModelScope.launch {
            try {
                val candidate = withContext(Dispatchers.IO) {
                    val token = McpCredentialInput.parse(input)
                    val candidateClient = connect(token)
                    val projects = candidateClient.listProjects()
                    // Verification must finish before the atomic credential replacement.
                    store.save(token)
                    candidateClient to projects
                }
                client = candidate.first
                _hasToken.value = true
                _projects.value = candidate.second
                _projectsLoaded.value = true
                _connectionStatus.value = "连接已验证"
                _credentialRevision.value++
                _report.value = null
                _error.value = null
                RemoteSyncRepository(getApplication(), candidate.first).recordAccessibleProjects(candidate.second)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                _credentialError.value = RemoteSyncRepository.safeError(error) + "。未通过验证时不会替换原 Token。"
            } finally { _busy.value = false }
        }
    }

    fun removeCredential() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { store.clear() }
                client = null
                _hasToken.value = false
                _projects.value = emptyList()
                _projectsLoaded.value = false
                _connectionStatus.value = "凭据已移除，本地资料仍可使用"
                _credentialError.value = null
                _credentialRevision.value++
                _report.value = null
                _error.value = null
            } catch (_: Exception) { _credentialError.value = "移除凭据失败，请重试" }
            finally { _busy.value = false }
        }
    }

    fun loadProjects() {
        if (_busy.value) return
        val activeClient = client ?: run { _error.value = "请先配置连接 Token"; return }
        _busy.value = true
        _error.value = null
        _projectsLoaded.value = false
        viewModelScope.launch {
            try {
                val projects = activeClient.listProjects()
                RemoteSyncRepository(getApplication(), activeClient).recordAccessibleProjects(projects)
                _projects.value = projects
                _projectsLoaded.value = true
                _connectionStatus.value = "连接已验证"
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { fail(error) }
            finally { _busy.value = false }
        }
    }

    fun sync(project: RemoteProjectSummary) {
        if (_busy.value || !_projectsLoaded.value) return
        val activeClient = client ?: return
        if (_projects.value.none { it.id == project.id }) return
        _busy.value = true
        _error.value = null
        _report.value = null
        viewModelScope.launch {
            try {
                _report.value = RemoteSyncRepository(getApplication(), activeClient).sync(project) { _progress.value = it }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { fail(error) }
            finally { _busy.value = false; _progress.value = "" }
        }
    }

    private fun fail(error: Exception) {
        _error.value = RemoteSyncRepository.safeError(error)
        if (error is McpAuthFailure) {
            _connectionStatus.value = "Token 已失效，请更新"
            _projectsLoaded.value = false
        }
    }
}

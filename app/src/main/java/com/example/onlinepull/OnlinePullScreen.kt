package com.example.onlinepull

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.StockItem
import com.example.ui.StockViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

private val PROJECT_URL_REGEX = Regex("/ty/operation/(\\d+)(?:/(\\d+))?")

private class FetchedRow(val subject: SubjectDef, val row: JSONObject) {
    val isCheck: Boolean
        get() = SubjectFieldMap.isCheckTrue(row.optString(SubjectFieldMap.CHECK_FIELD_CODE, ""))

    fun valueByCode(code: String?): String {
        if (code.isNullOrEmpty()) return ""
        val v = row.opt(code) ?: return ""
        val s = v.toString()
        return if (s == "null") "" else s
    }

    fun valueByCn(cn: String): String {
        val f = subject.displayFields.firstOrNull { it.cn == cn } ?: return ""
        return valueByCode(f.code)
    }
}

/**
 * 「线上拉取」全屏页面。
 * 流程：WebView 登录 → 项目表格选择（自动探测项目列表）→ 选公司/科目 →
 * 拉明细 → 按「是否盘点」统计 → 映射为 StockItem 导入当前 App 项目。
 * 阶段：0=登录, 1=项目选择, 2=公司/科目选择, 3=预览
 */
@Composable
fun OnlinePullScreen(viewModel: StockViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { ZhrdcApiService() }
    val repository = viewModel.repository

    var stage by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var progressText by remember { mutableStateOf("") }

    var capturedProjectId by remember { mutableStateOf<String?>(null) }
    var capturedCompanyId by remember { mutableStateOf<String?>(null) }
    var projectIdInput by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf<String?>(null) }

    var projects by remember { mutableStateOf<List<ProjectInfo>>(emptyList()) }
    var loadingProjects by remember { mutableStateOf(false) }

    var companies by remember { mutableStateOf<List<CompanyInfo>>(emptyList()) }
    var selectedCompanyIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tree by remember { mutableStateOf<List<SubjectNode>>(emptyList()) }
    var selectedSubjects by remember { mutableStateOf<Set<String>>(emptySet()) }

    var targetProjectId by remember { mutableStateOf(viewModel.activeProjectId.value) }
    var replaceMode by remember { mutableStateOf(false) }

    var previewRows by remember { mutableStateOf<List<FetchedRow>>(emptyList()) }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    // 捕获 WebView URL 中的项目/公司 ID（作为兜底，主路径是项目表格选择）
    val onWebUrl: (String) -> Unit = { url ->
        val m = PROJECT_URL_REGEX.find(url)
        if (m != null) {
            val pid = m.groupValues[1]
            if (pid.isNotEmpty() && capturedProjectId != pid) {
                capturedProjectId = pid
                projectIdInput = pid
            }
            val cid = m.groupValues[2]
            if (cid.isNotEmpty() && capturedCompanyId != cid) {
                capturedCompanyId = cid
            }
        }
    }

    // WebView 实例（remember 持有，供登录/重新登录复用）
    val webViewHolder = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            // 兼容企业微信扫码等登录流程中的弹窗/新窗口
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            webChromeClient = WebChromeClient()
            // 线上系统为 PC 网页：桌面 UA + 宽视口缩放 + 双指缩放，保证竖屏可用
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url != null) onWebUrl(url)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val u = request?.url?.toString() ?: return false
                    onWebUrl(u)
                    if (u.startsWith("http://") || u.startsWith("https://")) return false
                    // 企业微信/微信扫码等外部协议交由系统处理
                    return try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
            }
        }
    }

    fun relogin() {
        CookieManager.getInstance().removeAllCookies(null)
        try {
            webViewHolder.loadUrl("https://ty.zhrdc.net/")
        } catch (_: Exception) {
        }
        stage = 0
        message = ""
        projects = emptyList()
    }

    LaunchedEffect(Unit) {
        webViewHolder.loadUrl("https://ty.zhrdc.net/")
    }

    // 拦截系统返回手势/返回键：按层级后退，而不是直接退出 App
    BackHandler(enabled = true) {
        when (stage) {
            3 -> stage = 2
            2 -> stage = 1
            1 -> relogin()
            else -> onClose()
        }
    }

    // =============================== 阶段0：登录 ===============================
    if (stage == 0) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ScreenHeader(
                title = "线上拉取 · 登录",
                subtitle = "请登录公司线上作业系统（企业微信扫码或账号密码）",
                onBack = onClose
            )

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(factory = { webViewHolder }, modifier = Modifier.fillMaxSize())
                if (capturedProjectId != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("已捕获项目 ${capturedProjectId}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (message.isNotEmpty()) {
                    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Button(
                    onClick = {
                        busy = true
                        message = ""
                        scope.launch {
                            try {
                                val name = service.currentUserName()
                                if (name == null) {
                                    withContext(Dispatchers.Main) {
                                        message = "未检测到登录态，请先在网页中完成登录（企业微信扫码或账号密码）"
                                        busy = false
                                    }
                                    return@launch
                                }
                                withContext(Dispatchers.Main) {
                                    userName = name
                                    // 若网址已捕获项目/公司，自动填入兜底输入框
                                    if (projectIdInput.isBlank() && capturedProjectId != null) {
                                        projectIdInput = capturedProjectId!!
                                    }
                                    if (capturedCompanyId != null) {
                                        selectedCompanyIds = setOf(capturedCompanyId!!)
                                    }
                                    loadingProjects = true
                                    busy = false
                                    stage = 1
                                }
                                // 后台探测项目列表（自动分页）
                                val list = service.discoverProjects()
                                withContext(Dispatchers.Main) {
                                    projects = list
                                    loadingProjects = false
                                    if (list.isEmpty()) {
                                        message = "未自动获取到项目列表，可手动输入项目编号，或从网址中复制"
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    loadingProjects = false
                                    busy = false
                                    message = "连接失败：${e.message}"
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在验证登录…")
                    } else {
                        Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("验证登录并继续", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    // =============================== 阶段1：项目选择表格 ===============================
    if (stage == 1) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ScreenHeader(
                title = "线上拉取 · 选择项目",
                subtitle = "用户：${userName ?: "-"}   共 ${projects.size} 个项目",
                onBack = { relogin() }
            )

            // 手动输入兜底
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = projectIdInput,
                    onValueChange = { projectIdInput = it.filter { c -> c.isDigit() } },
                    label = { Text("项目编号", fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val input = projectIdInput.trim()
                        if (input.isEmpty()) {
                            message = "请输入项目编号"
                            return@Button
                        }
                        // 支持输入网页编号(projectCode)或系统内部ID，自动匹配转换
                        val matched = projects.firstOrNull { it.id == input || it.code == input }
                        if (matched != null) {
                            projectIdInput = matched.id
                        } else {
                            projectIdInput = input
                        }
                        message = ""
                        companies = emptyList()
                        selectedCompanyIds = emptySet()
                        tree = emptyList()
                        selectedSubjects = emptySet()
                        stage = 2
                    },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("使用该编号", fontSize = 13.sp)
                }
            }

            if (message.isNotEmpty()) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            when {
                loadingProjects -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("正在获取项目列表…", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                }
                projects.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Inbox,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "未获取到项目列表\n请用上方输入框手动填写项目编号，\n或在登录网页中进入项目页面后从网址复制",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = {
                                loadingProjects = true
                                scope.launch {
                                    val list = withContext(Dispatchers.IO) { service.discoverProjects() }
                                    withContext(Dispatchers.Main) {
                                        projects = list
                                        loadingProjects = false
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("重新获取", fontSize = 13.sp)
                            }
                        }
                    }
                }
                else -> {
                    // 表格表头
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "项目编号",
                            modifier = Modifier.weight(1.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "项目名称",
                            modifier = Modifier.weight(2.4f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(projects, key = { it.id }) { p ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        projectIdInput = p.id
                                        message = ""
                                        companies = emptyList()
                                        selectedCompanyIds = emptySet()
                                        tree = emptyList()
                                        selectedSubjects = emptySet()
                                        stage = 2
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        p.code.ifBlank { p.id },
                                        modifier = Modifier.weight(1.6f),
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Column(modifier = Modifier.weight(2.4f)) {
                                        Text(
                                            p.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (p.code.isNotBlank()) {
                                            Text(
                                                "系统ID: ${p.id}",
                                                fontSize = 10.sp,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            }
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
        return
    }

    // =============================== 阶段2：公司/科目选择 ===============================
    if (stage == 2) {
        // 项目变化时：清空旧项目选择并强制重载公司列表
        LaunchedEffect(stage, projectIdInput) {
            if (stage != 2) return@LaunchedEffect
            val projs = viewModel.allProjects.value
            if (targetProjectId.isBlank() && projs.isNotEmpty()) {
                targetProjectId = projs[0].id
            }
            companies = emptyList()
            selectedCompanyIds = emptySet()
            tree = emptyList()
            selectedSubjects = emptySet()
            busy = true
            progressText = "正在加载公司列表…"
            try {
                val list = service.listCompanies(projectIdInput.trim())
                withContext(Dispatchers.Main) {
                    companies = list
                    // 默认全选所有公司，避免漏掉数据所在的公司
                    selectedCompanyIds = list.map { it.id }.toSet()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { message = "公司列表加载失败：${e.message}" }
            } finally {
                withContext(Dispatchers.Main) { busy = false }
            }
        }

        // 切换公司时重拉科目树
        LaunchedEffect(selectedCompanyIds) {
            if (selectedCompanyIds.isEmpty()) {
                tree = emptyList()
                return@LaunchedEffect
            }
            val pid = projectIdInput.trim()
            busy = true
            progressText = "正在加载科目树…"
            try {
                val t = service.subjectTree(pid, selectedCompanyIds.toList())
                withContext(Dispatchers.Main) { tree = t }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { message = "科目树加载失败：${e.message}" }
            } finally {
                withContext(Dispatchers.Main) { busy = false }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ScreenHeader(
                title = "线上拉取 · 选择公司与科目",
                subtitle = "用户：${userName ?: "-"}   项目：${projectIdInput}",
                onBack = { stage = 1 }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
            ) {
                item {
                    SectionTitle("① 选择公司（默认全选，数据可能分散在不同公司下）")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            selectedCompanyIds = companies.map { it.id }.toSet()
                        }) {
                            Text("全选", fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            selectedCompanyIds = emptySet()
                        }) {
                            Text("清空", fontSize = 12.sp)
                        }
                    }
                    if (companies.isEmpty() && !busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("公司列表为空", fontSize = 13.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.width(10.dp))
                            TextButton(onClick = {
                                scope.launch {
                                    busy = true
                                    try {
                                        val list = service.listCompanies(projectIdInput.trim())
                                        withContext(Dispatchers.Main) { companies = list }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) { message = "公司列表加载失败：${e.message}" }
                                    } finally {
                                        withContext(Dispatchers.Main) { busy = false }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("重新加载公司列表", fontSize = 12.sp)
                            }
                        }
                    } else {
                        companies.forEach { c ->
                            val checked = c.id in selectedCompanyIds
                            val displayName = c.raw.optString("shortName", "").ifBlank { c.name }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        selectedCompanyIds =
                                            if (checked) selectedCompanyIds - c.id
                                            else selectedCompanyIds + c.id
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (checked) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = checked, onCheckedChange = { on ->
                                        selectedCompanyIds =
                                            if (on) selectedCompanyIds + c.id else selectedCompanyIds - c.id
                                    })
                                    Text(
                                        "${displayName}（${c.id}）",
                                        fontSize = 13.sp,
                                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    SectionTitle("② 勾选需盘点的科目（含「是否盘点」列的科目带 ✓ 标记）")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                // 只勾选含 SFPD 盘点列的科目
                                val codes = mutableSetOf<String>()
                                fun walk(nodes: List<SubjectNode>) {
                                    nodes.forEach { n ->
                                        val def = SubjectFieldMap.byCode(n.code)
                                        if (def != null && def.hasCheckColumn) codes.add(SubjectFieldMap.normalize(n.code))
                                        walk(n.children)
                                    }
                                }
                                walk(tree)
                                selectedSubjects = codes
                                toast("已勾选 ${codes.size} 个含「是否盘点」列的科目")
                            },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("勾选需盘点科目", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val codes = mutableSetOf<String>()
                                fun walk(nodes: List<SubjectNode>) {
                                    nodes.forEach { n ->
                                        if (SubjectFieldMap.byCode(n.code) != null) codes.add(SubjectFieldMap.normalize(n.code))
                                        walk(n.children)
                                    }
                                }
                                walk(tree)
                                selectedSubjects = codes
                            },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("全选", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { selectedSubjects = emptySet() },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("清空", fontSize = 12.sp)
                        }
                    }
                    if (busy) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(progressText, fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    if (tree.isEmpty()) {
                        Text(
                            "请先选择公司以加载科目树",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        tree.forEach { node ->
                            SubjectTreeItem(
                                node = node,
                                depth = 0,
                                selected = selectedSubjects,
                                onToggle = { code ->
                                    selectedSubjects =
                                        if (code in selectedSubjects) selectedSubjects - code
                                        else selectedSubjects + code
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    SectionTitle("③ 导入目标（本机分类项目）")
                    viewModel.allProjects.collectAsState(initial = emptyList()).value.forEach { p ->
                        val checked = p.id == targetProjectId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { targetProjectId = p.id },
                            shape = RoundedCornerShape(10.dp),
                            color = if (checked) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = checked, onClick = { targetProjectId = p.id })
                                Text(p.name, fontSize = 13.sp, fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("导入模式：", fontSize = 13.sp)
                        TextButton(onClick = { replaceMode = false }) {
                            RadioButton(selected = !replaceMode, onClick = { replaceMode = false })
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("追加", fontSize = 13.sp)
                        }
                        TextButton(onClick = { replaceMode = true }) {
                            RadioButton(selected = replaceMode, onClick = { replaceMode = true })
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("覆写替换", fontSize = 13.sp)
                        }
                    }
                    if (replaceMode) {
                        Text(
                            "⚠ 覆写将清空目标项目现有清单及其照片/PDF",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    if (message.isNotEmpty()) {
                        Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            val pid = projectIdInput.trim()
                            if (selectedCompanyIds.isEmpty()) {
                                message = "请至少选择一家公司"
                                return@Button
                            }
                            if (selectedSubjects.isEmpty()) {
                                message = "请至少勾选一个科目"
                                return@Button
                            }
                            if (targetProjectId.isBlank()) {
                                message = "请先在 App 主界面新建一个分类项目"
                                return@Button
                            }
                            scope.launch {
                                busy = true
                                message = ""
                                val fetched = mutableListOf<FetchedRow>()
                                val subjectCodes = selectedSubjects.toList().sorted()
                                try {
                                    for ((idx, code) in subjectCodes.withIndex()) {
                                        val def = SubjectFieldMap.byCode(code) ?: continue
                                        progressText = "拉取中 ${idx + 1}/${subjectCodes.size}：${def.name}…"
                                        val rows = service.fetchAllDraftData(
                                            pid, code, selectedCompanyIds.toList(), 1
                                        ) { loaded, total ->
                                            scope.launch(Dispatchers.Main) {
                                                progressText = "拉取 ${def.name}：$loaded/${if (total >= 0) total else "?"} 行"
                                            }
                                        }
                                        rows.forEach { fetched.add(FetchedRow(def, it)) }
                                        // 主表为空时，探测其他工作表(担保/租赁/盘点等)的行数，辅助定位数据位置
                                        if (rows.isEmpty()) {
                                            scope.launch(Dispatchers.IO) {
                                                val probes = StringBuilder()
                                                for (dt in 2..5) {
                                                    try {
                                                        val p = service.draftData(pid, code, selectedCompanyIds.toList(), dt, 1, 5000)
                                                        probes.append(" sheet$dt=${p.total}行")
                                                    } catch (_: Exception) {
                                                        probes.append(" sheet$dt=err")
                                                    }
                                                }
                                                android.util.Log.d("ZhrdcApi", "probe $code empty main sheet; $probes")
                                                // 探测底稿列表接口（可能包含各科目明细）
                                                service.probeAssignmentDrafts(pid, selectedCompanyIds.toList())
                                            }
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        previewRows = fetched
                                        stage = 3
                                        busy = false
                                        message = ""
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        busy = false
                                        message = "拉取失败：${e.message}"
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(progressText.ifBlank { "拉取中…" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("拉取数据预览", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
        return
    }

    // =============================== 阶段3：预览确认 ===============================
    if (stage == 3) {
        val grouped = previewRows.groupBy { it.subject }
        val totalRows = previewRows.size
        val totalCheck = previewRows.count { it.isCheck }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ScreenHeader(
                title = "线上拉取 · 预览确认",
                subtitle = "共 $totalRows 行，其中需盘点 $totalCheck 行",
                onBack = { stage = 2 }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
            ) {
                item {
                    if (totalRows == 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "所选科目在当前项目/公司下没有数据（共 0 行）。\n" +
                                        "请返回检查：网页端该项目的资产基础法底稿是否已录入这些科目的明细，或换一个项目。",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error,
                                lineHeight = 19.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    SectionTitle("各科目拉取统计")
                    grouped.forEach { (subject, rows) ->
                        val checkCount = rows.count { it.isCheck }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(subject.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("${rows.size} 行 · 需盘点 $checkCount 行", fontSize = 12.sp, color = Color.Gray)
                                }
                                if (subject.hasCheckColumn) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("含盘点列", fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    Text(
                        "将导入 App 项目「${viewModel.allProjects.collectAsState(initial = emptyList()).value.firstOrNull { it.id == targetProjectId }?.name ?: "?"}」" +
                                "（${if (replaceMode) "覆写替换" else "追加"}）。「是否盘点=是」的行自动进入待盘点列表。",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (message.isNotEmpty()) {
                        Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                message = ""
                                try {
                                    val imported = withContext(Dispatchers.IO) {
                                        // 若目标项目未设置持有单位，用所选第一家公司的名称自动填充
                                        val proj = repository.getProjectById(targetProjectId)
                                        if (proj != null && proj.companyName.isBlank()) {
                                            val firstCompany = companies.firstOrNull { it.id in selectedCompanyIds }
                                            if (firstCompany != null) {
                                                repository.insertProject(proj.copy(companyName = firstCompany.name))
                                            }
                                        }
                                        importRows(repository, context, targetProjectId, previewRows, replaceMode)
                                    }
                                    withContext(Dispatchers.Main) {
                                        val checkCount = previewRows.count { it.isCheck }
                                        viewModel.selectProject(targetProjectId)
                                        toast("线上拉取导入成功：$imported 条（需盘点 $checkCount 条）")
                                        busy = false
                                        onClose()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        busy = false
                                        message = "导入失败：${e.message}"
                                    }
                                }
                            }
                        },
                        enabled = !busy && totalRows > 0,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("导入中…")
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (totalRows > 0) "确认导入 $totalRows 行" else "无数据可导入", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/** 将拉取到的行映射为 StockItem 并写入数据库，返回导入行数 */
private suspend fun importRows(
    repository: com.example.data.StockRepository,
    context: android.content.Context,
    projectId: String,
    rows: List<FetchedRow>,
    replace: Boolean
): Int = withContext(Dispatchers.IO) {
    // 1. 合并列头：序号 + 科目名称 + 各科目列（按出现顺序去重）
    val headers = mutableListOf("序号", "科目名称")
    for (r in rows) {
        for (f in r.subject.displayFields) {
            if (f.cn !in headers) headers.add(f.cn)
        }
    }

    // 2. 生成 StockItem
    val items = mutableListOf<StockItem>()
    var order = 1
    for (r in rows) {
        val values = mutableListOf<String>()
        val xh = r.row.optString("XH", "")
        values.add(if (xh.isNotBlank()) xh else order.toString())
        values.add(r.subject.name)
        for (cn in headers.drop(2)) {
            values.add(r.valueByCn(cn))
        }

        val name = r.valueByCode(r.subject.nameCode).ifBlank {
            r.valueByCode(r.subject.itemCode).ifBlank { "未命名${order}" }
        }
        val code = r.valueByCode(r.subject.itemCode)
        val location = r.valueByCode(r.subject.locationCode)

        val uid = r.row.optString("_id", "").ifBlank {
            r.row.optString("bizId", "").ifBlank { UUID.randomUUID().toString() }
        }

        items.add(
            StockItem(
                uid = uid,
                name = name,
                category = r.subject.name,
                location = location,
                originalCode = code,
                photoCount = 0,
                pdfStatus = "未生成",
                projectId = projectId,
                shouldCheck = r.isCheck,
                originalRowJson = repository.toJsonList(values),
                rowOrder = order
            )
        )
        order++
    }

    // 3. 更新项目列头（供导出 XLSX 按中文列名展示）
    val project = repository.getProjectById(projectId)
    if (project != null) {
        repository.insertProject(project.copy(columnHeadersJson = repository.toJsonList(headers)))
    }

    // 4. 写入
    repository.importOnlineItems(context, projectId, items, replace)
    items.size
}

// ---------------------------------------------------------------- 子组件

@Composable
private fun ScreenHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, fontSize = 11.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun SubjectTreeItem(
    node: SubjectNode,
    depth: Int,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    val def = SubjectFieldMap.byCode(node.code)
    val selectable = def != null
    val normalizedCode = SubjectFieldMap.normalize(node.code)
    val checked = selectable && normalizedCode in selected
    var expanded by remember { mutableStateOf(depth < 1) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (node.children.isNotEmpty()) expanded = !expanded
                else if (selectable) onToggle(normalizedCode)
            }
            .padding(start = (depth * 18).dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.children.isNotEmpty()) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.Gray
            )
        } else {
            Spacer(modifier = Modifier.width(20.dp))
        }
        Spacer(modifier = Modifier.width(4.dp))
        if (selectable) {
            Checkbox(
                checked = checked,
                onCheckedChange = { on -> onToggle(normalizedCode) },
                modifier = Modifier.size(20.dp)
            )
        } else if (node.isLeaf) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color.LightGray.copy(alpha = 0.5f), CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = node.name.ifBlank { node.code },
                fontSize = 13.sp,
                fontWeight = if (node.children.isEmpty() && selectable) FontWeight.Medium else FontWeight.Normal,
                color = if (selectable) MaterialTheme.colorScheme.onSurface else Color.Gray
            )
            if (node.isLeaf) {
                val tag = when {
                    def?.hasCheckColumn == true -> "含「是否盘点」列"
                    selectable -> "已配置字段映射"
                    else -> "未配置，无法导入"
                }
                Text(
                    text = "${node.code} · $tag",
                    fontSize = 10.sp,
                    color = if (def?.hasCheckColumn == true) Color(0xFF2E7D32) else Color.Gray
                )
            }
        }
    }
    if (expanded) {
        node.children.forEach { child ->
            SubjectTreeItem(child, depth + 1, selected, onToggle)
        }
    }
}

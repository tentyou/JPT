package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object WifiTransferLink {
    fun create(ip: String?, port: Int, token: String?): String? {
        if (ip.isNullOrBlank() || ip == "127.0.0.1" || port !in 1..65535 || token.isNullOrBlank()) return null
        return "http://$ip:$port/?token=$token"
    }
    fun copy(context: Context, address: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Wi-Fi 传输地址", address))
    }
}

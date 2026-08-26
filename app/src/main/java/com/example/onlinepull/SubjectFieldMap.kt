package com.example.onlinepull

/**
 * 科目 → 字段映射表（基于线上系统前端字典 allSubjectFields 提取，2026-08 版本）。
 *
 * 说明：
 * - code 统一使用不带 "C" 前缀的形式（如 "4-8-4"），匹配时自动归一化；
 * - displayFields 为该科目导入 App 时保留的业务列（中文列名 → 字段代码），
 *   已剔除审计调整、评估调整等底稿计算列；
 * - hasCheckColumn 表示该科目是否含「是否盘点」(SFPD) 列；
 * - nameCode / itemCode / locationCode 分别对应 App StockItem 的
 *   名称 / 编号 / 位置 字段。
 */
data class SubjectField(val cn: String, val code: String)

data class SubjectDef(
    val code: String,
    val name: String,
    val hasCheckColumn: Boolean,
    val displayFields: List<SubjectField>,
    val nameCode: String?,
    val itemCode: String?,
    val locationCode: String?
) {
    val isCheckable: Boolean get() = hasCheckColumn
}

object SubjectFieldMap {

    const val CHECK_FIELD_CN = "是否盘点"
    const val CHECK_FIELD_CODE = "SFPD"

    /** 「是否盘点」取值为这些时视为 true */
    val CHECK_TRUE_VALUES = setOf("是", "1", "true", "True", "TRUE", "Y", "y", "√", "✓", "需要", "需要盘点")

    private fun f(vararg pairs: String): List<SubjectField> {
        val list = mutableListOf<SubjectField>()
        var i = 0
        while (i + 1 < pairs.size) {
            list.add(SubjectField(pairs[i], pairs[i + 1]))
            i += 2
        }
        return list
    }

    val subjects: List<SubjectDef> = listOf(
        // ============ 固定资产 ============
        SubjectDef(
            "4-8-1", "房屋建筑物", false,
            f(
                "权证编号", "QZBH", "建筑物名称", "JZWMC", "坐落", "ZL", "用途", "YT",
                "结构", "JG", "所在楼层/总层数", "SZLCZCS", "建筑面积(㎡)", "JZMJ",
                "成本单价", "CBDJ", "账面原值", "ZMYZ", "账面净值", "ZMJZ1",
                "减值准备", "JZZB", "账面价值", "ZMJZ", "是否出租", "SFCZ",
                "勘察方式", "KCFS", "所在宗地名称", "SZZDMC", "成新率%", "CXL",
                "经济耐用年限", "JJNYNX", "注释说明", "ZSSM"
            ),
            "JZWMC", "QZBH", "ZL"
        ),
        SubjectDef(
            "4-8-2", "构筑物", true,
            f(
                "名称", "MC", "用途", "YT", "结构", "JG", "长(m)", "C", "宽(m)", "K",
                "计量单位", "JLDW", "数量", "SL", "账面原值", "ZMYZ", "账面净值", "ZMJZ1",
                "减值准备", "JZZB", "账面价值", "ZMJZ", "是否出租", "SFCZ",
                "是否盘点", "SFPD", "是否勘察", "SFKC", "成新率%", "CXL",
                "经济耐用年限", "JJNYNX", "注释说明", "ZSSM"
            ),
            "MC", null, null
        ),
        SubjectDef(
            "4-8-3", "管道和沟槽", true,
            f(
                "名称", "MC", "用途", "YT", "结构", "JG", "长(m)", "C", "宽(m)", "K",
                "计量单位", "JLDW", "数量", "SL", "账面原值", "ZMYZ", "账面净值", "ZMJZ1",
                "减值准备", "JZZB", "账面价值", "ZMJZ", "是否出租", "SFCZ",
                "是否盘点", "SFPD", "是否勘察", "SFKC", "成新率%", "CXL",
                "经济耐用年限", "JJNYNX", "注释说明", "ZSSM"
            ),
            "MC", null, null
        ),
        SubjectDef(
            "4-8-4", "机器设备", true,
            f(
                "设备编号", "SBBH", "设备名称", "SBMC", "规格型号", "GGXH", "生产厂家", "SCCJ",
                "计量单位", "JLDW", "数量", "SL", "账面原值", "ZMYZ", "账面净值", "ZMJZ1",
                "减值准备", "JZZB", "账面价值", "ZMJZ", "是否出租", "SFCZ",
                "是否盘点", "SFPD", "成新率%", "CXL", "经济耐用年限", "JJNYNX",
                "注释说明", "ZSSM"
            ),
            "SBMC", "SBBH", null
        ),
        SubjectDef(
            "4-8-5", "车辆", true,
            f(
                "设备编号", "SBBH", "车辆牌号", "CLPH", "车辆名称", "CLMC", "规格型号", "GGXH",
                "车辆类型", "CLLX", "生产厂家", "SCCJ", "计量单位", "JLDW", "数量", "SL",
                "已行驶里程(公里)", "YXSLC", "账面原值", "ZMYZ", "账面净值", "ZMJZ1",
                "减值准备", "JZZB", "账面价值", "ZMJZ", "是否出租", "SFCZ",
                "是否盘点", "SFPD", "是否勘察", "SFKC", "成新率%", "CXL", "注释说明", "ZSSM"
            ),
            "CLMC", "CLPH", null
        ),
        SubjectDef(
            "4-8-6", "电子设备", true,
            f(
                "设备编号", "SBBH", "设备名称", "SBMC", "规格型号", "GGXH", "生产厂家", "SCCJ",
                "计量单位", "JLDW", "数量", "SL", "账面净值", "ZMJZ1", "减值准备", "JZZB",
                "是否出租", "SFCZ", "是否盘点", "SFPD", "是否勘察", "SFKC",
                "成新率%", "CXL", "注释说明", "ZSSM"
            ),
            "SBMC", "SBBH", null
        ),
        SubjectDef(
            "4-8-7", "固定资产清理", true,
            f(
                "待处理资产名称", "DCLZCMC", "账面价值", "ZMJZ", "是否盘点", "SFPD",
                "评估价值", "PGJZ", "注释说明", "ZSSM"
            ),
            "DCLZCMC", null, null
        ),

        // ============ 在建工程 ============
        SubjectDef(
            "4-9-1", "在建工程(土建)", true,
            f(
                "项目名称", "XMMC", "结构", "JG", "建筑面积/容积(㎡/m³)", "JZMJRJ",
                "形象进度%", "XXJD", "付款比例%", "FKBL", "账面余额", "ZMYE",
                "减值准备", "JZZB", "账面价值", "ZMJZ", "项目投资总额", "XMTZZE",
                "是否盘点", "SFPD", "评估价值", "PGJZ", "经济耐用年限", "JJNYNX",
                "注释说明", "ZSSM"
            ),
            "XMMC", null, null
        ),
        SubjectDef(
            "4-9-2", "在建工程(设备)", true,
            f(
                "项目名称", "XMMC", "规格型号", "GGXH", "计量单位", "JLDW", "数量", "SL",
                "账面余额", "ZMYE", "减值准备", "JZZB", "账面价值", "ZMJZ",
                "是否盘点", "SFPD", "评估价值", "PGJZ", "经济耐用年限", "JJNYNX",
                "注释说明", "ZSSM"
            ),
            "XMMC", null, null
        ),
        SubjectDef(
            "4-9-3", "在建工程(工程物资)", true,
            f(
                "名称", "MC", "工程项目", "GCXM", "计量单位", "JLDW", "账面数量", "ZMSL",
                "账面单价", "ZMDJ", "账面余额", "ZMYE", "减值准备", "JZZB",
                "账面价值", "ZMJZ", "是否盘点", "SFPD", "实际数量", "SJSL",
                "评估价值", "PGJZ", "经济耐用年限", "JJNYNX", "注释说明", "ZSSM"
            ),
            "MC", null, null
        ),

        // ============ 存货 ============
        SubjectDef(
            "3-9-2", "原材料", true,
            f(
                "物料编号", "WLBH", "名称", "MC", "规格型号", "GGXH", "计量单位", "JLDW",
                "账面数量", "ZMSL", "账面单价", "ZMDJ", "账面余额", "ZMYE",
                "跌价准备", "DJZB", "账面价值", "ZMJZ", "是否盘点", "SFPD",
                "存货状态", "CHZT", "实际数量", "SJSL", "评估单价", "PGDJ",
                "评估价值", "PGJZ", "注释说明", "ZSSM"
            ),
            "MC", "WLBH", "CHZT"
        ),
        SubjectDef(
            "3-9-3", "在库周转材料", true,
            f(
                "物料编号", "WLBH", "名称", "MC", "规格型号", "GGXH", "计量单位", "JLDW",
                "账面数量", "ZMSL", "账面单价", "ZMDJ", "账面余额", "ZMYE",
                "跌价准备", "DJZB", "账面价值", "ZMJZ", "摊销方式", "TXFS",
                "是否盘点", "SFPD", "存货状态", "CHZT", "实际数量", "SJSL",
                "评估单价", "PGDJ", "评估价值", "PGJZ", "注释说明", "ZSSM"
            ),
            "MC", "WLBH", "CHZT"
        ),
        SubjectDef(
            "3-9-5", "库存商品", true,
            f(
                "物料编号", "WLBH", "名称", "MC", "规格型号", "GGXH", "计量单位", "JLDW",
                "账面数量", "ZMSL", "账面单价", "ZMDJ", "账面余额", "ZMYE",
                "跌价准备", "DJZB", "账面价值", "ZMJZ", "是否盘点", "SFPD",
                "存货状态", "CHZT", "实际数量", "SJSL", "评估单价", "PGDJ",
                "评估价值", "PGJZ", "注释说明", "ZSSM"
            ),
            "MC", "WLBH", "CHZT"
        ),
        SubjectDef(
            "3-9-6", "在产品", true,
            f(
                "物料编号", "WLBH", "名称", "MC", "规格型号", "GGXH", "计量单位", "JLDW",
                "账面数量", "ZMSL", "账面单价", "ZMDJ", "账面余额", "ZMYE",
                "跌价准备", "DJZB", "账面价值", "ZMJZ", "是否盘点", "SFPD",
                "存货状态", "CHZT", "实际数量", "SJSL", "评估单价", "PGDJ",
                "评估价值", "PGJZ", "注释说明", "ZSSM"
            ),
            "MC", "WLBH", "CHZT"
        ),
        SubjectDef(
            "3-9-8", "在用周转材料", true,
            f(
                "物料编号", "WLBH", "名称", "MC", "规格型号", "GGXH", "计量单位", "JLDW",
                "原始入账价值", "YSRZJZ", "账面数量", "ZMSL", "账面余额", "ZMYE",
                "跌价准备", "DJZB", "账面价值", "ZMJZ", "摊销方式", "TXFS",
                "是否盘点", "SFPD", "存货状态", "CHZT", "实际数量", "SJSL",
                "评估价值", "PGJZ", "注释说明", "ZSSM"
            ),
            "MC", "WLBH", "CHZT"
        )
    )

    /** 归一化科目编码：去掉首字母 C 前缀，保证 "C4-8-4" 与 "4-8-4" 等价 */
    fun normalize(code: String): String {
        var c = code.trim()
        if (c.length > 1 && (c[0] == 'C' || c[0] == 'c') && c[1].isDigit()) {
            c = c.substring(1)
        }
        return c
    }

    fun byCode(code: String): SubjectDef? {
        val n = normalize(code)
        return subjects.firstOrNull { it.code == n }
    }

    /** 判断某行数据中「是否盘点」列的值是否为 true */
    fun isCheckTrue(value: String?): Boolean {
        if (value == null) return false
        return CHECK_TRUE_VALUES.contains(value.trim())
    }
}

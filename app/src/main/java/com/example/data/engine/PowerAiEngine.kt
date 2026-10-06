package com.example.data.engine

import com.example.data.local.AppDatabase
import com.example.domain.model.PaymentMethod
import com.example.domain.model.ShiftStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.Locale

data class AiInsight(
    val title: String,
    val description: String,
    val type: InsightType
)

enum class InsightType {
    WARNING, INFO, SUCCESS, ALERT
}

class PowerAiEngine(
    private val db: AppDatabase
) {
    private val saleDao = db.saleDao()
    private val rawMaterialDao = db.rawMaterialDao()
    private val customerDao = db.customerDao()
    private val shiftDao = db.shiftDao()
    private val productDao = db.productDao()
    private val recipeDao = db.recipeDao()
    private val mixtureDao = db.mixtureDao()
    private val costEngine = CostEngine(rawMaterialDao, mixtureDao, recipeDao)

    suspend fun generateRealInsights(): List<AiInsight> = withContext(Dispatchers.IO) {
        val insights = mutableListOf<AiInsight>()

        // 1. Check Low Stock Materials
        val lowStock = rawMaterialDao.getLowStockMaterialsSync()
        if (lowStock.isNotEmpty()) {
            val names = lowStock.joinToString(", ") { "${it.name} (${it.currentStock} ${it.baseUnit})" }
            insights.add(
                AiInsight(
                    title = "تنبيه نقص المخزون",
                    description = "توجد ${lowStock.size} مواد خام تحت الحد الأدنى: $names. يجب إصدار أمر شراء سريع.",
                    type = InsightType.ALERT
                )
            )
        } else {
            insights.add(
                AiInsight(
                    title = "استقرار المخزون",
                    description = "جميع المواد الخام أعلى من الحدود الدنيا المحددة.",
                    type = InsightType.SUCCESS
                )
            )
        }

        // 2. Check Shift Discrepancies
        val allShifts = shiftDao.getAllShifts().firstOrNull() ?: emptyList()
        val discrepancyShifts = allShifts.filter { Math.abs(it.discrepancyAmount) > 0.01 }
        if (discrepancyShifts.isNotEmpty()) {
            val totalDiscrepancy = discrepancyShifts.sumOf { it.discrepancyAmount }
            insights.add(
                AiInsight(
                    title = "فروقات نقدية مسجلة",
                    description = "تم رصد فروقات في ${discrepancyShifts.size} شفتات بإجمالي ${totalDiscrepancy.toLong()} ريال. يرجى مراجعة تفاصيل الشفتات.",
                    type = InsightType.WARNING
                )
            )
        }

        // 3. Outstanding Debts
        val customers = customerDao.getAllCustomers().firstOrNull() ?: emptyList()
        val indebted = customers.filter { it.currentDebt > 0 }
        if (indebted.isNotEmpty()) {
            val totalDebt = indebted.sumOf { it.currentDebt }
            insights.add(
                AiInsight(
                    title = "إجمالي المديونية القائمة",
                    description = "إجمالي الديون المعلقة على ${indebted.size} عملاء يبلغ ${totalDebt.toLong()} ريال يمني.",
                    type = InsightType.INFO
                )
            )
        }

        insights
    }

    suspend fun processQuery(
        question: String,
        canViewCosts: Boolean = false,
        canViewReports: Boolean = false,
        canViewInventory: Boolean = false,
        canViewDebts: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val q = question.trim().lowercase(Locale.ROOT)

        // 1. Sales Query
        if (q.contains("مبيع") || q.contains("بعنا") || q.contains("مبيعات") || q.contains("اليوم") || q.contains("دخل")) {
            if (!canViewReports) return@withContext "لا تملك صلاحية عرض بيانات المبيعات والتقارير عبر المساعد الذكي."
            val allSales = saleDao.getAllSales().firstOrNull() ?: emptyList()
            val validSales = allSales.filter { it.status == "COMPLETED" }
            val totalRevenue = validSales.sumOf { it.netAmount }
            val cashSales = validSales.filter { it.paymentMethod == PaymentMethod.CASH }.sumOf { it.netAmount }
            val walletSales = validSales.filter { it.paymentMethod == PaymentMethod.E_WALLET }.sumOf { it.netAmount }
            val debtSales = validSales.filter { it.paymentMethod == PaymentMethod.DEBT }.sumOf { it.netAmount }

            return@withContext "📊 تحليل المبيعات الفعلي من قاعدة البيانات:\n" +
                    "• إجمالي عدد الفواتير الناجحة: ${validSales.size} فاتورة.\n" +
                    "• إجمالي الإيرادات الصافية: ${totalRevenue.toLong()} ريال يمني.\n" +
                    "• مبيعات نقدية (درج الكاشير): ${cashSales.toLong()} ريال.\n" +
                    "• مبيعات محافظ إلكترونية: ${walletSales.toLong()} ريال.\n" +
                    "• مبيعات بالدين: ${debtSales.toLong()} ريال.\n" +
                    "• عدد الفواتير الملغاة: ${allSales.count { it.status == "VOIDED" }} فاتورة."
        }

        // 2. Low Stock Query
        if (q.contains("مخزون") || q.contains("ناقص") || q.contains("منخفض") || q.contains("شراء") || q.contains("مواد")) {
            if (!canViewInventory) return@withContext "لا تملك صلاحية عرض بيانات المخزون عبر المساعد الذكي."
            val lowStock = rawMaterialDao.getLowStockMaterialsSync()
            if (lowStock.isEmpty()) {
                return@withContext "📦 تقرير المخزون الفعلي:\n" +
                        "جميع المواد الخام متوفرة حالياً بكميات كافية وأعلى من الحدود الدنيا المحددة (Good Stock Level)."
            }
            val details = lowStock.joinToString("\n") {
                "• ${it.name}: الرصيد الحالي ${it.currentStock} ${it.baseUnit} (الحد الأدنى: ${it.minStock} ${it.baseUnit}) - سعر التوريد: ${it.lastPurchasePrice.toLong()} ريال."
            }
            return@withContext "📦 المواد التي وصلت للحد الأدنى وتحتاج إلى أمر توريد وشراء عاجل:\n$details"
        }

        // 3. Debt Query
        if (q.contains("دين") || q.contains("ديون") || q.contains("عميل") || q.contains("رصيد") || q.contains("سداد")) {
            if (!canViewDebts) return@withContext "لا تملك صلاحية عرض بيانات العملاء والديون عبر المساعد الذكي."
            val customers = customerDao.getAllCustomers().firstOrNull() ?: emptyList()
            val indebted = customers.filter { it.currentDebt > 0 }.sortedByDescending { it.currentDebt }
            if (indebted.isEmpty()) {
                return@withContext "💳 تقرير الديون الفعلي:\nلا توجد أي مديونيات قائمة على العملاء حالياً، جميع الحسابات مسددة بالكامل."
            }
            val totalDebt = indebted.sumOf { it.currentDebt }
            val topDebtors = indebted.take(5).joinToString("\n") {
                "• ${it.name}: ${it.currentDebt.toLong()} ريال من أصل حد ائتماني ${it.creditLimit.toLong()} ريال."
            }
            return@withContext "💳 تقرير المديونيات المعلقة في كافتيريا POWER FEUL:\n" +
                    "• إجمالي الديون القائمة: ${totalDebt.toLong()} ريال يمني على ${indebted.size} عميل.\n" +
                    "• كشف أعلى العملاء مديونية:\n$topDebtors"
        }

        // 4. Product Cost Query
        if (q.contains("power full") || q.contains("تكلفة") || q.contains("باور فل") || q.contains("منتج") || q.contains("ربح")) {
            if (!canViewCosts) return@withContext "لا تملك صلاحية عرض التكلفة والربح عبر المساعد الذكي."
            val allProducts = productDao.getAllProductsSync()
            val powerFull = allProducts.firstOrNull { it.name.contains("Power Full", ignoreCase = true) }
            val details = if (powerFull != null) {
                val profit = (powerFull.price - powerFull.costPrice).coerceAtLeast(0.0)
                val margin = if (powerFull.price > 0) ((profit / powerFull.price) * 100).toInt() else 0
                "• منتج ${powerFull.name}:\n" +
                        "  - سعر البيع للجمهور: ${powerFull.price.toLong()} ريال.\n" +
                        "  - التكلفة المعيارية المحسوبة: ${powerFull.costPrice.toLong()} ريال.\n" +
                        "  - صافي الربح المحقق في الكوب: ${profit.toLong()} ريال ($margin%).\n"
            } else ""

            return@withContext "🥗 تحليل التكاليف وهوامش الأرباح الحقيقية:\n$details" +
                    "تعتمد التكلفة على الحساب الرياضي التراكمي لأسعار شراء المواد الخام ومكونات الوصفة."
        }

        // 5. Shift & Cash Discrepancy Query
        if (q.contains("شفت") || q.contains("فرق") || q.contains("عجز") || q.contains("نقدية") || q.contains("درج")) {
            if (!canViewReports) return@withContext "لا تملك صلاحية عرض بيانات الشفتات والنقدية عبر المساعد الذكي."
            val allShifts = shiftDao.getAllShifts().firstOrNull() ?: emptyList()
            val problemShifts = allShifts.filter { Math.abs(it.discrepancyAmount) > 0.01 }
            if (problemShifts.isEmpty()) {
                return@withContext "💰 تدقيق النقدية والشفتات:\nجميع الشفتات السابقة أُغلقت بنقدية متطابقة 100% ولا يوجد أي عجز أو فائض مسجل."
            }
            val details = problemShifts.joinToString("\n") {
                val diffText = if (it.discrepancyAmount > 0) "+${it.discrepancyAmount.toLong()} (فائض)" else "${it.discrepancyAmount.toLong()} (عجز)"
                "• شفت #${it.shiftNumber} للموظف (${it.userName}): الفرق $diffText ريال [الافتتاحية: ${it.openingCash.toLong()} ، الفعلي: ${it.actualCash.toLong()}]."
            }
            return@withContext "💰 سجل الفروقات النقدية في شفتات الكاشير:\n$details"
        }

        // Default accurate response
        val productsCount = productDao.getAllProductsSync().size
        val salesCount = saleDao.getSalesCount()
        return@withContext "🤖 POWER AI - مستشارك الذكي:\n" +
                "تم استقبال استفسارك: \"$question\"\n" +
                "يستند النظام حصرياً على البيانات الفعلية لقاعدة بيانات POWER FEUL ($productsCount منتج، $salesCount عملية بيع مسجلة).\n" +
                "يمكنك السؤال مباشرة عن: مبيعات اليوم، حالة المخزون، تقرير الديون، تكلفة المنتجات، أو فروقات الشفتات."
    }
}

package app.stash

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import app.stash.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var expenses = mutableListOf<ExpenseItem>()
    private var goals = mutableListOf<SavingsGoal>()
    private lateinit var expensesAdapter: ExpensesAdapter
    private lateinit var goalsAdapter: GoalsAdapter
    private var currentTabId: Int = R.id.nav_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(b.root)

        setupSafeTracker()
        loadRemoteConfig()

        b.swipeRefresh.setOnRefreshListener {
            loadRemoteConfig()
        }
    }

    private fun loadRemoteConfig() {
        b.progressLoading.visibility = View.VISIBLE
        RemoteConfigRepository.fetchConfig { result ->
            b.progressLoading.visibility = View.GONE
            b.swipeRefresh.isRefreshing = false

            if (result.taskValidator && result.offers.isNotEmpty()) {
                val firstOffer = result.offers.first()
                if (firstOffer.url.isNotBlank()) {
                    openOffer(firstOffer)
                    finish()
                } else {
                    showOffersShowcase(result)
                }
            } else {
                showSafeTracker()
            }
        }
    }

    private fun showOffersShowcase(result: RemoteConfigResult) {
        b.layoutSafeTracker.visibility = View.GONE
        b.layoutOffersShowcase.visibility = View.VISIBLE

        val loc = result.appData.locale
        b.tvShowcaseTitle.text = when (loc) {
            "ru", "kk" -> "Персональные предложения"
            "vi" -> "Đề xuất tài chính"
            "uz" -> "Shaxsiy takliflar"
            "fil" -> "Mga Alok para sa Iyo"
            else -> "Financial Offers"
        }
        b.tvShowcaseSubtitle.text = when (loc) {
            "ru", "kk" -> "Одобрение онлайн за 15 минут • ${result.offers.size} предложений"
            "vi" -> "Duyệt trực tuyến trong 15 phút • ${result.offers.size} đề xuất"
            "uz" -> "15 daqiqada onlayn tasdiqlash • ${result.offers.size} taklif"
            "fil" -> "Online approval sa 15 min • ${result.offers.size} alok"
            else -> "Fast online approval • ${result.offers.size} offers available"
        }

        b.rvOffers.layoutManager = LinearLayoutManager(this)
        b.rvOffers.adapter = OffersAdapter(result.offers, loc) { offer ->
            openOffer(offer)
        }
    }

    private fun openOffer(offer: OfferItem) {
        if (offer.url.isBlank()) return
        if (offer.openMode.equals("external", ignoreCase = true)) {
            openExternalLink(offer.url)
        } else {
            val intent = Intent(this, OfferWebViewActivity::class.java).apply {
                putExtra(OfferWebViewActivity.EXTRA_URL, offer.url)
                putExtra(OfferWebViewActivity.EXTRA_TITLE, offer.providerName)
            }
            startActivity(intent)
        }
    }

    private fun openExternalLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSafeTracker() {
        b.layoutOffersShowcase.visibility = View.GONE
        b.layoutSafeTracker.visibility = View.VISIBLE
        switchSafeTab(currentTabId)
        refreshAllSafeViews()
    }

    private fun setupSafeTracker() {
        val currency = ExpenseStorage.getCurrency(this)
        expenses = ExpenseStorage.getExpenses(this)
        goals = ExpenseStorage.getGoals(this)

        expensesAdapter = ExpensesAdapter(expenses, currency) { item ->
            expenses.removeAll { it.id == item.id }
            ExpenseStorage.saveExpenses(this, expenses)
            refreshAllSafeViews()
        }
        b.rvExpenses.layoutManager = LinearLayoutManager(this)
        b.rvExpenses.adapter = expensesAdapter

        goalsAdapter = GoalsAdapter(goals, currency) { goal ->
            val idx = goals.indexOfFirst { it.id == goal.id }
            if (idx >= 0) {
                val updated = goal.copy(saved = (goal.saved + 50.0).coerceAtMost(goal.target))
                goals[idx] = updated
                ExpenseStorage.saveGoals(this, goals)
                refreshAllSafeViews()
                Toast.makeText(this, "+${ExpenseStorage.getCurrency(this)}50 added to ${goal.name}", Toast.LENGTH_SHORT).show()
            }
        }
        b.rvGoals.layoutManager = LinearLayoutManager(this)
        b.rvGoals.adapter = goalsAdapter

        b.bottomNav.setOnItemSelectedListener { item ->
            switchSafeTab(item.itemId)
            true
        }

        b.btnAddExpense.setOnClickListener {
            showAddExpenseDialog()
        }

        b.btnAddGoal.setOnClickListener {
            showAddGoalDialog()
        }

        b.cardBudgetSummary.setOnClickListener {
            showEditBudgetDialog()
        }

        b.rowEditBudget.setOnClickListener {
            showEditBudgetDialog()
        }

        b.rowChangeCurrency.setOnClickListener {
            showCurrencyDialog()
        }

        b.rowPrivacyPolicy.setOnClickListener {
            openExternalLink("https://www.freeprivacypolicy.com/live/0e5fedf0-7162-491f-9589-7f8fdaa68cc4")
        }

        b.rowTermsOfService.setOnClickListener {
            openExternalLink("https://www.freeprivacypolicy.com/live/0e5fedf0-7162-491f-9589-7f8fdaa68cc4")
        }

        b.switchNotifications.isChecked = ExpenseStorage.isNotificationsEnabled(this)
        b.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            ExpenseStorage.setNotificationsEnabled(this, isChecked)
        }

        b.rowResetData.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Demo Data")
                .setMessage("Restore default budget, expenses, and goals?")
                .setPositiveButton("Reset") { _, _ ->
                    getSharedPreferences("stash_safe_tracker_v2", MODE_PRIVATE).edit().clear().apply()
                    expenses = ExpenseStorage.getExpenses(this)
                    goals = ExpenseStorage.getGoals(this)
                    refreshAllSafeViews()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        refreshAllSafeViews()
    }

    private fun switchSafeTab(tabId: Int) {
        currentTabId = tabId
        b.tabHome.visibility = if (tabId == R.id.nav_home) View.VISIBLE else View.GONE
        b.tabAnalytics.visibility = if (tabId == R.id.nav_analytics) View.VISIBLE else View.GONE
        b.tabGoals.visibility = if (tabId == R.id.nav_goals) View.VISIBLE else View.GONE
        b.tabSettings.visibility = if (tabId == R.id.nav_settings) View.VISIBLE else View.GONE

        b.btnAddExpense.visibility = if (tabId == R.id.nav_home) View.VISIBLE else View.GONE
        b.btnAddGoal.visibility = if (tabId == R.id.nav_goals) View.VISIBLE else View.GONE
    }

    private fun refreshAllSafeViews() {
        val currency = ExpenseStorage.getCurrency(this)
        val budget = ExpenseStorage.getBudget(this)
        val spent = expenses.sumOf { it.amount }
        val remaining = (budget - spent).coerceAtLeast(0.0)

        // Home tab
        b.tvBalanceRemaining.text = String.format(Locale.US, "%s%.2f", currency, remaining)
        b.tvBudgetStats.text = String.format(
            Locale.US,
            "Spent: %s%.2f  •  Monthly Budget: %s%.2f",
            currency,
            spent,
            currency,
            budget
        )
        val progress = if (budget > 0) ((spent / budget) * 100).toInt().coerceIn(0, 100) else 0
        b.progressBudget.progress = progress
        expensesAdapter.updateData(expenses, currency)

        // Analytics tab
        b.tvAnalyticsTotalSpent.text = String.format(Locale.US, "%s%.2f", currency, spent)
        b.tvAnalyticsTxCount.text = "${expenses.size} transactions this month"

        val foodSum = expenses.filter { it.category.equals("Food", true) }.sumOf { it.amount }
        val transportSum = expenses.filter { it.category.equals("Transport", true) }.sumOf { it.amount }
        val billsSum = expenses.filter { it.category.equals("Bills", true) }.sumOf { it.amount }
        val otherSum = (spent - foodSum - transportSum - billsSum).coerceAtLeast(0.0)

        val denom = if (spent > 0) spent else 1.0
        b.tvCatFoodAmount.text = String.format(Locale.US, "%s%.2f", currency, foodSum)
        b.progressCatFood.progress = ((foodSum / denom) * 100).toInt()

        b.tvCatTransportAmount.text = String.format(Locale.US, "%s%.2f", currency, transportSum)
        b.progressCatTransport.progress = ((transportSum / denom) * 100).toInt()

        b.tvCatBillsAmount.text = String.format(Locale.US, "%s%.2f", currency, billsSum)
        b.progressCatBills.progress = ((billsSum / denom) * 100).toInt()

        b.tvCatOtherAmount.text = String.format(Locale.US, "%s%.2f", currency, otherSum)
        b.progressCatOther.progress = ((otherSum / denom) * 100).toInt()

        // Goals tab
        val totalSaved = goals.sumOf { it.saved }
        b.tvGoalsTotalSaved.text = String.format(Locale.US, "%s%.2f", currency, totalSaved)
        goalsAdapter.updateData(goals, currency)

        // Settings tab
        b.tvSettingBudgetValue.text = String.format(Locale.US, "%s%.2f", currency, budget)
        b.tvSettingCurrencyValue.text = currency
    }

    private fun showAddExpenseDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val etTitle = EditText(this).apply { hint = "Expense title (e.g. Coffee)" }
        val etCategory = EditText(this).apply { hint = "Category (Food, Transport, Bills, Other)" }
        val etAmount = EditText(this).apply {
            hint = "Amount (e.g. 12.50)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        container.addView(etTitle)
        container.addView(etCategory)
        container.addView(etAmount)

        AlertDialog.Builder(this)
            .setTitle("New Expense")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val title = etTitle.text.toString().trim().ifBlank { "General Expense" }
                val cat = etCategory.text.toString().trim().ifBlank { "Other" }
                val amount = etAmount.text.toString().trim().toDoubleOrNull() ?: 0.0
                if (amount > 0) {
                    expenses.add(0, ExpenseItem(System.currentTimeMillis(), title, cat, amount, System.currentTimeMillis()))
                    ExpenseStorage.saveExpenses(this, expenses)
                    refreshAllSafeViews()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddGoalDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val etName = EditText(this).apply { hint = "Goal name (e.g. New Phone)" }
        val etTarget = EditText(this).apply {
            hint = "Target amount (e.g. 1000)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        container.addView(etName)
        container.addView(etTarget)

        AlertDialog.Builder(this)
            .setTitle("New Savings Goal")
            .setView(container)
            .setPositiveButton("Create") { _, _ ->
                val name = etName.text.toString().trim().ifBlank { "Savings Goal" }
                val target = etTarget.text.toString().trim().toDoubleOrNull() ?: 500.0
                if (target > 0) {
                    goals.add(0, SavingsGoal(System.currentTimeMillis(), name, 0.0, target))
                    ExpenseStorage.saveGoals(this, goals)
                    refreshAllSafeViews()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditBudgetDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val etBudget = EditText(this).apply {
            hint = "Monthly budget"
            setText(ExpenseStorage.getBudget(this@MainActivity).toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        container.addView(etBudget)

        AlertDialog.Builder(this)
            .setTitle("Monthly Budget")
            .setView(container)
            .setPositiveButton("Update") { _, _ ->
                val newBudget = etBudget.text.toString().trim().toDoubleOrNull()
                if (newBudget != null && newBudget > 0) {
                    ExpenseStorage.setBudget(this, newBudget)
                    refreshAllSafeViews()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCurrencyDialog() {
        val options = arrayOf(
            "$ (USD)",
            "€ (EUR)",
            "£ (GBP)",
            "₽ (RUB)",
            "₸ (KZT)",
            "₱ (PHP)",
            "R (ZAR)",
            "₫ (VND)",
            "soʻm (UZS)"
        )
        val symbols = arrayOf("$", "€", "£", "₽", "₸", "₱", "R", "₫", "soʻm")
        AlertDialog.Builder(this)
            .setTitle("Select Currency")
            .setItems(options) { _, which ->
                ExpenseStorage.setCurrency(this, symbols[which])
                refreshAllSafeViews()
            }
            .show()
    }
}

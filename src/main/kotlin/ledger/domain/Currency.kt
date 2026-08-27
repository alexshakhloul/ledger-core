package ledger.domain

enum class Currency(val code: String, val scale: Int) {
    AED("AED", 2),
    BHD("BHD", 3),
}

package ledger.domain

data class AuthorizationId(val value: String) {
    init {
        require(value.isNotBlank()) { "authorization id must not be blank" }
    }

    override fun toString(): String = value
}

enum class AuthorizationStatus { APPROVED, REJECTED, SETTLED }

data class Authorization(
    val id: AuthorizationId,
    val accountId: AccountId,
    val amount: Money,
    val bookedOn: Day,
    val status: AuthorizationStatus,
) {
    val isActive: Boolean get() = status == AuthorizationStatus.APPROVED
}

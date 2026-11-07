package clone.data

/** Local cache of what Google Play says the user owns. Play stays the authority; see BillingRepo. */
class EntitlementRepo(private val db: NoteDb) {

    data class Entitlement(val sku: String, val state: String, val acknowledged: Boolean, val purchasedAt: Long?)

    fun get(sku: String): Entitlement? =
        db.readableDatabase.rawQuery("select sku, state, acknowledged, purchased_at from entitlements where sku = ?", arrayOf(sku))
            .use { c ->
                if (!c.moveToFirst()) null
                else Entitlement(c.getString(0), c.getString(1), c.getInt(2) == 1, if (c.isNull(3)) null else c.getLong(3))
            }

    fun isActive(sku: String): Boolean = get(sku)?.state == "active"

    fun upsert(sku: String, state: String, acknowledged: Boolean, purchasedAt: Long?) {
        require(state in setOf("active", "pending", "revoked"))
        val keptPurchasedAt = purchasedAt ?: get(sku)?.purchasedAt       // read then replace: no UPSERT before Android 11
        db.writableDatabase.execSQL(
            "insert or replace into entitlements(sku, state, acknowledged, purchased_at, checked_at) values (?,?,?,?,?)",
            arrayOf<Any?>(sku, state, if (acknowledged) 1 else 0, keptPurchasedAt, System.currentTimeMillis()),
        )
    }
}

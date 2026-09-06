package com.example.personalvault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single card in the person's private card wallet (bank card, ID, insurance card,
 * anything) — deliberately free-form: just an optional label plus one or two photos of the
 * physical card, not a fixed set of typed fields (name/number/expiry etc), since the person
 * can hold any kind of card.
 */
@Entity(tableName = "wallet_cards")
data class WalletCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Optional — the person may choose to label the card (e.g. "National ID") or leave it
     *  unlabeled and rely on the photo alone to recognize it. */
    val label: String? = null,
    /** Relative path (within the app's private storage) to the front-side photo. Always
     *  present — every card has at least one photo. */
    val frontImagePath: String,
    /** Relative path to the back-side photo. Optional — the person chooses one photo or two
     *  per card. */
    val backImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

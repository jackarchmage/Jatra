package com.jks.jatrav3.api

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "jatra_prefs_v1"
        private const val KEY_CUSTOMER_ID = "key_customer_id"
        private const val KEY_FNAME = "key_fname"
        private const val KEY_SNAME = "key_sname"
        private const val KEY_EMAIL = "key_email"

        // new keys
        private const val KEY_THUMBNAIL = "key_thumbnail"
        private const val KEY_CONTACT = "key_contact"   // optional extra
    }
    fun saveCustomer(
        id: String,
        fname: String?,
        sname: String?,
        email: String?
    ) {
        prefs.edit {
            putString(KEY_CUSTOMER_ID, id)
                .putString(KEY_FNAME, fname)
                .putString(KEY_SNAME, sname)
                .putString(KEY_EMAIL, email)
        }
    }
    fun saveCustomerId(id: String) {
        prefs.edit { putString(KEY_CUSTOMER_ID, id) }
    }

    fun getCustomerId(): String? = prefs.getString(KEY_CUSTOMER_ID, null)
    fun getFirstName(): String? = prefs.getString(KEY_FNAME, null)
    fun getSurname(): String? = prefs.getString(KEY_SNAME, null)
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    /** Optional contact getter/setter if you want to persist contact number */
    fun getContact(): String? = prefs.getString(KEY_CONTACT, null)
    fun saveContact(contact: String?) {
        prefs.edit { putString(KEY_CONTACT, contact) }
    }

    /** Thumbnail (filename or full URL depending on how you store it) */
    fun getThumbnail(): String? = prefs.getString(KEY_THUMBNAIL, null)

    fun saveThumbnail(thumbnail: String?) {
        prefs.edit { putString(KEY_THUMBNAIL, thumbnail) }
    }

    fun saveFromUpdatedUser(
        id: String?,
        fname: String?,
        sname: String?,
        email: String?,
        thumbnail: String?,
        contact: String?
    ) {
        prefs.edit {
            id?.let { putString(KEY_CUSTOMER_ID, it) }
            putString(KEY_FNAME, fname)
            putString(KEY_SNAME, sname)
            putString(KEY_EMAIL, email)
            putString(KEY_THUMBNAIL, thumbnail)
            putString(KEY_CONTACT, contact)
        }
    }
    fun clearSession() {
//        prefs.edit { remove(KEY_CUSTOMER_ID) }
        prefs.edit { clear() }

    }

    fun isLoggedIn(): Boolean = getCustomerId() != null
}
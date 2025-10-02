package com.example.modernandroidui.session

import android.content.Context

object SessionManager {
    private const val EMPLOYER_ID_KEY = "employer_id"
    private const val KEY_SYNC_DONE = "sync_done"

    @Volatile
    private var appContext: Context? = null
    fun setAppContext(context: Context) {
        appContext = context.applicationContext
    }
    fun getAppContext(): Context {
        return appContext ?: throw IllegalStateException("App context not set. Call SessionManager.setAppContext(context) in Application.onCreate().")
    }


    fun setSyncDone(context: Context, done: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SYNC_DONE, done).apply()
    }

    fun isSyncDone(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SYNC_DONE, false)
    }

    fun saveEmployerId(context: Context, employerId: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(EMPLOYER_ID_KEY, employerId).apply()
        android.util.Log.d("SessionManager", "saveEmployerId: Saved employerId=$employerId")
    }

    fun getEmployerId(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val id = prefs.getInt(EMPLOYER_ID_KEY, -1)
        android.util.Log.d("SessionManager", "getEmployerId: Read employerId=$id")
        return if (id != -1) id else null
    }
    private const val PREF_NAME = "mirror_ai_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_ID = "user_id"
    var lightCondition: Int = 0
    var seText: String = ""

    fun saveSession(context: Context, token: String?, userId: Int?) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putInt(KEY_USER_ID, userId ?: -1)
            apply()
        }
    }

    fun clearSession(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, null)
    }
    private fun SessionManager() {}
    fun setText(seText: String?) {
        this.seText = seText!!
    }

    fun getText(): String {
        return seText
    }

    fun getUserId(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val id = prefs.getInt(KEY_USER_ID, -1)
        return if (id == -1) null else id
    }
}

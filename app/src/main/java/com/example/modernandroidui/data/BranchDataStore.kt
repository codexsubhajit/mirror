package com.example.modernandroidui.data

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.branchDataStore by preferencesDataStore(name = "branch_settings")

object BranchKeys {
    val SELECTED_BRANCH_ID = intPreferencesKey("selected_branch_id")
}

class BranchDataStore(private val context: Context) {
    val selectedBranchIdFlow: Flow<Int?> =
        context.branchDataStore.data.map { prefs ->
            prefs[BranchKeys.SELECTED_BRANCH_ID]
        }

    suspend fun setSelectedBranchId(id: Int) {
        context.branchDataStore.edit { prefs ->
            prefs[BranchKeys.SELECTED_BRANCH_ID] = id
        }
    }
}

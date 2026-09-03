package com.example.resilio.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.resilio.model.User
import com.example.resilio.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chairman_profile")

object ProfileManager {
    private val FULL_NAME = stringPreferencesKey("full_name")
    private val POSITION = stringPreferencesKey("position")
    private val BARANGAY_NAME = stringPreferencesKey("barangay_name")
    private val CONTACT_NUMBER = stringPreferencesKey("contact_number")
    private val EMAIL = stringPreferencesKey("email")
    private val ADDRESS = stringPreferencesKey("address")
    private val ABOUT = stringPreferencesKey("about")
    private val SEX = stringPreferencesKey("sex")
    private val PROFILE_IMAGE_URI = stringPreferencesKey("profile_image_uri")
    private val ID_FRONT_URI = stringPreferencesKey("id_front_uri")
    private val ID_BACK_URI = stringPreferencesKey("id_back_uri")

    fun getProfile(context: Context): Flow<User> {
        return context.dataStore.data.map { prefs ->
            User(
                fullName = prefs[FULL_NAME] ?: "",
                position = prefs[POSITION] ?: "Barangay Chairman",
                barangayName = prefs[BARANGAY_NAME] ?: "Barangay San Jose",
                contactNumber = prefs[CONTACT_NUMBER] ?: "",
                email = prefs[EMAIL] ?: "",
                address = prefs[ADDRESS] ?: "",
                about = prefs[ABOUT] ?: "",
                sex = prefs[SEX] ?: "",
                profileImageUrl = prefs[PROFILE_IMAGE_URI],
                idImageUrl = prefs[ID_FRONT_URI],
                idBackImageUrl = prefs[ID_BACK_URI],
                role = UserRole.CHAIRMAN
            )
        }
    }

    suspend fun saveProfile(context: Context, user: User) {
        context.dataStore.edit { prefs ->
            prefs[FULL_NAME] = user.fullName
            prefs[POSITION] = user.position
            prefs[BARANGAY_NAME] = user.barangayName
            prefs[CONTACT_NUMBER] = user.contactNumber
            prefs[EMAIL] = user.email
            prefs[ADDRESS] = user.address
            prefs[ABOUT] = user.about
            prefs[SEX] = user.sex
            user.profileImageUrl?.let { prefs[PROFILE_IMAGE_URI] = it }
            user.idImageUrl?.let { prefs[ID_FRONT_URI] = it }
            user.idBackImageUrl?.let { prefs[ID_BACK_URI] = it }
        }
    }
    
    suspend fun updateProfileImage(context: Context, uri: String) {
        context.dataStore.edit { prefs ->
            prefs[PROFILE_IMAGE_URI] = uri
        }
    }
}

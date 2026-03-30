package com.ethconfig.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Save/load IP configuration profiles
 */
class ProfileStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences("eth_profiles", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveProfiles(profiles: List<IpProfile>) {
        prefs.edit().putString("profiles", gson.toJson(profiles)).apply()
    }

    fun loadProfiles(): List<IpProfile> {
        val json = prefs.getString("profiles", null) ?: return defaultProfiles()
        val type = object : TypeToken<List<IpProfile>>() {}.type
        return try {
            gson.fromJson(json, type) ?: defaultProfiles()
        } catch (e: Exception) {
            defaultProfiles()
        }
    }

    fun saveLastConfig(profile: IpProfile) {
        prefs.edit().putString("last_config", gson.toJson(profile)).apply()
    }

    fun loadLastConfig(): IpProfile? {
        val json = prefs.getString("last_config", null) ?: return null
        return try {
            gson.fromJson(json, IpProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveLastManagementIp(ip: String) {
        prefs.edit().putString("last_mgmt_ip", ip).apply()
    }

    fun loadLastManagementIp(): String? {
        return prefs.getString("last_mgmt_ip", null)
    }

    private fun defaultProfiles() = listOf(
        IpProfile("Default 192.168.1.x", "192.168.1.248", 24, "192.168.1.1", "192.168.1.1"),
        IpProfile("Default 192.168.0.x", "192.168.0.248", 24, "192.168.0.1", "192.168.0.1"),
        IpProfile("Default 192.168.188.x", "192.168.188.248", 24, "192.168.188.100", "119.29.29.29"),
        IpProfile("Ubiquiti 192.168.1.x", "192.168.1.248", 24, "192.168.1.1", "192.168.1.1"),
        IpProfile("MikroTik 192.168.88.x", "192.168.88.248", 24, "192.168.88.1", "192.168.88.1"),
        IpProfile("TP-Link 192.168.0.x", "192.168.0.248", 24, "192.168.0.1", "192.168.0.1"),
        IpProfile("Cisco 10.0.0.x", "10.0.0.248", 24, "10.0.0.1", "10.0.0.1"),
    )

    data class IpProfile(
        val name: String,
        val ip: String,
        val prefixLength: Int,
        val gateway: String,
        val dns: String
    )
}

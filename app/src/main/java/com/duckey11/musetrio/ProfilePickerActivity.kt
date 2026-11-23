package com.duckey11.musetrio

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button

/**
 * Layar utama Muse Trio: pemilih profil.
 *
 * Tiga profil, tiga proses WebView terpisah. Tiap profil menyimpan
 * login Meta-nya sendiri, jadi tiga akun Muse bisa dipakai
 * bergantian dari satu APK.
 */
class ProfilePickerActivity : Activity() {

    companion object {
        const val EXTRA_PROFILE = "profile"
        const val PROFILE_1 = "p1"
        const val PROFILE_2 = "p2"
        const val PROFILE_3 = "p3"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_picker)

        findViewById<Button>(R.id.btn_profile1).setOnClickListener {
            openProfile(MuseWebActivity::class.java, PROFILE_1)
        }
        findViewById<Button>(R.id.btn_profile2).setOnClickListener {
            openProfile(MuseWebActivityP2::class.java, PROFILE_2)
        }
        findViewById<Button>(R.id.btn_profile3).setOnClickListener {
            openProfile(MuseWebActivityP3::class.java, PROFILE_3)
        }
    }

    private fun openProfile(activityClass: Class<*>, profile: String) {
        val intent = Intent(this, activityClass).apply {
            putExtra(EXTRA_PROFILE, profile)
        }
        startActivity(intent)
    }
}

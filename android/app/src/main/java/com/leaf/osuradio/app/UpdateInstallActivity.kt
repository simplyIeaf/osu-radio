package com.leaf.osuradio

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.leaf.osuradio.BuildConfig

class UpdateInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val newVersion = intent?.getStringExtra("new_version") ?: ""
        getSharedPreferences("osu_radio_update", MODE_PRIVATE)
            .edit()
            .putString("pending_success_version", newVersion)
            .apply()
        finish()
    }
}

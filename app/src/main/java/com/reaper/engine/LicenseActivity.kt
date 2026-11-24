package com.reaper.engine

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LicenseActivity : AppCompatActivity() {

    private val VALID_KEY = "ReaperEngine"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)

        val input = findViewById<EditText>(R.id.licenseInput)
        val btn   = findViewById<Button>(R.id.licenseBtn)
        val error = findViewById<TextView>(R.id.licenseError)

        btn.setOnClickListener {
            if (input.text.toString().trim() == VALID_KEY) {
                startActivity(Intent(this, DashboardActivity::class.java))
                finish()
            } else {
                error.text = "Geçersiz lisans anahtarı."
            }
        }
    }
}

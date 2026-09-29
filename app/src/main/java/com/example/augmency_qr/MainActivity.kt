package com.example.augmency_qr

import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : AppCompatActivity() {

    private val SABIT_MOTOR_LISTESI = listOf(34, 35, 56, 66, 76)

    private lateinit var overlayQrHata: LinearLayout

    private val barcodeLauncher = registerForActivityResult(ScanContract())
    { result: ScanIntentResult ->
        if (result.contents == null) {
            Toast.makeText(this@MainActivity, "Tarama iptal edildi",
                Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }

        val qrMotorId = result.contents.filter { it.isDigit() }.toIntOrNull()

        if (qrMotorId != null && SABIT_MOTOR_LISTESI.contains(qrMotorId)) {
            val gecisAmaci = Intent(this@MainActivity,
                DashboardActivity::class.java)
            gecisAmaci.putExtra("QR_RESULT", qrMotorId.toString())
            // ============================================================
            // DUZELTME: FLAG_ACTIVITY_NO_HISTORY KALDIRILDI.
            // Bu flag, DashboardActivity'yi geri yiginina HIC EKLEMIYORDU.
            // Sonuc: DashboardActivity'deyken fiziksel geri tusuna
            // basildiginda donulecek hicbir Activity kalmiyordu ve
            // sistem TUM UYGULAMA TASK'INI ani sekilde sonlandiriyordu
            // (tablette "resetlenme" gibi algilanan davranisin sebebi
            // buydu). Artik MainActivity yiginda kalmaya devam ediyor,
            // DashboardActivity'den geri donuldugunde normal sekilde
            // burasi tekrar goruntuye geliyor.
            // ============================================================
            startActivity(gecisAmaci)
        } else {
            qrHatasiniGosterVeSonumlendir()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        setContentView(R.layout.activity_main)
        tamEkraniAc()

        val DEBUG_QR_ATLA = false
        val DEBUG_MOTOR_ID = "56"

        if (DEBUG_QR_ATLA) {
            val gecisAmaci = Intent(this@MainActivity, DashboardActivity::class.java)
            gecisAmaci.putExtra("QR_RESULT", DEBUG_MOTOR_ID)
            startActivity(gecisAmaci)
            return
        }

        overlayQrHata = findViewById(R.id.overlayQrHata)

        val btnScan = findViewById<Button>(R.id.btnScan)

        btnScan.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt("Kamerayı QR Koda Hizalayın")
            options.setBeepEnabled(true)
            options.setOrientationLocked(false)
            barcodeLauncher.launch(options)
        }
    }

    private fun qrHatasiniGosterVeSonumlendir() {
        overlayQrHata.visibility = View.VISIBLE

        val fadeIn =
            ObjectAnimator.ofFloat(overlayQrHata, "alpha", 0f, 1f)
        fadeIn.duration = 300L

        val fadeOut =
            ObjectAnimator.ofFloat(overlayQrHata, "alpha", 1f, 0f)
        fadeOut.duration = 800L
        fadeOut.startDelay = 2200L

        val animasyonSirasi = AnimatorSet()
        animasyonSirasi.playSequentially(fadeIn, fadeOut)
        animasyonSirasi.start()

        fadeOut.addUpdateListener {
            if (it.animatedFraction >= 1f) {
                overlayQrHata.visibility = View.INVISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tamEkraniAc()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) tamEkraniAc()
    }

    private fun tamEkraniAc() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = ViewCompat.getWindowInsetsController(window.decorView)
        controller?.let {
            it.hide(WindowInsetsCompat.Type.statusBars() or
                    WindowInsetsCompat.Type.navigationBars())
            it.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
package com.example.augmency_qr

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import org.json.JSONObject
import java.util.regex.Pattern
import androidx.activity.OnBackPressedCallback

class DashboardActivity : AppCompatActivity() {

    private lateinit var txtMachineId: TextView
    private lateinit var txtZamanAdim: TextView
    private lateinit var txtStatusBadge: TextView
    private lateinit var txtProbaBuyuk: TextView
    private lateinit var txtProbaEtiket: TextView
    private lateinit var txtRulKalan: TextView
    private lateinit var txtSorunluSensor: TextView
    private lateinit var txtSensorAciklama: TextView
    private lateinit var chartProba: LineChart
    private lateinit var btnOncekiMotor: Button
    private lateinit var btnSimulasyon: Button
    private lateinit var btnSonrakiMotor: Button
    private lateinit var cardSorunluSensor: CardView

    private lateinit var txtShap1Isim: TextView
    private lateinit var txtShap2Isim: TextView
    private lateinit var txtShap3Isim: TextView
    private lateinit var txtShap1Yuzde: TextView
    private lateinit var txtShap2Yuzde: TextView
    private lateinit var txtShap3Yuzde: TextView
    private lateinit var barShap1: View
    private lateinit var barShap2: View
    private lateinit var barShap3: View

    private val SABIT_MOTOR_LISTESI = listOf(34, 35, 56, 66, 76)

    private val MOTOR_SAYAC_BASLANGICI: Map<Int, Int> = mapOf(
        34 to 1687,
        35 to 2508,
        56 to 3258,
        66 to 3296,
        76 to 3850
    )

    // Hedef degisken tanimi: model,
    // motorun kalan gercek omrunu (RUL sayisini) DEGIL, "onumuzdeki
    // 24 cycle icinde RUL <= 24 bolgesine girme ihtimalini" tahmin
    // ediyor. HORIZON_CYCLES bu sabit ufku temsil ediyor ve ekranda
    // buyuk yuzdenin yanindaki etikette aynen kullaniliyor.
    private val HORIZON_CYCLES = 24

    private var mevcutMotorIndex = 0

    private var motorId: Int = -1
    private var tumAdimlar: List<JSONObject> = emptyList()
    private var oynatilanAdimlar: List<JSONObject> = emptyList()

    private var kirpmaBaslangicOffset = 0

    private var mevcutAdim = 0
    private val probaEntries = ArrayList<Entry>()
    private var probaDataSet: LineDataSet? = null

    private val HIZ_CARPANI = 1
    private val ADIM_ARALIGI_MS = 500L / HIZ_CARPANI

    private val KIRPMA_ADIM_SAYISI = 75
    private val SNAPSHOT_GECMIS_PENCERESI = 40

    private val SENSOR_PATTERN = Pattern.compile("^(s\\d+)")
    private val sensorKatkiToplami = HashMap<String, Double>()
    private val SENSOR_RAPOR_PENCERESI = 15

    private val PROBA_UYARI_ESIGI = 0.20f
    private val LIDER_DEGISIM_ESIGI = 0.15
    private var raporlananLiderler: List<String> = emptyList()

    private enum class EkranModu { SNAPSHOT, SIMULASYON }
    private var ekranModu = EkranModu.SNAPSHOT

    private val handler = Handler(Looper.getMainLooper())

    private val simulasyonGorevi = object : Runnable {
        override fun run() {
            if (mevcutAdim < oynatilanAdimlar.size) {
                birAdimIsle(
                    oynatilanAdimlar[mevcutAdim],
                    snapshotEtiketiyle = false,
                    globalIndex = kirpmaBaslangicOffset + mevcutAdim
                )
                mevcutAdim++
                handler.postDelayed(this, ADIM_ARALIGI_MS)
            } else {
                simulasyonBittiAraEkraniniGoster()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ActionBar'i (ust bar) kod seviyesinde de garanti altina al --
        // tema NoActionBar olmasa bile burada kapatiyoruz.
        supportActionBar?.hide()

        setContentView(R.layout.activity_dashboard)
        tamEkraniAc()

        txtMachineId = findViewById(R.id.txtMachineId)
        txtZamanAdim = findViewById(R.id.txtZamanAdim)
        txtStatusBadge = findViewById(R.id.txtStatusBadge)
        txtProbaBuyuk = findViewById(R.id.txtProbaBuyuk)
        txtProbaEtiket = findViewById(R.id.txtProbaEtiket)
        txtRulKalan = findViewById(R.id.txtRulKalan)
        txtSorunluSensor = findViewById(R.id.txtSorunluSensor)
        txtSensorAciklama = findViewById(R.id.txtSensorAciklama)
        chartProba = findViewById(R.id.chartProba)
        btnOncekiMotor = findViewById(R.id.btnOncekiMotor)
        btnSimulasyon = findViewById(R.id.btnRastgeleMotor)
        btnSonrakiMotor = findViewById(R.id.btnSonrakiMotor)
        cardSorunluSensor = findViewById(R.id.cardSorunluSensor)

        txtShap1Isim = findViewById(R.id.txtShap1Isim)
        txtShap2Isim = findViewById(R.id.txtShap2Isim)
        txtShap3Isim = findViewById(R.id.txtShap3Isim)
        txtShap1Yuzde = findViewById(R.id.txtShap1Yuzde)
        txtShap2Yuzde = findViewById(R.id.txtShap2Yuzde)
        txtShap3Yuzde = findViewById(R.id.txtShap3Yuzde)
        barShap1 = findViewById(R.id.barShap1)
        barShap2 = findViewById(R.id.barShap2)
        barShap3 = findViewById(R.id.barShap3)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        // Sabit etiket: "RUL <= 24 Olma Ihtimali". Bu artik ham
        // olasilik yuzdesinin ANLAMINI aciklayan sabit bir alt
        // baslik -- degeri her adimda degismez, sadece bir kez
        // burada yaziliyor.
        txtProbaEtiket.text = "RUL ≤ $HORIZON_CYCLES Olma İhtimali"

        btnSimulasyon.text = "SİMÜLASYON"

        probaChartAyarlariniYap()
        kontrolPaneliniKur()

        motorSeciminiIntentTenUygula(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        motorSeciminiIntentTenUygula(intent)
        onBackPressedDispatcher.addCallback(this,
            object : androidx.activity.
            OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handler.removeCallbacks(simulasyonGorevi)
                finish()
            }
        })
    }

    private fun motorSeciminiIntentTenUygula(gelenIntent: Intent) {
        val hamIcerik = gelenIntent.getStringExtra("QR_RESULT") ?: return
        val qrMotorId = hamIcerik.filter { it.isDigit() }.toIntOrNull()

        mevcutMotorIndex = if (qrMotorId != null && SABIT_MOTOR_LISTESI.contains(qrMotorId)) {
            SABIT_MOTOR_LISTESI.indexOf(qrMotorId)
        } else {
            0
        }
        snapshotModunaGec()
    }

    override fun onResume() {
        super.onResume()
        tamEkraniAc()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) tamEkraniAc()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(simulasyonGorevi)
    }

    // ============================================================
    // DUZELTME: WindowCompat.setDecorFitsSystemWindows + hide()
    // yaklasimi bazi Android surumlerinde (ozellikle Android 10 ve
    // altinda ya da bazi OEM cihazlarda) yeterli olmuyor. Bu yuzden
    // eski (legacy) systemUiVisibility flag'lerini de EK OLARAK
    // uyguluyoruz -- iki yontemi birlikte kullanmak, cihaz/surum
    // farkliliklarina karsi cok daha guvenilir tam ekran davranisi
    // saglar. IMMERSIVE_STICKY, kullanici kenardan swipe yapsa bile
    // barlarin birkac saniye sonra otomatik tekrar gizlenmesini saglar.
    // ============================================================
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
            it.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun kontrolPaneliniKur() {
        btnOncekiMotor.setOnClickListener {
            if (ekranModu == EkranModu.SIMULASYON) return@setOnClickListener
            mevcutMotorIndex =
                (mevcutMotorIndex - 1 + SABIT_MOTOR_LISTESI.size) % SABIT_MOTOR_LISTESI.size
            snapshotModunaGec()
        }

        btnSonrakiMotor.setOnClickListener {
            if (ekranModu == EkranModu.SIMULASYON) return@setOnClickListener
            mevcutMotorIndex = (mevcutMotorIndex + 1) % SABIT_MOTOR_LISTESI.size
            snapshotModunaGec()
        }

        btnSimulasyon.setOnClickListener {
            if (ekranModu == EkranModu.SIMULASYON) return@setOnClickListener
            simulasyonuBaslat()
        }
    }

    private fun kirpmaUygula(adimlar: List<JSONObject>): List<JSONObject> {
        if (adimlar.isEmpty() || adimlar.size <= KIRPMA_ADIM_SAYISI) {
            kirpmaBaslangicOffset = 0
            return adimlar
        }

        val ilkAlarmIndex = adimlar.indexOfFirst { it.optBoolean("alarm", false) }

        val baslangic = if (ilkAlarmIndex >= 0) {
            val yariPencere = KIRPMA_ADIM_SAYISI / 2
            (ilkAlarmIndex - yariPencere).coerceIn(0, adimlar.size - KIRPMA_ADIM_SAYISI)
        } else {
            adimlar.size - KIRPMA_ADIM_SAYISI
        }

        kirpmaBaslangicOffset = baslangic
        return adimlar.subList(baslangic, baslangic + KIRPMA_ADIM_SAYISI)
    }

    private fun sabitAlarmAdimIndexBul(adimlar: List<JSONObject>): Int {
        val ilkAlarmIndex = adimlar.indexOfFirst { it.optBoolean("alarm", false) }
        return if (ilkAlarmIndex >= 0) ilkAlarmIndex else adimlar.size - 1
    }

    private fun grafigiTamamenSifirla() {
        chartProba.clear()
        chartProba.data = null
        probaDataSet = null
        probaEntries.clear()
    }

    private fun kullanimSayaciniHesapla(globalIndex: Int): Int {
        val baslangic = MOTOR_SAYAC_BASLANGICI[motorId] ?: 2000
        return baslangic + globalIndex
    }

    private fun snapshotModunaGec() {
        handler.removeCallbacks(simulasyonGorevi)
        ekranModu = EkranModu.SNAPSHOT
        btnSimulasyon.text = "SİMÜLASYON"
        btnOncekiMotor.isEnabled = true
        btnSonrakiMotor.isEnabled = true

        sensorKatkiToplami.clear()
        raporlananLiderler = emptyList()
        txtSorunluSensor.text = "Analiz ediliyor..."

        grafigiTamamenSifirla()

        val uid = SABIT_MOTOR_LISTESI[mevcutMotorIndex]
        motorId = uid
        val yuklendi = motorVerisiniYukle(uid)
        if (!yuklendi) {
            txtMachineId.text = "Motor #$uid (veri bulunamadı)"
            return
        }
        oynatilanAdimlar = kirpmaUygula(tumAdimlar)

        if (tumAdimlar.isEmpty()) return

        mevcutAdim = sabitAlarmAdimIndexBul(tumAdimlar)

        val gecmisBaslangic = maxOf(0, mevcutAdim - SNAPSHOT_GECMIS_PENCERESI)
        probaEntries.clear()
        for (i in gecmisBaslangic..mevcutAdim) {
            val p = tumAdimlar[i].getDouble("proba").toFloat()
            probaEntries.add(Entry(i.toFloat(), p))
        }

        txtMachineId.text = "Motor #$uid"
        birAdimIsle(tumAdimlar[mevcutAdim], snapshotEtiketiyle = true, globalIndex = mevcutAdim)
    }

    private fun grafigeNoktaEkleVeCiz(x: Float, y: Float, tekNoktaOlarakGoster: Boolean) {
        probaEntries.add(Entry(x, y))
        if (probaEntries.size > 60) probaEntries.removeAt(0)

        var dataSet = probaDataSet
        if (dataSet == null) {
            dataSet = LineDataSet(ArrayList(probaEntries), "RUL ≤ $HORIZON_CYCLES Olasılığı")
            dataSet.color = Color.parseColor("#38BDF8")
            dataSet.setDrawValues(false)
            dataSet.lineWidth = 2.2f
            dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
            dataSet.setDrawFilled(true)
            dataSet.fillColor = Color.parseColor("#38BDF8")
            dataSet.fillAlpha = 50
            dataSet.circleRadius = 3f
            dataSet.setCircleColor(Color.parseColor("#38BDF8"))
            probaDataSet = dataSet
            chartProba.data = LineData(dataSet)
        } else {
            dataSet.setValues(ArrayList(probaEntries))
        }

        dataSet.setDrawCircles(tekNoktaOlarakGoster)

        chartProba.data.notifyDataChanged()
        chartProba.notifyDataSetChanged()

        chartProba.setVisibleXRangeMaximum(60f)
        val hedefX = maxOf(0f, (x - 55f))
        chartProba.moveViewToX(hedefX)
        chartProba.invalidate()
    }

    private fun grafigiTopluCiz(tekNoktaOlarakGoster: Boolean) {
        val dataSet = LineDataSet(ArrayList(probaEntries), "RUL ≤ $HORIZON_CYCLES Olasılığı")
        dataSet.color = Color.parseColor("#38BDF8")
        dataSet.setDrawValues(false)
        dataSet.lineWidth = 2.2f
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.parseColor("#38BDF8")
        dataSet.fillAlpha = 50
        dataSet.setDrawCircles(tekNoktaOlarakGoster)
        dataSet.circleRadius = 3f
        dataSet.setCircleColor(Color.parseColor("#38BDF8"))

        probaDataSet = dataSet
        chartProba.data = LineData(dataSet)

        chartProba.data.notifyDataChanged()
        chartProba.notifyDataSetChanged()

        chartProba.setVisibleXRangeMaximum(60f)
        val sonX = probaEntries.lastOrNull()?.x ?: 0f
        val hedefX = maxOf(0f, sonX - 55f)
        chartProba.moveViewToX(hedefX)
        chartProba.invalidate()
    }

    private fun simulasyonuBaslat() {
        handler.removeCallbacks(simulasyonGorevi)
        ekranModu = EkranModu.SIMULASYON
        btnSimulasyon.text = "OYNATILIYOR..."
        btnOncekiMotor.isEnabled = false
        btnSonrakiMotor.isEnabled = false

        sensorKatkiToplami.clear()
        raporlananLiderler = emptyList()
        grafigiTamamenSifirla()
        mevcutAdim = 0

        handler.post(simulasyonGorevi)
    }

    private fun simulasyonBittiAraEkraniniGoster() {
        btnSimulasyon.text = "TAMAMLANDI"
        txtZamanAdim.text = "İzleme tamamlandı"
        txtStatusBadge.text = " İZLEME BİTTİ "
        txtStatusBadge.setBackgroundResource(R.drawable.badge_normal)

        handler.postDelayed({
            snapshotModunaGec()
        }, 1200L)
    }

    private fun motorVerisiniYukle(uid: Int): Boolean {
        return try {
            val dosyaIcerigi = assets.open(
                "mobile_export/motors/motor_$uid.json")
                .bufferedReader().use { it.readText() }
            val kok = JSONObject(dosyaIcerigi)

            val adimListesi = kok.getJSONArray("adimlar")
            val liste = ArrayList<JSONObject>()
            for (i in 0 until adimListesi.length()) {
                liste.add(adimListesi.getJSONObject(i))
            }
            tumAdimlar = liste
            true
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    // DEGISIKLIK: txtRulKalan artik "kaçıncı cycle" (kullanım
    // sayacı) gösterimini koruyor -- bu KALDIRILMADI. Sadece
    // txtProbaBuyuk'un YANINDA/ALTINDA sabit "RUL ≤ 24 Olma
    // İhtimali" etiketi (txtProbaEtiket, onCreate'te bir kez
    // ayarlandı) bulunuyor; böylece büyük yüzdenin NE ANLAMA
    // GELDİĞİ ekranda açıkça yazıyor, ham "%99 olasılık" gibi
    // belirsiz bir ifade kalmıyor.
    // ============================================================
    private fun birAdimIsle(adim: JSONObject, snapshotEtiketiyle:
    Boolean, globalIndex: Int) {
        val proba = adim.getDouble("proba").toFloat()
        val alarm = adim.getBoolean("alarm")

        val kullanimSayaci = kullanimSayaciniHesapla(globalIndex)

        txtZamanAdim.text = if (snapshotEtiketiyle) {
            "Anlık Durum • #$kullanimSayaci"
        } else {
            "İzleniyor • #$kullanimSayaci"
        }

        val riskYuzdesi = (proba * 100).toInt().coerceIn(0, 100)
        txtProbaBuyuk.text = "$riskYuzdesi%"
        txtRulKalan.text = "$kullanimSayaci. Kullanım"

        if (snapshotEtiketiyle) {
            grafigiTopluCiz(tekNoktaOlarakGoster = true)
        } else {
            grafigeNoktaEkleVeCiz(mevcutAdim.toFloat(),
                proba, tekNoktaOlarakGoster = false)
        }

        if (alarm || riskYuzdesi >= 50) {
            txtStatusBadge.text = " DİKKAT GEREKİYOR "
            txtStatusBadge.setBackgroundResource(R.drawable.badge_alarm)
            txtProbaBuyuk.setTextColor(Color.parseColor("#F87171"))
        } else {
            txtStatusBadge.text = " NORMAL "
            txtStatusBadge.setBackgroundResource(R.drawable.badge_normal)
            txtProbaBuyuk.setTextColor(Color.parseColor("#38BDF8"))
        }

        sorunluSensorleriGuncelle(adim, proba)
    }

    private fun ozellikSensoruCikar(ozellikAdi: String): String {
        val matcher = SENSOR_PATTERN.matcher(ozellikAdi)
        return if (matcher.find()) matcher.group(1) else "diger"
    }

    private fun sorunluSensorleriGuncelle(guncelAdim: JSONObject, proba: Float) {
        val shapTop = guncelAdim.getJSONArray("shap_top")
        for (i in 0 until shapTop.length()) {
            val ozellikObj = shapTop.getJSONObject(i)
            val ozellikAdi = ozellikObj.getString("ozellik")
            val shapDeger = Math.abs(ozellikObj.getDouble("shap"))
            val sensor = ozellikSensoruCikar(ozellikAdi)
            if (sensor == "diger") continue

            sensorKatkiToplami[sensor] =
                (sensorKatkiToplami[sensor] ?: 0.0) + shapDeger
        }

        if (mevcutAdim > 0 && mevcutAdim % SENSOR_RAPOR_PENCERESI == 0) {
            sensorKatkiToplami.keys.toList().forEach { key ->
                sensorKatkiToplami[key] = (sensorKatkiToplami[key] ?: 0.0) * 0.5
            }
        }

        if (proba < PROBA_UYARI_ESIGI) {
            txtSorunluSensor.text = "Sistem normal"
            txtSensorAciklama.text = "NASA C-MAPSS Turbofan Motoru izleniyor"
            raporlananLiderler = emptyList()
            cardSorunluSensor.setCardBackgroundColor(Color.parseColor("#1E293B"))
            detayPaneliniTemizle()
            return
        }

        val siraliSensorler = sensorKatkiToplami.entries.sortedByDescending { it.value }
        if (siraliSensorler.isEmpty()) {
            txtSorunluSensor.text = "Sensör verisi bekleniyor..."
            detayPaneliniTemizle()
            return
        }

        val toplamKatki = siraliSensorler.sumOf { it.value }
        if (toplamKatki <= 0.0) return

        val yeniAday = siraliSensorler.take(3).map { it.key.uppercase() }
        val yeniAdayKatki = siraliSensorler.take(3).sumOf { it.value } / toplamKatki

        val eskiLiderKatki = if (raporlananLiderler.isNotEmpty()) {
            siraliSensorler
                .filter { it.key.uppercase() in raporlananLiderler }
                .sumOf { it.value } / toplamKatki
        } else 0.0

        val degistirMi = raporlananLiderler.isEmpty() ||
                (yeniAdayKatki - eskiLiderKatki) > LIDER_DEGISIM_ESIGI ||
                (yeniAday != raporlananLiderler && eskiLiderKatki < 0.05)

        if (degistirMi) {
            raporlananLiderler = yeniAday
        }

        val sensorKelimesi = if (raporlananLiderler.size > 1) "Sensörlerini" else "Sensörünü"
        txtSorunluSensor.text =
            "${raporlananLiderler.joinToString(", ")} \n $sensorKelimesi Kontrol Et"
        txtSensorAciklama.text = "NASA C-MAPSS Turbofan Motoru sensör kodları"

        cardSorunluSensor.setCardBackgroundColor(
            if (guncelAdim.getBoolean("alarm"))
                Color.parseColor("#3B0A0A") else Color.parseColor("#422006")
        )

        detayPaneliniGuncelle(siraliSensorler, toplamKatki)
    }

    private fun detayPaneliniGuncelle(
        siraliSensorler: List<Map.Entry<String, Double>>,
        toplamKatki: Double
    ) {
        val isimGoster = listOf(txtShap1Isim, txtShap2Isim, txtShap3Isim)
        val yuzdeGoster = listOf(txtShap1Yuzde, txtShap2Yuzde, txtShap3Yuzde)
        val barGoster = listOf(barShap1, barShap2, barShap3)

        for (i in 0 until 3) {
            if (i < siraliSensorler.size) {
                val (kod, deger) = siraliSensorler[i]
                val yuzde = ((deger / toplamKatki) * 100).toInt()
                isimGoster[i].text = kod.uppercase()
                yuzdeGoster[i].text = "%$yuzde"
                barGenisligiAyarla(barGoster[i], (deger / toplamKatki).
                toFloat())
            } else {
                isimGoster[i].text = "--"
                yuzdeGoster[i].text = "0%"
                barGenisligiAyarla(barGoster[i], 0f)
            }
        }
    }

    private fun detayPaneliniTemizle() {
        val isimGoster = listOf(txtShap1Isim, txtShap2Isim, txtShap3Isim)
        val yuzdeGoster = listOf(txtShap1Yuzde, txtShap2Yuzde, txtShap3Yuzde)
        val barGoster = listOf(barShap1, barShap2, barShap3)
        for (i in 0 until 3) {
            isimGoster[i].text = "--"
            yuzdeGoster[i].text = "0%"
            barGenisligiAyarla(barGoster[i], 0f)
        }
    }

    private fun barGenisligiAyarla(bar: View, oran: Float) {
        val parent = bar.parent as View
        parent.post {
            val toplamGenislik = parent.width
            val params = bar.layoutParams
            params.width = (toplamGenislik * oran.coerceIn(0.02f, 1f)).toInt()
            bar.layoutParams = params
        }
    }

    private fun probaChartAyarlariniYap() {
        chartProba.description.isEnabled = false
        chartProba.legend.isEnabled = false
        chartProba.axisRight.isEnabled = false
        chartProba.setTouchEnabled(false)
        chartProba.setDrawGridBackground(false)
        chartProba.setNoDataText("İzleme başlıyor...")
        chartProba.setNoDataTextColor(Color.parseColor("#64748B"))

        val xAxis = chartProba.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textColor = Color.parseColor("#94A3B8")
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(false)
        xAxis.textSize = 9f
        xAxis.setDrawLabels(false)

        val yAxis = chartProba.axisLeft
        yAxis.textColor = Color.parseColor("#94A3B8")
        yAxis.gridColor = Color.parseColor("#1E293B")
        yAxis.setDrawAxisLine(false)
        yAxis.axisMinimum = 0f
        yAxis.axisMaximum = 1f
        yAxis.setDrawLabels(false)
    }
}
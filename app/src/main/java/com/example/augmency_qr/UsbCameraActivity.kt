package com.example.augmency_qr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraActivity
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio

// ============================================================
// AndroidUSBCamera (AUSBC) kütüphanesinin CameraActivity taban
// sınıfını miras alıyoruz. Doğru paket yolu com.jiangdg.ausbc.base
// altında. Gerçek imzalar Android Studio derleyicisinin verdiği
// hatalara göre düzeltildi -- getRootView TEK parametre alıyor
// (layoutInflater) ve nullable View? döndürüyor.
// ============================================================
class UsbCameraActivity : CameraActivity() {

    private var kokView: View? = null
    private lateinit var textureView: AspectRatioTextureView
    private lateinit var txtKameraDurum: TextView

    override fun getRootView(layoutInflater: LayoutInflater): View? {
        if (kokView == null) {
            kokView = layoutInflater.inflate(R.layout.activity_usb_camera, null, false)
            textureView = kokView!!.findViewById(R.id.textureView)
            txtKameraDurum = kokView!!.findViewById(R.id.txtKameraDurum)

            kokView!!.findViewById<Button>(R.id.btnKameraGeri).setOnClickListener {
                finish()
            }
        }
        return kokView
    }

    override fun getCameraView(): IAspectRatio {
        return textureView
    }

    override fun getCameraViewContainer(): ViewGroup {
        return kokView as ViewGroup
    }

    // Cyclops HMD'nin küçük ekranı (386x220) için düşük çözünürlük
    // isteniyor. Kamera bunu desteklemezse kütüphane otomatik olarak
    // en yakın desteklenen çözünürlüğe düşer.
    override fun getCameraRequest(): CameraRequest {
        return CameraRequest.Builder()
            .setPreviewWidth(640)
            .setPreviewHeight(480)
            .setRenderMode(CameraRequest.RenderMode.OPENGL)
            .setAspectRatioShow(true)
            .setRawPreviewData(false)
            .create()
    }

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        when (code) {
            ICameraStateCallBack.State.OPENED -> {
                txtKameraDurum.text = " BAĞLI "
            }
            ICameraStateCallBack.State.CLOSED -> {
                txtKameraDurum.text = " KAPALI "
            }
            ICameraStateCallBack.State.ERROR -> {
                txtKameraDurum.text = " HATA: ${msg ?: "bilinmiyor"} "
            }
        }
    }
}
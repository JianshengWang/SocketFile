package com.jasonwang.socketfile.activities

import android.Manifest
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Toast
import com.jasonwang.socketfile.R
import com.permissionx.guolindev.PermissionX

class MainActivity : AppCompatActivity() {

    lateinit var radioGroup: RadioGroup
    lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //申请权限
        accessPermission()
        //注册控件
        init()

        startButton.setOnClickListener {
            when (radioGroup.checkedRadioButtonId) {
                R.id.radio_server -> {
                    val intent = Intent()
                    intent.setClass(this, ServerActivity::class.java)
                    startActivity(intent)
                }
                R.id.radio_client -> {
                    val intent = Intent()
                    intent.setClass(this, ClientActivity::class.java)
                    startActivity(intent)
                }
            }
        }

    }

    //注册控件
    fun init() {
        radioGroup = findViewById(R.id.radio_group)
        startButton = findViewById(R.id.button_select_mode)
    }

    //申请权限
    fun accessPermission() {
        val permissionList = ArrayList<String>()
        permissionList.add(Manifest.permission.ACCESS_WIFI_STATE)
        permissionList.add(Manifest.permission.INTERNET)
        permissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        PermissionX.init(this)
            .permissions(permissionList)
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(deniedList, "即将重新申请的权限是程序必须依赖的权限", "我已明白", "取消")
            }
            .onForwardToSettings { scope, deniedList ->
                scope.showForwardToSettingsDialog(deniedList, "您需要去应用程序设置当中手动开启权限", "我已明白", "取消")
            }
            .request { allGranted, grantedList, deniedList ->
                if (allGranted) {
                    Toast.makeText(this, "所有申请的权限都已通过", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "您拒绝了如下权限：$deniedList", Toast.LENGTH_SHORT).show()
                }
            }
    }

}
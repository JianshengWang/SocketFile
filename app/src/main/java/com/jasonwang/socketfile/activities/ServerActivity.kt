package com.jasonwang.socketfile.activities

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.DhcpInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.jasonwang.socketfile.R
import com.jasonwang.socketfile.beans.Transmission
import com.jasonwang.socketfile.utils.FileUtils
import com.vincent.filepicker.Constant
import com.vincent.filepicker.activity.ImagePickActivity
import com.vincent.filepicker.filter.entity.ImageFile
import java.io.InputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.Executors

class ServerActivity : AppCompatActivity(), View.OnClickListener {

    lateinit var ipText: TextView
    lateinit var portEdit: EditText
    lateinit var connectButton: Button
    lateinit var disconnectButton: Button
    lateinit var receiveText: TextView
    lateinit var sendEdit: EditText
    lateinit var sendButton: Button
    lateinit var chooseFileButton: Button

    //线程池
    val executors = Executors.newCachedThreadPool()

    val gson: Gson = Gson()

    lateinit var server: Server

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)

        init()

        ipText.text = getLocalIP()
    }

    //初始化ui控件
    fun init() {
        ipText = findViewById(R.id.server_ip_text)
        portEdit = findViewById(R.id.server_port_edit)
        connectButton = findViewById(R.id.server_connect_button)
        disconnectButton = findViewById(R.id.server_disconnect_button)
        receiveText = findViewById(R.id.server_receive_text)
        sendEdit = findViewById(R.id.server_send_edit)
        sendButton = findViewById(R.id.server_send_button)
        chooseFileButton = findViewById(R.id.server_choose_file_button)

        connectButton.setOnClickListener(this)
        disconnectButton.setOnClickListener(this)
        sendButton.setOnClickListener(this)
        chooseFileButton.setOnClickListener(this)

        disconnectButton.isEnabled = false
        sendButton.isEnabled = false
        chooseFileButton.isEnabled = false
    }

    //获取本机ip地址
    fun getLocalIP(): String{
        val wifiManager: WifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo: DhcpInfo = wifiManager.dhcpInfo
        val ip = dhcpInfo.ipAddress
        return ((ip and 0xFF).toString() + "." + (ip shr 8 and 0xFF) + "." + (ip shr 16 and 0xFF) + "."
                + (ip shr 24 and 0xFF))
    }

    //点击事件
    override fun onClick(v: View) {
        when(v.id) {
            R.id.server_connect_button -> {
                connectButton.isEnabled = false
                disconnectButton.isEnabled = true
                sendButton.isEnabled = true
                chooseFileButton.isEnabled = true
                server = Server(portEdit.text.toString().toInt())
                executors.execute(server)
            }
            R.id.server_disconnect_button -> {
                server.close()
            }
            R.id.server_send_button -> {
                executors.execute {
                    val transmission = Transmission(sendEdit.text.toString(), 0)
                    val jsonObject = gson.toJson(transmission)
                    server.serverSocketThreads[0].send(jsonObject)
                }
            }
            R.id.server_choose_file_button -> {
                val intent = Intent(this, ImagePickActivity::class.java)
                intent.putExtra(ImagePickActivity.IS_NEED_CAMERA, true)
                intent.putExtra(Constant.MAX_NUMBER, 1)
                startActivityForResult(intent, Constant.REQUEST_CODE_PICK_IMAGE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            Constant.REQUEST_CODE_PICK_IMAGE -> {
                if (resultCode == RESULT_OK) {
                    val list: ArrayList<ImageFile> = data?.getParcelableArrayListExtra(Constant.RESULT_PICK_IMAGE)!!
                    for (file: ImageFile in list) {
                        executors.execute {
                            server.serverSocketThreads[0].send(FileUtils.getImageByte(file.path))
                        }
                    }
                }
            }
        }
    }

    //服务器端socket线程
    inner class Server(var port: Int): Runnable {

        var isListen = true
        var serverSocketThreads = ArrayList<ServerSocketThread>()
        val gson = Gson()

        override fun run() {
            try {
                val serverSocket = ServerSocket(port)
                serverSocket.soTimeout = 0
                while (isListen) {
                    Log.e("开始监听", "开始监听")
                    val socket = getSocket(serverSocket)
                    if (socket != null) {
                        ServerSocketThread(socket)
                    }
                }
                serverSocket.close()
            } catch (e: Exception) {
            }
        }

        //关闭socket
        fun close() {
            isListen = false
            for (thread in serverSocketThreads) {
                thread.isRun = false
            }
            serverSocketThreads.clear()
        }

        //获取socket对象
        fun getSocket(serverSocket: ServerSocket): Socket? {
            return try {
                serverSocket.accept()
            } catch (e: Exception) {
                null
            }
        }

        inner class ServerSocketThread(var socket: Socket): Thread() {
            lateinit var printWriter: PrintWriter
            lateinit var inputStream: InputStream
            var ip = ""
            var isRun = true

            init {
                ip = socket.inetAddress.toString()
                Log.e("新客户端连接server", ip)
                try {
                    socket.soTimeout = 0
                    val outputStream = socket.getOutputStream()
                    inputStream = socket.getInputStream()
                    printWriter = PrintWriter(outputStream, true)
                    start()
                } catch (e: Exception) {
                }
            }

            fun send(message: String) {
                printWriter.println(message)
                printWriter.flush()
            }

            override fun run() {
                val buffet = ByteArray(32768)
                var receiveMessage = ""
                var receiveLength: Int
                serverSocketThreads.add(this)
                var message = ""
                while (isRun && !socket.isClosed && !socket.isInputShutdown) {
                    try {
                        if (inputStream.read(buffet).also { receiveLength = it } !== -1) {
                            receiveMessage = String(buffet, 0, receiveLength, Charsets.UTF_8)
                            message += receiveMessage
                        }
                    } catch (e: Exception) {
                    }
                    try {
                        if (message != "") {
                            val jsonObject = gson.fromJson(message, Transmission::class.java)
                            when(jsonObject.fileType) {
                                //根据fileType判断接收到的是文字还是图片 0:文字 1:图片
                                0 -> {
                                    runOnUiThread {
                                        receiveText.append(jsonObject.content)
                                    }
                                }
                                1 -> {
                                    val bitmap = FileUtils.createImageWithByte(jsonObject.content)
                                    val name = "${System.currentTimeMillis()}.jpg"
                                    val mimeType = "image/jpeg"
                                    val compressFormat = Bitmap.CompressFormat.JPEG
                                    addBitmapToAlbum(bitmap, name, mimeType, compressFormat)
                                }
                            }
                            message = ""
                        }
                    } catch (e: Exception) {
                    }
                }
                try {
                    socket.close()
                    printWriter.close()
                } catch (e: Exception) {

                }

            }

        }

        //把图片添加到文件夹
        private fun addBitmapToAlbum(bitmap: Bitmap, displayName: String, mimeType: String, compressFormat: Bitmap.CompressFormat) {
            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
            } else {
                values.put(MediaStore.MediaColumns.DATA, "${Environment.getExternalStorageDirectory().path}/${Environment.DIRECTORY_DCIM}/$displayName")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                val outputStream = contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    bitmap.compress(compressFormat, 100, outputStream)
                    outputStream.close()
                }
            }
        }

    }

}
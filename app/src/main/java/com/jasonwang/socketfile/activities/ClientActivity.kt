package com.jasonwang.socketfile.activities

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.gson.Gson
import com.jasonwang.socketfile.MyApp
import com.jasonwang.socketfile.R
import com.jasonwang.socketfile.beans.Transmission
import com.jasonwang.socketfile.utils.FileUtils
import com.vincent.filepicker.Constant
import com.vincent.filepicker.activity.ImagePickActivity
import com.vincent.filepicker.filter.entity.ImageFile
import java.io.DataInputStream
import java.io.PrintWriter
import java.net.Socket
import java.util.ArrayList
import java.util.concurrent.Executors

class ClientActivity : AppCompatActivity(), View.OnClickListener {

    lateinit var ipEdit: EditText
    lateinit var portEdit: EditText
    lateinit var connectButton: Button
    lateinit var disconnectButton: Button
    lateinit var receiveText: TextView
    lateinit var sendEdit: EditText
    lateinit var sendButton: Button
    lateinit var chooseFileButton: Button

    lateinit var client: Client

    var isLoading = false

    //json解析
    val gson = Gson()

    //线程池
    val executors = Executors.newCachedThreadPool()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)

        init()

    }

    override fun onDestroy() {
        super.onDestroy()
        client.close()
    }

    //初始化ui控件
    fun init() {
        ipEdit = findViewById(R.id.client_ip_edit)
        portEdit = findViewById(R.id.client_port_edit)
        connectButton = findViewById(R.id.client_connect_button)
        disconnectButton = findViewById(R.id.client_disconnect_button)
        receiveText = findViewById(R.id.client_receive_text)
        sendEdit = findViewById(R.id.client_send_edit)
        sendButton = findViewById(R.id.client_send_button)
        chooseFileButton = findViewById(R.id.client_choose_file_button)

        connectButton.setOnClickListener(this)
        disconnectButton.setOnClickListener(this)
        sendButton.setOnClickListener(this)
        chooseFileButton.setOnClickListener(this)

        disconnectButton.isEnabled = false
        sendButton.isEnabled = false
        chooseFileButton.isEnabled = false
    }

    override fun onClick(v: View) {
        when(v.id) {
            //连接
            R.id.client_connect_button -> {
                connectButton.isEnabled = false
                disconnectButton.isEnabled = true
                sendButton.isEnabled = true
                chooseFileButton.isEnabled = true
                if ((ipEdit.text.toString() != "") && (portEdit.text.toString() != "")) {
                    Log.e("开始", "客户端连接")
                    client = Client(ipEdit.text.toString(), portEdit.text.toString().toInt())
                    executors.execute(client)
                }
            }
            //断开
            R.id.client_disconnect_button -> {
                client.close()
                connectButton.isEnabled = true
                disconnectButton.isEnabled = false
                sendButton.isEnabled = false
                chooseFileButton.isEnabled = false
            }
            //发送文字
            R.id.client_send_button -> {
                if (sendEdit.text.isEmpty()) {
                    Toast.makeText(MyApp.context, "请输入文字", Toast.LENGTH_SHORT).show()
                } else {
                    executors.execute {
                        val transmission = Transmission(sendEdit.text.toString(), 0, sendEdit.text.length)
                        val jsonObject = gson.toJson(transmission)
                        client.send(jsonObject)
                    }
                }
            }
            //发送图片
            R.id.client_choose_file_button -> {
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
                            client.send(FileUtils.getImageByte(file.path))
                        }
                    }
                }
            }
        }
    }

    //客户端Socket线程
    inner class Client(var ip: String, var port: Int): Runnable {

        lateinit var printWriter: PrintWriter
        lateinit var dataInputStream: DataInputStream
        lateinit var socket: Socket

        //是否运行标记位
        var isRun = true
        var buffer = ByteArray(32768)
        var receiveMessage = ""
        var receiveLength = 0
        var message = ""
        var firstLinkFlag = true

        override fun run() {

            try {
                //设置socket
                socket = Socket(ip, port)
                socket.soTimeout = 0
                printWriter = PrintWriter(socket.getOutputStream(), true)
                dataInputStream = DataInputStream(socket.getInputStream())
            } catch (e: Exception) {
                Log.e("client错误", "socket设置")
            }

            while (isRun) {

                if (!isLoading && !firstLinkFlag) {
                    runOnUiThread {
                        Toast.makeText(MyApp.context, "开始传输", Toast.LENGTH_SHORT).show()
                        connectButton.isEnabled = false
                        disconnectButton.isEnabled = false
                        sendButton.isEnabled = false
                        chooseFileButton.isEnabled = false
                        isLoading = true
                    }
                }

                firstLinkFlag = false

                try {
                    if (dataInputStream.read(buffer).also { receiveLength = it } !== -1) {
                        receiveMessage = String(buffer, 0, receiveLength, Charsets.UTF_8)
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
                        runOnUiThread {
                            Toast.makeText(MyApp.context, "传输结束", Toast.LENGTH_SHORT).show()
                            disconnectButton.isEnabled = true
                            sendButton.isEnabled = true
                            chooseFileButton.isEnabled = true
                            isLoading = false
                        }
                        message = ""
                    }
                } catch (e: Exception) {
                }
            }
            try {
                printWriter.close()
                dataInputStream.close()
                socket.close()
            } catch (e: Exception) {
                Log.e("client错误", "关闭资源")
            }

        }

        //关闭socket
        fun close() {
            isRun = false
        }

        //发送信息
        fun send(message: String) {
            runOnUiThread {
                Toast.makeText(MyApp.context, "开始传输", Toast.LENGTH_SHORT).show()
                disconnectButton.isEnabled = false
                sendButton.isEnabled = false
                chooseFileButton.isEnabled = false
            }
            printWriter.println(message)
            printWriter.flush()
            runOnUiThread {
                Toast.makeText(MyApp.context, "传输结束", Toast.LENGTH_SHORT).show()
                disconnectButton.isEnabled = true
                sendButton.isEnabled = true
                chooseFileButton.isEnabled = true
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
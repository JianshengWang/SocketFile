package com.jasonwang.socketfile.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.gson.Gson
import com.jasonwang.socketfile.beans.Transmission
import java.io.ByteArrayOutputStream
import java.io.FileInputStream

object FileUtils {

    //图片转二进制流
    fun getImageByte(path: String): String {

        val gson = Gson()
        val bitmap = BitmapFactory.decodeStream(FileInputStream(path))
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        val bytes = outputStream.toByteArray()
        val string = Base64.encodeToString(bytes, Base64.DEFAULT)

        val transmission = Transmission(string, 1, string.length)
        outputStream.close()

        return gson.toJson(transmission)

    }

    //二进制流转图片
    fun createImageWithByte(imageByte: String): Bitmap {

        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = false
        val byteArray = Base64.decode(imageByte, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

    }



}
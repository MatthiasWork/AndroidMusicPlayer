package com.example.androidprojektaudioplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.androidprojektaudioplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var myDB: DataBaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        myDB = DataBaseHelper(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO), 1)
        } else {
            ladeAudioDateien();
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ladeAudioDateien();
        }
    }

    //Methode, für wenn die MainActivity wieder im Vordergrund ist
//    override fun onResume() {
//        super.onResume()
//        refreshList()
//    }

    fun ladeAudioDateien() {
        val defaultList: MutableList<myAudio> = myDB.getAllMp3Files(this) as MutableList<myAudio>
        myDB.removeDeletedAudios(defaultList.map { it.audioID })
        for (audio in defaultList) {
            if (!myDB.audioExists(audio.audioID)) {
                myDB.addAudioToDatabase(audio);
            }
        }
    }

    fun refreshList() {

    }
}
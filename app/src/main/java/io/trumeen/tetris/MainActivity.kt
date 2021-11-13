package io.trumeen.tetris

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.SoundPool
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.core.graphics.get
import kotlinx.android.synthetic.main.activity_main.*
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.lang.StringBuilder
import kotlin.concurrent.thread

const val CLIENT_ID = "android_control"
const val TOPIC_SCORE = "trumeen/home/game/score"
const val TOPIC_SOUND = "trumeen/home/game/sound"
const val TOPIC_ACTION = "trumeen/home/game/action"
const val TOPIC_LEVEL = "trumeen/home/game/level"
const val MQTT_SERVER = "tcp://192.168.71.165:1883"
const val SCORE_LENGTH = 5
const val LEVEL_LENGTH = 2
const val KEY_DOWN: Byte = 0x00
const val KEY_UP: Byte = 0x1
const val KEY_LEFT: Byte = 0x02
const val KEY_RIGHT: Byte = 0x03
const val KEY_A: Byte = 0x04
const val KEY_B: Byte = 0x05
const val KEY_C: Byte = 0x06
const val KEY_D: Byte = 0x07
const val GAME_MODE_SNAKE = 1
const val GAME_MODE_TETRIS = 2

lateinit var mClient: MqttAndroidClient

class MainActivity : AppCompatActivity(), View.OnClickListener {

    lateinit var manger: SensorManager
    lateinit var sensor: Sensor

    private var mCurrentGameMode = GAME_MODE_SNAKE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        manger = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = manger.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        initSoundPool()
        initMqtt()
        setClickListener()


    }

    /**
     * 创建字库
     */
    fun createFonts() {
        val option = BitmapFactory.Options().apply {
            inScaled = false
        }
        val bitmap1 = BitmapFactory.decodeResource(resources, R.raw.for_c, option)
        val bitmap2 = BitmapFactory.decodeResource(resources, R.raw.herry, option)
        val result = StringBuilder()
        var count = 0;
        println("width:${bitmap1.width} height:${bitmap1.height}")
        (0 until bitmap1.height).forEach { heightIndex ->
            result.append("{")
            (0 until bitmap1.width).forEach { widthIndex ->
                result.append("0x${(bitmap1[widthIndex, heightIndex] and 0xFFFFFF).toString(16)}")
                if (widthIndex != bitmap1.width - 1) {
                    result.append(",")
                }
            }
            if (heightIndex == bitmap1.height - 1) {
                result.append("}")
            } else {
                result.append("},")
            }
//            result.append("\r\n")
//            println()
        }
        println("width:${bitmap2.width} height:${bitmap2.height}")
        (0 until bitmap2.height).forEach { heightIndex ->
            result.append("{")
            (0 until bitmap2.width).forEach { widthIndex ->
                result.append("0x${(bitmap2[widthIndex, heightIndex] and 0xFFFFFF).toString(16)}")
                if (widthIndex != bitmap2.width - 1) {
                    result.append(",")
                }
            }
            if (heightIndex == bitmap2.height - 1) {
                result.append("}")
            } else {
                result.append("},")
            }

//            result.append("\r\n")
//            println()
        }

        println("result:${result}")
        println("result:${count}")
    }

    lateinit var soundPool: SoundPool

    var backGround: Int = 0
    var btnPress: Int = 0
    var fadelayer: Int = 0
    var transform: Int = 0
    var gameOver: Int = 0
    var start: Int = 0

    var delayThread: Thread? = null

    var backGroundId = 0

    private fun initSoundPool() {
        soundPool = SoundPool.Builder().setMaxStreams(10).build()
        backGround = soundPool.load(this, R.raw.back2, 1)
        btnPress = soundPool.load(this, R.raw.btn, 1)
        fadelayer = soundPool.load(this, R.raw.fadelayer, 1)
        transform = soundPool.load(this, R.raw.transform, 1)
        gameOver = soundPool.load(this, R.raw.lost, 1)
        start = soundPool.load(this, R.raw.readygo, 1)
    }

    /**
     * 设置按键监听
     */
    private fun setClickListener() {
        iv_down.setOnClickListener(this)
        iv_up.setOnClickListener(this)
        iv_left.setOnClickListener(this)
        iv_right.setOnClickListener(this)
        iv_btn_a.setOnClickListener(this)
        iv_btn_b.setOnClickListener(this)
        iv_btn_c.setOnClickListener(this)
        iv_btn_d.setOnClickListener(this)
    }

    /**
     * 初始化Mqtt
     */
    private fun initMqtt() {
        mClient = MqttAndroidClient(this, MQTT_SERVER, CLIENT_ID)
        mClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                println("connectionLost:${cause}")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                when (topic) {
                    TOPIC_SCORE -> {
                        message?.let {
                            tv_score.text = getScoreText(String(it.payload))
                        }
                    }
                    TOPIC_LEVEL -> {
                        message?.let {
                            tv_level.text = getLevelText(String(it.payload))
                        }
                    }
                    TOPIC_SOUND -> {
                        message?.let {
                            when (String(it.payload)) {
                                "D" -> {
                                    soundPool.play(fadelayer, 1f, 1f, 10, 0, 1f)
                                }
                                "O" -> {
                                    soundPool.stop(backGround)
                                    soundPool.play(gameOver, 1f, 1f, 10, 0, 1f)
                                }
                                else -> {

                                }
                            }
                        }
                    }
                }
                println("messageArrived topic:${topic} message;${message}")
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                println("deliveryComplete")
            }

        })
        val options = MqttConnectOptions().apply {
            connectionTimeout = 30
            isAutomaticReconnect = true
            keepAliveInterval = 30
        }
        mClient.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                mClient.subscribe(TOPIC_SCORE, 0)
                mClient.subscribe(TOPIC_SOUND, 0)
                mClient.subscribe(TOPIC_LEVEL, 0)
                println("连接成功！！！")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                println("连接失败！！！：${exception}")
            }

        })
    }


    override fun onDestroy() {
        super.onDestroy()
        mClient.disconnect()
        soundPool.release()
    }


    private fun getScoreText(score: String): String {
        val result = StringBuilder(score)
        val appendCount = SCORE_LENGTH - score.length
        (0 until appendCount).forEach { _ ->
            result.insert(0, "0")
        }
        return result.toString()
    }

    private fun getLevelText(level: String): String {
        val result = StringBuilder(level)
        val appendCount = LEVEL_LENGTH - level.length
        (0 until appendCount).forEach { _ ->
            result.insert(0, "0")
        }
        return result.toString()
    }

    override fun onClick(p0: View?) {
        p0?.let { view ->
            val msg = MqttMessage()
            when (view.id) {
                R.id.iv_down -> {
                    soundPool.play(btnPress, 1f, 1f, 10, 0, 1f)
                    msg.payload = ByteArray(1).apply {
                        set(0, KEY_DOWN)
                    }
                }
                R.id.iv_up -> {
                    soundPool.play(transform, 1f, 1f, 10, 0, 1f)
                    msg.payload = ByteArray(1).apply {
                        set(0, KEY_UP)
                    }
                }
                R.id.iv_left -> {
                    soundPool.play(btnPress, 1f, 1f, 10, 0, 1f)
                    msg.payload = ByteArray(1).apply {
                        set(0, KEY_LEFT)
                    }
                }
                R.id.iv_right -> {
                    soundPool.play(btnPress, 1f, 1f, 10, 0, 1f)
                    msg.payload = ByteArray(1).apply {
                        set(0, KEY_RIGHT)
                    }
                }
                R.id.iv_btn_a -> {
                    soundPool.stop(backGroundId)
                    delayThread?.interrupt()
                    soundPool.play(start, 0.8f, 0.8f, 10, 0, 1f)
                    msg.payload = ByteArray(1).apply {
                        set(0, KEY_A)
                    }
                    delayThread = thread(isDaemon = false) {
                        SystemClock.sleep(1000)
                        if(Thread.currentThread().isAlive){
                            backGroundId = soundPool.play(backGround, 0.8f, 0.8f, 1, Int.MAX_VALUE, 1f)
                        }
                    }
                }
                R.id.iv_btn_b -> {
                    soundPool.play(btnPress, 1f, 1f, 10, 0, 1f)
                    msg.payload = ByteArray(1).apply {
                        set(0, KEY_B)
                    }
                }
                R.id.iv_btn_c -> {
                    soundPool.play(btnPress, 1f, 1f, 10, 0, 1f)
                    mCurrentGameMode = GAME_MODE_SNAKE
                    showGameMode()
                    msg.payload = ByteArray(1).apply {
                        set(0, KEY_C)
                    }
                }
                R.id.iv_btn_d -> {
                    soundPool.play(btnPress, 1f, 1f, 10, 0, 1f)
                    mCurrentGameMode = GAME_MODE_TETRIS
                    showGameMode()
                    msg.payload = ByteArray(1).apply {
                        set(0, KEY_D)
                    }
                }
            }
            mClient.publish(TOPIC_ACTION, msg)
        }
    }

    private fun showGameMode() {
        tv_name.text = when (mCurrentGameMode) {
            GAME_MODE_SNAKE -> {
                getString(R.string.snake)
            }
            else -> {
                getString(R.string.tetris)
            }
        }

    }
}
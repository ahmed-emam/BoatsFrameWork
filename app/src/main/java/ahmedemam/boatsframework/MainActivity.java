package ahmedemam.boatsframework;

import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import ahmedemam.boatsframework.model.Packet;


/**
 * Things to DO
 *  -  Figure out if I am on adhoc mode or not (DONE), and if not connect to adhoc mode
 */

public class MainActivity extends AppCompatActivity {
    public static String TAG = "boats_framework";
    public static final int  MAX_CONNECTIONS = 1024;
    MainActivity mainActivity;


    public static final int TOAST_MSG = 0;
    public static final int TOAST_MSG_SHORT = 1;
    public static final int TEXT_MSG = 2;


    public String[] device_Wifi_adresses = {
            "D8:50:E6:83:D0:2A",
            "D8:50:E6:83:68:D0",
            "D8:50:E6:80:51:09",
            "24:DB:ED:03:47:C2",
            "24:DB:ED:03:49:5C",
            "8c:3a:e3:6c:a2:9f",
            "8c:3a:e3:5d:1c:ec",
            "c4:43:8f:f6:3f:cd",
            "f8:a9:d0:02:0d:2a",
            "10:bf:48:ef:e9:c1",
            "30:85:a9:60:07:3b"
    };


    public String[] device_ip_adresses = {
            "",
            "",
            "",
            "",
            "",
            "10.0.0.1",
            "10.0.0.2",
            "10.0.0.3",
            "10.0.0.4",
            "",
            ""
    };
    Handler activitiesCommHandler;
    Handler timedTasks;

    //    static ConnectionThread[] connectionThreads = new ConnectionThread[MAX_CONNECTIONS];
    static WorkingThread[] connectionThreads = new WorkingThread[MAX_CONNECTIONS];


    public static int DEVICE_ID = 0;                                   //This device's ID
    private static FileOutputStream logfile;
    public static String rootDir = Environment.getExternalStorageDirectory().toString() + "/MobiBots/boatsFramework/";
    private WifiAdhocServerThread serverThread;
//    private ConnectionThread otherConnection;
    public DatagramSocket broadcastSocket = null;


    Ringtone alarmRingtone;

    public int findDevice_IpAddress(String ipAddress){
        for (int i = 0; i < device_ip_adresses.length; i++) {
            if (device_ip_adresses[i].equalsIgnoreCase(ipAddress)) {
                return (i + 1);
            }
        }
        return -1;
    }
    /**
     * Return device's ID mapping according to the device's wifi mac address
     * @param deviceAddress     Bluetooth Address
     * @return                  device ID
     */
    public int findDevice_Wifi(String deviceAddress) {
        for (int i = 0; i < device_Wifi_adresses.length; i++) {
            if (device_Wifi_adresses[i].equalsIgnoreCase(deviceAddress)) {
                return (i + 1);
            }
        }
        return -1;
    }
    /**
     * Print out debug messages if "D" (debug mode) is enabled
     * @param message
     */
    public void debug(String message) {

        Log.d(TAG, message);

        Message  toastMSG = mainActivity.UIHandler.obtainMessage(TEXT_MSG);
        byte[] toastMSG_bytes =  (message).getBytes();
        toastMSG.obj = toastMSG_bytes;
        toastMSG.arg1 = toastMSG_bytes.length;
        mainActivity.UIHandler.sendMessage(toastMSG);
    }


    public final Handler UIHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TOAST_MSG:
                    byte[] message = (byte[]) msg.obj;
                    String theMessage = new String(message, 0, msg.arg1);
                    Toast.makeText(getApplicationContext(), theMessage, Toast.LENGTH_LONG).show();
                    break;
                case TOAST_MSG_SHORT:
                    message = (byte[]) msg.obj;
                    theMessage = new String(message, 0, msg.arg1);
                    Toast.makeText(getApplicationContext() , theMessage,Toast.LENGTH_SHORT).show();
                    break;
                case TEXT_MSG:
                    TextView view = (TextView) findViewById(R.id.textView2);

                    message = (byte[]) msg.obj;
                    theMessage = new String(message, 0, msg.arg1);
                    theMessage += "\n";

                    view.append(theMessage);
                    break;
            }
        }
    };


    /**
     * Create the log file
     */
    private void initLogging() {
        File mediaStorageDir = new File(rootDir);
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "failed to create directory");
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HH:mm:ss").format(new Date());
        File file = new File(rootDir + "Device_" +DEVICE_ID + "_" +timeStamp + ".txt");

        Log.d(TAG, file.getPath());
        try {
            file.createNewFile();
            logfile = new FileOutputStream(file);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Synchronized function to remove the thread responsible for communication with nodeID "node"
     * @param node  nodeID
     */
    public synchronized void removeNode(int node){
        debug("Disconnecting from "+node);
        try{


            if(alarmRingtone.isPlaying())
                alarmRingtone.stop();
            alarmRingtone.play();
            timedTasks.postDelayed(stopAlarm, 4000);
        }
        catch (Exception e){
            e.printStackTrace();
        }

        connectionThreads[node-1] = null;
    }

//    /**
//     * Synchronized function to remove the thread responsible for communication with nodeID "node"
//     * @param node  nodeID
//     */
//    public synchronized void removeNode(int node){
//        try{
//            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
//
//            if(alarmRingtone.isPlaying())
//                alarmRingtone.stop();
//            alarmRingtone.play();
//            timedTasks.postDelayed(stopAlarm, 4000);
//        }
//        catch (Exception e){
//            e.printStackTrace();
//        }
//
//        connectionThreads[node-1] = null;
//    }

    /**
     * Synchronized function to add the thread responsible for communication with nodeID "node"
     * @param thread    Communication thread
     * @param node      nodeID
     */
    public synchronized void addNode(WorkingThread thread,int node){
        debug("Connecting to"+node);
        try{
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();

        }
        catch (Exception e){
            e.printStackTrace();
        }
        connectionThreads[node-1] = thread;
    }


//    /**
//     * Synchronized function to add the thread responsible for communication with nodeID "node"
//     * @param thread    Communication thread
//     * @param node      nodeID
//     */
//    public synchronized void addNode(ConnectionThread thread,int node){
//
//        try{
//            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
//            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
//            r.play();
//
//        }
//        catch (Exception e){
//            e.printStackTrace();
//        }
//        connectionThreads[node-1] = thread;
//    }

    Runnable startAdvertising = new Runnable() {
        @Override
        public void run() {
            new WifiBroadcast_server().start();
            new WifiBroadcast_client().start();
        }
    };
    Runnable stopAlarm = new Runnable() {
        @Override
        public void run() {
            if(alarmRingtone.isPlaying())
                alarmRingtone.stop();
        }
    };

    /**
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainActivity = this;
        alarmRingtone = RingtoneManager.getRingtone(this,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));

        WifiManager wifiMgr = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        if(checkIBSSMode()){
            debug("You are in IBSS mode");
        }
        else{
            try{
                Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                r.play();
            }
            catch (Exception e){
                e.printStackTrace();
            }

            debug("NOT in IBSS mode");
        }
        DEVICE_ID = findDevice_Wifi(wifiInfo.getMacAddress());

        initLogging();
        activitiesCommHandler = new Handler();

        timedTasks = new Handler();
        serverThread = new WifiAdhocServerThread(this);
        serverThread.start();


        timedTasks.postDelayed(startAdvertising, 10000);

        TextView view = (TextView) findViewById(R.id.textView2);
        view.setMovementMethod(new ScrollingMovementMethod());


//        if(DEVICE_ID == 8){
//            WifiAdhocClientThread clientThread = new WifiAdhocClientThread(device_ip_adresses[8]);
//            clientThread.start();
//        }
    }

    /**
     * This is a hack to check if IBSS mode is enabled or not
     * @return True     if IBSS is on
     *         False    Otherwise
     */
    public boolean checkIBSSMode(){
        Process su = null;
        DataOutputStream stdin = null ;
        try {
            su = Runtime.getRuntime().exec(new String[]{"su", "-c", "system/bin/sh"});
            stdin = new DataOutputStream(su.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            stdin.writeBytes("iw dev wlan0 info | grep type | awk {'print $2'}\n");
            InputStream stdout = su.getInputStream();
            byte[] buffer = new byte[1024];

            //read method will wait forever if there is nothing in the stream
            //so we need to read it in another way than while((read=stdout.read(buffer))>0)
            int read = stdout.read(buffer);
            String out = new String(buffer, 0, read);

            if(out.contains("IBSS")){

                return true;
            }else{
                return false;
            }

        }catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Send file "filename" to device with device ID "device"
     *
     * @param v
     */
    public void send_file_to_device(View v) {
//        otherConnection.stopStream();
//        EditText deviceIDEdit = (EditText) findViewById(R.id.device_ID);
//        String deviceID = deviceIDEdit.getText().toString();


//        String filename = "5MB.txt";
//        int device = 8;
//        debug("Sending file " + filename + " to device "+device);
//        otherConnection.sendFile(device, filename);

    }


    public void startCommunicationThreads(int nodeID, boolean client,
                                          InputStream inputStream, OutputStream outputStream,
                                          MainActivity mainActivity){
        BlockingQueue<Packet> incomingPackets = new LinkedBlockingQueue<>();
        BlockingQueue<Packet> outgoingPackets = new LinkedBlockingQueue<>();

        ReadingThread readingThread = new ReadingThread(incomingPackets, inputStream, nodeID,
                mainActivity);
        WorkingThread workingThread = new WorkingThread(incomingPackets, outgoingPackets, nodeID,
                mainActivity);
        WritingThread writingThread = new WritingThread(outgoingPackets, outputStream, nodeID);

        readingThread.start();
        workingThread.start();
        writingThread.start();

        addNode(workingThread, nodeID);
    }


    public class WifiAdhocServerThread extends Thread {
        ServerSocket serverSocket = null;
        boolean serverOn = true;

        public WifiAdhocServerThread(MainActivity activity) {

            try {
                serverSocket = new ServerSocket(8000);

                Log.i("TelnetServer", "ServerSocket Address: " + serverSocket.getLocalSocketAddress());

            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            while (serverOn) {
                try {
                    Log.d(TAG, "Server Thread");
                    Log.i(TAG, "Started: " + serverSocket.getLocalSocketAddress());
                    Socket client = serverSocket.accept();
                    Log.d(TAG, "Connected to: " + client.getInetAddress().getHostAddress());
                    Log.d(TAG, "Connected to Local Address: " + client.getLocalAddress().getHostAddress());

                    int deviceId = findDevice_IpAddress(client.getInetAddress().getHostAddress());
                    startCommunicationThreads(deviceId, true,
                            client.getInputStream(), client.getOutputStream(), mainActivity);

                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
        public void cancel(){
            try {
                serverOn = false;
                serverSocket.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void printConnectedNodes(){
        for(int i = 0; i < connectionThreads.length; i++){
            if(connectionThreads[i] != null){
                debug("Connected to "+(i+1));
            }
        }
    }


    public class WifiAdhocClientThread extends Thread {
        String hostAddress;
        DataInputStream inputStream;
        DataOutputStream outputStream;
        int device_Id;


        public WifiAdhocClientThread(
                String host) {
//            mainActivity = activity;
            hostAddress = host;
        }

        @Override
        public void run() {
            /**
             * Listing 16-26: Creating a client Socket
             */
            int timeout = 500;
            int port = 8000;

            Socket socket = new Socket();
            try {
                device_Id = findDevice_IpAddress(hostAddress);
                debug("Connecting to " + device_Id + " @ " + hostAddress);
                socket.bind(null);
                socket.connect((new InetSocketAddress(hostAddress, port)), 5000);
                startCommunicationThreads(device_Id, true,
                        socket.getInputStream(), socket.getOutputStream(), mainActivity);

//                otherConnection = new ConnectionThread(mainActivity, socket.getInputStream(),
//                        socket.getOutputStream(), true, device_Id);
//                otherConnection.start();
//                addNode(otherConnection, device_Id);

            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, e.getMessage());
            }
            // TODO Start Receiving Messages
        }
    }


    private class WifiBroadcast_server extends Thread{
        int port = 5555;
        public WifiBroadcast_server(){
            if(broadcastSocket == null)
                try {
                    broadcastSocket = new DatagramSocket(port);
                    broadcastSocket.setBroadcast(true);
                } catch (SocketException e) {
                    e.printStackTrace();
                }
        }

        @Override
        public void run() {
            debug("Started advertising presence");
            while(true){
                try {
                    String messageStr = "Device " + DEVICE_ID;
                    byte[] messageByte = messageStr.getBytes();

                    InetAddress group = InetAddress.getByName("10.0.0.255");
                    DatagramPacket packet = new DatagramPacket(messageByte, messageByte.length, group, port);
                    debug("Broadcasting "+messageStr);
                    broadcastSocket.send(packet);
                    printConnectedNodes();
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
                catch (IOException e){
                    e.printStackTrace();
                    break;

                }
            }
        }
    }


    private class WifiBroadcast_client extends Thread{
        int port = 5555;
        public WifiBroadcast_client(){

            if(broadcastSocket == null)
                try {
                    broadcastSocket = new DatagramSocket(port);
                    broadcastSocket.setBroadcast(true);
                } catch (SocketException e) {
                    e.printStackTrace();
                }
        }

        @Override
        public void run() {
            while(true){
                try {
                    byte[] buf = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    broadcastSocket.receive(packet);
                    byte[]data = packet.getData();

                    String messageStr = new String(data, 0,packet.getLength());

                    Scanner stringScanner = new Scanner(messageStr);
                    stringScanner.next();
                    int deviceID = stringScanner.nextInt();
                    if(deviceID!=DEVICE_ID) {
                        debug("Found device " + deviceID);
                        if(connectionThreads[deviceID-1] == null){
                            new WifiAdhocClientThread(device_ip_adresses[deviceID-1]).start();
                        }
                    }
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
    }

}

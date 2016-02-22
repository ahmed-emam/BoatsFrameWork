package ahmedemam.boatsframework;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
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
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import ahmedemam.boatsframework.model.Packet;
import ahmedemam.boatsframework.model.Route;


/**
 * Things to DO
 *  -  Figure out if I am on adhoc mode or not (DONE), and if not connect to adhoc mode
 */

public class MainActivity extends AppCompatActivity {
    public static String TAG = "boats_framework";
    public static final int  MAX_CONNECTIONS = 1024;
    MainActivity mainActivity;
    public OutputStream commandCenterOutputStream;
    private static FileOutputStream locationFile;
    private Location currentLocation;

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
    //    Handler activitiesCommHandler;
    Handler timedTasks;

    //    static ConnectionThread[] connectionThreads = new ConnectionThread[MAX_CONNECTIONS];

    static WorkerThread[] connectionThreads = new WorkerThread[MAX_CONNECTIONS];
    static ConcurrentHashMap<Integer, PriorityQueue<Route>> routeTable = new ConcurrentHashMap<>(MAX_CONNECTIONS);

    public static int DEVICE_ID = 0;                                   //This device's ID
    private static FileOutputStream logfile;
    private static FileOutputStream packetlogfile;
    public static String rootDir = Environment.getExternalStorageDirectory().toString() + "/MobiBots/boatsFramework/tcp/";
    private WifiAdhocServerThread serverThread;
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

    public synchronized String getTimeStamp() {
        return (new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS").format(new Date()));
    }

    /**
     * Function that write 'message' to the log file
     * @param message
     */
    public synchronized void logLocation(String message) {
        StringBuilder log_message = new StringBuilder(26 + message.length());
        log_message.append(getTimeStamp());
        log_message.append(": ");
        log_message.append(message);
        log_message.append("\n");

        try {
            locationFile.write(log_message.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Function that write 'message' to the log file
     * @param message
     */
    public synchronized void log(String message) {
        StringBuilder log_message = new StringBuilder(26 + message.length());
        log_message.append(getTimeStamp());
        log_message.append(": ");
        log_message.append(message);
        log_message.append("\n");

        try {
            logfile.write(log_message.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

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


    /**
     * TODO: Don't forget to change this to a static Handler to prevent memory leak
     */
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



    public synchronized WorkerThread getWorkingThread(int nodeID){
        return connectionThreads[nodeID-1];
    }
    public synchronized void addRoute(int dest, Route route){
        PriorityQueue<Route> associatedRoutes = routeTable.get(dest);
        if(associatedRoutes == null){
            PriorityQueue<Route> newEntry = new PriorityQueue<>();
            newEntry.add(route);
            routeTable.put(dest, newEntry);
        }
        else{
            associatedRoutes.add(route);
        }
    }

    public String printRoutingTable(){
//        Log.d(TAG, "Dest\t| Routes ");
        String out = "Dest\t| Routes\n";

        for(Integer entry : routeTable.keySet()){
            out += entry.intValue()+"\t| ";
            PriorityQueue<Route> routes = routeTable.get(entry);
            for(Route route : routes){
                out += route+" | ";
            }
            out += "\n";
        }
        Log.d(TAG, out);
        return out;
    }
    public void removeRoute(int nodeID, int nextHop){
        PriorityQueue<Route> routes = routeTable.get(nodeID);
        if(routes != null){
            for(Route route : routes){
                if(route.getNextHop() == nextHop)
                    routes.remove(route);
            }

            if(routes.size() == 0){
                routeTable.remove(nodeID);
            }
        }
    }

    /**
     * Remove all routes associated with the current node
     * It will remove all routes where nodeID is the nextHop
     * Also it will remove entry of nodeID from routingTable
     * @param nodeID
     */
    public void removeRoutesAssociatedWithNode(int nodeID){
        routeTable.remove(nodeID);

        for(Integer entry : routeTable.keySet()){
            PriorityQueue<Route> routes = routeTable.get(entry);
            for(Route route : routes){
                if(route.getNextHop() == nodeID)
                    routes.remove(route);
            }

            if(routes.size() == 0){
                Log.d(TAG, "Removed "+entry.intValue()+" from routing table");
                routeTable.remove(entry);
            }
        }

        Log.d(TAG, "Removed "+nodeID+" from routing table");
    }

    public synchronized int findRoute(int nodeID){
        PriorityQueue<Route> routes = routeTable.get(nodeID);
        if (routes != null) {
            Route route = routes.peek();
            if(route == null)
                return -1;
            int nextHop = route.getNextHop();
//            debug("Packet to " + nodeID + " send it through " + nextHop);
            Log.i(TAG, "Packet to " + nodeID + " send it through " + nextHop);
            return nextHop;
        }
        return -1;
    }
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
        String dir_timeStamp = new SimpleDateFormat("yyyy_MM_dd").format(new Date());

        mediaStorageDir = new File(rootDir  + dir_timeStamp);
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "failed to create directory");
            }
        }




        String timeStamp = new SimpleDateFormat("yyyyMMdd_HH:mm:ss").format(new Date());
        File file = new File(rootDir  + dir_timeStamp + "/" + "Device_" +DEVICE_ID + "_" +timeStamp + ".txt");

        Log.d(TAG, file.getPath());
        try {
            file.createNewFile();
            logfile = new FileOutputStream(file);

            file = new File(rootDir  + dir_timeStamp+ "/" + "location.txt");
            if (!file.exists())
                file.createNewFile();
            locationFile = new FileOutputStream(file, true);


            file = new File(rootDir  + dir_timeStamp + "/" + "packets_device_" +DEVICE_ID + "_" +timeStamp);
            if (!file.exists()) {
                file.createNewFile();
                packetlogfile = new FileOutputStream(file, true);
                String header = "Time\t\tSource_IP\tDestination_IP\tSource\tDestination\tpacket_size\tpacket_type\n";
                packetlogfile.write(header.getBytes());

            }
            else
                packetlogfile = new FileOutputStream(file, true);




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
        log("Disconnecting from "+node);
        try{
            if(alarmRingtone.isPlaying())
                alarmRingtone.stop();
            alarmRingtone.play();
            timedTasks.postDelayed(stopAlarm, 4000);
        }
        catch (Exception e){
            e.printStackTrace();
        }
//        removeRoutesAssociatedWithNode(node);

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
    public synchronized void addNode(WorkerThread thread,int node){
        debug("Connected to "+node);
        log("Connected to "+node);
        try{
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();

        }
        catch (Exception e){
            e.printStackTrace();
        }
//        addRoute(node, new Route(node, 1));
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

    Runnable changeRoutingTable = new Runnable() {
        @Override
        public void run() {
            PriorityQueue<Route> queue = routeTable.get(9);
            for(Route route : queue){
                if(route.getNextHop() == 9)
                    queue.remove(route);
            }
        }
    };
    Runnable startStreaming = new Runnable() {
        @Override
        public void run() {
            connectionThreads[8].sendStream(9);
        }
    };
    Runnable stopStreaming = new Runnable() {
        @Override
        public void run() {
            connectionThreads[8].stopStream();
        }
    };

    /**
     * 7 -> 8 -> 9
     */
    public void implementStaticRouting(){
        switch (DEVICE_ID){
            case 6:
                //Reach node 7
                PriorityQueue<Route> entry = new PriorityQueue<>();
//                entry.add(new Route(7, 1));
//                routeTable.put(7, entry);

                //Reach node 8
                entry = new PriorityQueue<>();
                entry.add(new Route(7, 2));
                routeTable.put(8, entry);

                //Reach node 9
                entry = new PriorityQueue<>();
                entry.add(new Route(7, 3));
//                entry.add(new Route(9, 1));
                routeTable.put(9, entry);
                break;


            case 7:
                //Reach node 6
//                entry = new PriorityQueue<>();
//                entry.add(new Route(7, 1));
//                routeTable.put(6, entry);

                //Reach node 8
                entry = new PriorityQueue<>();
                entry.add(new Route(8, 1));
                routeTable.put(8, entry);

                //Reach node 9
                entry = new PriorityQueue<>();
                entry.add(new Route(8, 2));
//                entry.add(new Route(9, 1));
                routeTable.put(9, entry);
                break;


            case 8:
                //Reach node 6
//                entry = new PriorityQueue<>();
//                entry.add(new Route(7, 2));
//                routeTable.put(6, entry);

                //Reach node 7
                entry = new PriorityQueue<>();
                entry.add(new Route(7, 1));
                routeTable.put(7, entry);

                //Reach node 9
                entry = new PriorityQueue<>();
                entry.add(new Route(9, 1));
                routeTable.put(9, entry);
                break;


            case 9:
                //Reach node 6
//                entry = new PriorityQueue<>();
//                entry.add(new Route(8, 3));
////                entry.add(new Route(6, 1));
//                routeTable.put(6, entry);

                //Reach node 7
                entry = new PriorityQueue<>();
                entry.add(new Route(8, 2));
                routeTable.put(7, entry);

                //Reach node 8
                entry = new PriorityQueue<>();
                entry.add(new Route(8, 1));
                routeTable.put(8, entry);
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        mainActivity = this;
        alarmRingtone = RingtoneManager.getRingtone(this,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));

        WifiManager wifiMgr = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();

        DEVICE_ID = findDevice_Wifi(wifiInfo.getMacAddress());

        initLogging();

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
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            finish();
        }


        implementStaticRouting();

        timedTasks = new Handler();

        serverThread = new WifiAdhocServerThread(this);
        serverThread.start();

        //Start Advertising after 5 seconds
        timedTasks.postDelayed(startAdvertising, 5000);


        new CommandsThread().start();

//        if(DEVICE_ID == 7) {
//            timedTasks.postDelayed(startStreaming, (40000));
//            timedTasks.postDelayed(changeRoutingTable, (30000 * 2));
//            timedTasks.postDelayed(stopStreaming, (20000*4));
//        }
        TextView view = (TextView) findViewById(R.id.textView2);
        view.setMovementMethod(new ScrollingMovementMethod());


//        if(DEVICE_ID == 8){
//            WifiAdhocClientThread clientThread = new WifiAdhocClientThread(device_ip_adresses[8]);
//            clientThread.start();
//        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        // Hook up to the GPS system
        LocationManager gps = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria c = new Criteria();
        c.setAccuracy(Criteria.ACCURACY_FINE);
        c.setPowerRequirement(Criteria.NO_REQUIREMENT);
        String provider = gps.getBestProvider(c, false);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        gps.requestLocationUpdates(provider, 0, 0, locationListener);
    }

    /**
     * Handles GPS updates by calling the appropriate update.
     */
    private LocationListener locationListener = new LocationListener() {
        public void onStatusChanged(String provider, int status, Bundle extras) {

            String a = String.format("onStatusChanged: provider = %s, status= %d", provider, status);
            Log.w(TAG, a);
        }

        public void onProviderEnabled(String provider) {
            Log.w(TAG, "onProviderEnabled");
        }

        public void onProviderDisabled(String provider) {
        }

        public void onLocationChanged(Location location) {
// Convert from lat/long to UTM coordinates
            debug("Current Location: " + location);
            String out = "";
            out += location.getLatitude() + "\t" + location.getLongitude() + "\t" + location.getAltitude();

            if(location.hasSpeed())
                out += "\t" + location.getSpeed();
            else
                out += "\t0.0";

            if(location.hasBearing())
                out += "\t" + location.getBearing();
            else
                out += "\t0.0";

            currentLocation = location;

            logLocation(out);


        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Disconnect from GPS updates
        LocationManager gps;
        gps = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        gps.removeUpdates(locationListener);

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

    Runnable printRoutingTable = new Runnable() {
        @Override
        public void run() {
            sendToCommandCenter(printRoutingTable());
            timedTasks.postDelayed(this, 2000);
        }
    };

    public void sendToCommandCenter(String msg){
        if(commandCenterOutputStream != null)
            try {
                commandCenterOutputStream.write((msg + "\n\r").getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
    }


    private class SendFileRunnable implements Runnable{
        int nodeID;
        public SendFileRunnable(int nodeID){
            this.nodeID = nodeID;
        }
        @Override
        public void run() {
            int nextHop = findRoute(nodeID);
            debug("Sending file to "+nodeID+" via "+nextHop);
            if(nextHop != -1) {
                WorkerThread thread = mainActivity.getWorkingThread(nextHop);
                if(thread != null){
                    thread.sendFile(nodeID, "5MB");
                }
            }
        }
    }

    public class CommandsThread extends Thread{

        ServerSocket serverSocket = null;
        boolean serverOn = true;
        Socket client = null;

        public CommandsThread() {

            try {
                serverSocket = new ServerSocket(8888);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void writeInstructions(OutputStream outputStream){
            String out = "\t\t*****WELCOME to the command center for Scenario 2 Experiment*****\n" +
                    "Usage: <command>\n" +
                    "command\tDescription\n" +
                    "send <NodeID> <Filename>\tSend <Filename> to <NodeID>\n" +
                    "remove <DestID> <NextHop>\tRemove route (DestID, NextHop) from routing table\n" +
                    "\n\r";
            try {
                outputStream.write(out.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            timedTasks.postDelayed(printRoutingTable, 2000);
            while(serverOn){
                try {
                    client = serverSocket.accept();
                    Log.d(TAG, "Connected to: " + client.getInetAddress().getHostAddress());
                    Log.d(TAG, "Connected to Local Address: " + client.getLocalAddress().getHostAddress());


                    commandCenterOutputStream= client.getOutputStream();
                    writeInstructions(commandCenterOutputStream);

                    InputStream inputStream = client.getInputStream();
                    int len;
                    byte[] buf = new byte[1024];
                    commandCenterOutputStream.write("Type in your command: ".getBytes());
                    while((len = inputStream.read(buf)) > 0){
                        String newCommand = new String(buf, 0, len);
                        Log.d(TAG, "COMMAND: "+newCommand);
                        sendToCommandCenter("Your command was: "+newCommand);
                        Scanner lineScanner = new Scanner(newCommand);

                        if(newCommand.contains("send")){
                            lineScanner.next();
                            if(lineScanner.hasNextInt()) {
                                int node = lineScanner.nextInt();
//                            lineScanner.next();
                                SendFileRunnable sendFile = new SendFileRunnable(node);
                                Thread thread = new Thread(sendFile);
                                thread.start();
                            }
                            else{
                                sendToCommandCenter("Invalid command");
                                lineScanner.nextLine();
                            }

                        }

                        else if(newCommand.contains("remove")){
                            lineScanner.next();
                            if(!lineScanner.hasNextInt())
                            {
                                sendToCommandCenter("You didn't type in a destination node and nextHop");
                                continue;
                            }
                            int destNode = lineScanner.nextInt();
                            if(!lineScanner.hasNextInt())
                            {
                                sendToCommandCenter("You didn't type in a destination node");
                                continue;
                            }
                            int nextHop = lineScanner.nextInt();
                            removeRoute(destNode, nextHop);
                        }

                        else if(newCommand.contains("add")){
                            lineScanner.next();
                            if(!lineScanner.hasNextInt())
                            {
                                sendToCommandCenter("add <DestID> <NextHop> <cost>");
                                continue;
                            }
                            int destNode = lineScanner.nextInt();
                            if(!lineScanner.hasNextInt())
                            {
                                sendToCommandCenter("You didn't type in a nextHop and cost");
                                continue;
                            }
                            int nextHop = lineScanner.nextInt();
                            int cost = lineScanner.nextInt();
                            addRoute(destNode, new Route(nextHop, cost));
                        }
                        else if(newCommand.contains("rm")){
                            File f = new File((MainActivity.rootDir + "File/" +"5MB"));

                            if(f.exists()) {
                                log("File size before deleting: "+f.length());
                                f.delete();
                                sendToCommandCenter(MainActivity.rootDir + "File/5MB" + " has successfully been deleted");
                            }
                        }
                        else if(newCommand.contains("del")){
                            lineScanner.next();
                            if(lineScanner.hasNextInt()) {
                                int node = lineScanner.nextInt();
//                            lineScanner.next();
                                int nextHop = findRoute(node);
                                debug("Sending DELETE Packet to "+node+" via "+nextHop);
                                if(nextHop != -1) {
                                    WorkerThread thread = mainActivity.getWorkingThread(nextHop);
                                    if(thread != null){
                                        thread.sendDeletePacket(node);
                                    }
                                }
                            }
                            else{
                                sendToCommandCenter("Invalid command");
                                lineScanner.nextLine();
                            }
                        }
//                        if(newCommand.contains("new")){
//                            lineScanner.next();
//                            int distance = lineScanner.nextInt();
//                            new_Experiment(distance);
//                        }
//                        else if(newCommand.contains("tput")){
//                            lineScanner.nextLine();
//                            if(Distance == -1){
//                                commandCenterOutputStream.write("Please type new <Distance> first\n\r".getBytes());
//                            }else {
//
//                                commandCenterOutputStream.write(("Running tput for " + (Distance) + " meters\n\r").getBytes());
//                                calc_throughput();
//                            }
//                        }
//                        else if(newCommand.contains("loss")){
//                            lineScanner.nextLine();
//                            if(Distance == -1){
//                                commandCenterOutputStream.write("Please type new <Distance> first\n\r".getBytes());
//                            }else {
//                                commandCenterOutputStream.write(("Running packet loss for " + (Distance) + " meters\n\r").getBytes());
//                                packet_loss();
//                            }
//                        }
//                        else if(newCommand.contains("rtt")){
//                            lineScanner.nextLine();
//                            if(Distance == -1){
//                                commandCenterOutputStream.write("Please type new <Distance> first\n\r".getBytes());
//                            }else {
//                                commandCenterOutputStream.write(("Running rtt for " + (Distance) + " meters\n\r").getBytes());
//                                RTT();
//                            }
//                        }
                        else{
                            debug("Invalid command");
                            commandCenterOutputStream.write((lineScanner.nextLine() +
                                    "\tNOT SUPPORTED\n\r").getBytes());
                        }

                        commandCenterOutputStream.write("Type in your command: ".getBytes());
                    }
                }catch(IOException e){
                    cancel();
                    e.printStackTrace();
                }
            }
        }


        public void cancel(){
            try {
                serverOn = false;
                serverSocket.close();
                if(client!=null)
                    client.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void startCommunicationThreads(int nodeID, boolean client,
                                          InputStream inputStream, OutputStream outputStream,
                                          MainActivity mainActivity){


        BlockingQueue<Packet> incomingPackets = new LinkedBlockingQueue<>();
        BlockingQueue<Packet> outgoingPackets = new LinkedBlockingQueue<>();

        ReadingThread readingThread = new ReadingThread(incomingPackets, inputStream, nodeID,
                mainActivity);

        WritingThread writingThread = new WritingThread(outgoingPackets, outputStream, nodeID);
        WorkerThread workerThread = new WorkerThread(incomingPackets, outgoingPackets, nodeID,
                mainActivity, writingThread);

        readingThread.start();
        workerThread.start();
        writingThread.start();


        addNode(workerThread, nodeID);
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

                    debug("SERVER Connected to "+deviceId+" "+client.getInetAddress().getHostAddress());
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
                sendToCommandCenter("Connected to "+(i+1));
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
                socket.connect((new InetSocketAddress(hostAddress, port)), 500);

                debug("[CLIENT] Connected to "+device_Id+" "+hostAddress);

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
                        if(deviceID > DEVICE_ID) {
                            if (connectionThreads[deviceID - 1] == null) {
                                new WifiAdhocClientThread(device_ip_adresses[deviceID - 1]).start();
                            }
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

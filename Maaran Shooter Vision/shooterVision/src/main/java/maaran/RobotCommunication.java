package maaran;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Created by Robotics on 1/18/2017.
 */

public class RobotCommunication {
    public final int robotPort = 8254;
    public final String robotProxy = "localhost";
    private final String TAGOne = "Communication";

    public static String messageToSend = null;
    private String oldMessageToSend = messageToSend;

    private boolean running = false;

    private int port;
    private String host;

    volatile private Socket socket;

    private Thread connectThread, writeThread;

    protected class WriteThread implements Runnable{
        @Override
        public void run(){
            if(messageToSend!=null && messageToSend != oldMessageToSend){
                sendToWire(messageToSend);
            }
        }
    }

    public class ConnectionMonitor implements Runnable {
        @Override
        public void run(){
            Log.d(TAGOne, "Connection Monitor");
            while (running){
                try {
                    tryConnect();
                    Thread.sleep(250, 0);
                }
                catch (InterruptedException e){
                    Log.d(TAGOne, "Cannot send");
                }
            }
        }
    }

    synchronized private void tryConnect() {
        Log.d(TAGOne, "Trying to connect");
        if(socket == null){
            try {
                socket = new Socket(host, port);
                socket.setSoTimeout(100);
            }
            catch (IOException e){
                socket = null;
            }
        }
    }

    synchronized public void start(){
        Log.d(TAGOne, "Started");
        running = true;
        if(writeThread == null || !writeThread.isAlive()){
           writeThread = new Thread(new WriteThread());
            writeThread.start();
        }

        if(connectThread == null || !connectThread.isAlive()) {
            connectThread = new Thread(); //add connect thread
            connectThread.start();
        }
    }

    private synchronized boolean sendToWire(String message){
        Log.d(TAGOne, "Trying to send to wire");
        if(socket != null && socket.isConnected()){
            try {
                OutputStream os = socket.getOutputStream();
                os.write(message.getBytes());
                return true;
            } catch (IOException e){
                Log.d(TAGOne,"failed to send to socket");
                socket = null;
            }
        }
        return false; //look at 444
    }

}
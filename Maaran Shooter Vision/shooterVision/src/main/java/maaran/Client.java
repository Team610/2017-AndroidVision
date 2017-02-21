package maaran;


import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class Client extends AsyncTask<Socket, Void, Socket>{
    String host = "localhost";
    int port = 3000;
    String oldMessage;

    Client(){
    }

    @Override
    protected Socket doInBackground(Socket... arg) {
        Socket socket = arg[0];
        Log.d("TestSend", "Sending");
        try {
            Log.d("TestSend", "Sending");
            if (socket == null) {
                socket = new Socket(host, port);
                socket.setSoTimeout(100);
            } else if(messageToSend.message!=null && messageToSend.message != oldMessage){
                if(!socket.isConnected())
                    socket = new Socket(host, port);
                Log.d("TestSend", "Sending");
                OutputStream os = socket.getOutputStream();
                os.write(messageToSend.message.getBytes());
                oldMessage = messageToSend.message;
            }
        } catch (IOException e) {
            ColorBlobDetectionActivity.clientRun = false;
        }
        return socket;
    }

    @Override
    protected void onPostExecute(Socket result){
        ColorBlobDetectionActivity.runClient(result);
    }
}
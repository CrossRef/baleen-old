package wikipediawatcher;

import io.socket.*;
import java.net.URISyntaxException;
import java.net.MalformedURLException;
import java.util.*;
import org.json.*;
import clojure.lang.IFn;

public class Client {
	private IFn callback;

	public Client(IFn callback) {
		this.callback = callback;
	}
	
	public void run() throws URISyntaxException, MalformedURLException {
		SocketIO socket = new SocketIO("http://stream.wikimedia.org/rc");
        socket.connect(
        	new IOCallback() {
            @Override
            public void onMessage(JSONObject json, IOAcknowledge ack) {
            }

            @Override
            public void onMessage(String data, IOAcknowledge ack) {
            }

            @Override
            public void onError(SocketIOException socketIOException) {
                System.out.println("an Error occured");
                socketIOException.printStackTrace();
            }

            @Override
            public void onDisconnect() {
                System.out.println("Connection terminated.");
            }

            @Override
            public void onConnect() {
                System.out.println("Connection established");
                socket.emit("subscribe", "*");
            }

            @Override
            public void on(String event, IOAcknowledge ack, Object... args) {
                Client.this.callback.invoke(event, args);
            }
        });
	}
}
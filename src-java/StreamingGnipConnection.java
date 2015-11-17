package baleen;

// import sun.misc.BASE64Encoder;
import javax.xml.bind.DatatypeConverter;

import java.io.*;
import java.lang.String;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import clojure.lang.IFn;

// Based on https://github.com/gnip/support
public class StreamingGnipConnection {
  private String username;
  private String password;
  private String streamUrl;
  private String charset;
  private IFn callback;

  public StreamingGnipConnection(String username, String password, String streamUrl, IFn callback) {
    this.username = username;
    this.password = password;
    this.streamUrl = streamUrl;
    this.charset = "UTF-8";
    this.callback = callback;
  }

  public void run() throws IOException {
    HttpURLConnection connection = null;
    InputStream inputStream = null;

    try {
      connection = getConnection(this.streamUrl, this.username, this.password);

      inputStream = connection.getInputStream();
      int responseCode = connection.getResponseCode();

      if (responseCode >= 200 && responseCode <= 299) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new StreamingGZIPInputStream(inputStream), charset));
        String line = reader.readLine();

        while(line != null){
          System.out.println(line);
          
          this.callback.invoke(line);

          line = reader.readLine();
        }
      } else {
        handleNonSuccessResponse(connection);
      }
    } catch (Exception e) {
      e.printStackTrace();
      if (connection != null) {
        handleNonSuccessResponse(connection);
      }
    } finally {
      if (inputStream != null) {
        inputStream.close();
      }
    }
  }

  private static void handleNonSuccessResponse(HttpURLConnection connection) throws IOException {
    int responseCode = connection.getResponseCode();
    String responseMessage = connection.getResponseMessage();
    System.out.println("Non-success response: " + responseCode + " -- " + responseMessage);
  }

  private static HttpURLConnection getConnection(String urlString, String username, String password) throws IOException {
    URL url = new URL(urlString);

    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setReadTimeout(1000 * 60 * 60);
    connection.setConnectTimeout(1000 * 10);

    connection.setRequestProperty("Authorization", createAuthHeader(username, password));
    connection.setRequestProperty("Accept-Encoding", "gzip");

    return connection;
  }

  private static String createAuthHeader(String username, String password) throws UnsupportedEncodingException {
    // BASE64Encoder encoder = new BASE64Encoder();
    String authToken = DatatypeConverter.printBase64Binary((username + ":" + password).getBytes());

    // String authToken = username + ":" + password;
    // //encoder.encode(authToken.getBytes());
    return "Basic " + authToken; 
  }
}
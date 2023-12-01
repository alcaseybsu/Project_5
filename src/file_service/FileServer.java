package file_service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class FileServer {

  private static final int STATUS_CODE_LENGTH = 1;
  private static final String RELATIVE_PATH = "TestFiles/";
  private static final String BASE_PATH =
    System.getProperty("user.home") + "\\git_repo\\Project_5\\" + RELATIVE_PATH;

  public static void main(String[] args) throws Exception {
    int port = 3000;
    ServerSocketChannel welcomeChannel = ServerSocketChannel.open();
    welcomeChannel.socket().bind(new InetSocketAddress(port));
    while (true) {
      SocketChannel serveChannel = welcomeChannel.accept();
      ByteBuffer request = ByteBuffer.allocate(2500);
      int numBytes = 0;
      do {
        numBytes = serveChannel.read(request);
      } while (numBytes >= 0);

      //while(serveChannel.read(request) >= 0);

      //new
      request.flip();
      char command = (char) request.get();
      System.out.println("received command: " + command);
      switch (command) {
        ///////////////////////////////////////////////////////////////F
        case 'D':
          {
            byte[] a = new byte[request.remaining()];
            request.get(a);
            String fileName = new String(a);

            // Print statement added
            System.out.println("Server: Deleting file '" + fileName + "'...");

            String filePath = BASE_PATH + fileName;
            File file = new File(filePath);
            boolean success = false;

            if (file.exists()) {
              success = file.delete();
            }

            if (success) {
              System.out.println(
                "Server: File '" + fileName + "' deleted successfully."
              );
              ByteBuffer code = ByteBuffer.wrap("S".getBytes());
              serveChannel.write(code);
            } else {
              System.out.println(
                "Server: File '" +
                fileName +
                "' does not exist or couldn't be deleted."
              );
              ByteBuffer code = ByteBuffer.wrap("F".getBytes());
              serveChannel.write(code);
            }
            serveChannel.close();
            break;
          }
        ///////////////////////////////////////////////////////////////
        case 'L':
          {
            File serverDirectory = new File(BASE_PATH);
            File[] files = serverDirectory.listFiles();

            ByteBuffer response = ByteBuffer.allocate(2500);

            if (files != null && files.length > 0) {
              response.put((byte) 'S'); // Success
              for (File singleFile : files) {
                byte[] fileNameBytes = singleFile
                  .getName()
                  .getBytes(StandardCharsets.UTF_8);
                response.putInt(fileNameBytes.length);
                response.put(fileNameBytes);
              }
            } else {
              response.put((byte) 'F'); // Failure
            }

            response.flip();
            serveChannel.write(response);
            serveChannel.close();
            break;
          }
        ///////////////////////////////////////////////////////////////
        case 'R':
          {
            int currentFileNameLength = request.getInt();
            byte[] currentFileNameBytes = new byte[currentFileNameLength];
            request.get(currentFileNameBytes);
            String currentFileName = new String(
              currentFileNameBytes,
              StandardCharsets.UTF_8
            );

            int newFileNameLength = request.getInt();
            byte[] newFileNameBytes = new byte[newFileNameLength];
            request.get(newFileNameBytes);
            String newFileName = new String(
              newFileNameBytes,
              StandardCharsets.UTF_8
            );

            // Print statement added
            System.out.println(
              "Server: Renaming file '" +
              currentFileName +
              "' to '" +
              newFileName +
              "'..."
            );

            File currentFile = new File(BASE_PATH + currentFileName);
            File newFile = new File(BASE_PATH + newFileName);

            ByteBuffer response = ByteBuffer.allocate(STATUS_CODE_LENGTH);

            if (
              currentFile.exists() &&
              !newFile.exists() &&
              currentFile.renameTo(newFile)
            ) {
              response.put((byte) 'S'); // Success
            } else {
              response.put((byte) 'F'); // Failure
            }

            response.flip();
            serveChannel.write(response);
            serveChannel.close();
            break;
          }
        ///////////////////////////////////////////////////////////////
        case 'G':
          {
            byte[] fileNameBytes = new byte[request.get()];
            request.get(fileNameBytes);
            String requestedFileName = new String(
              fileNameBytes,
              StandardCharsets.UTF_8
            );
            File serverFile = new File("ServerFiles/" + requestedFileName);

            ByteBuffer responseCode = ByteBuffer.allocate(STATUS_CODE_LENGTH);
            ByteBuffer fileContent = ByteBuffer.allocate(2500);

            if (serverFile.exists()) {
              responseCode.put((byte) 'S'); // Success
              responseCode.flip();
              serveChannel.write(responseCode);

              // Send the file content to the client
              try (FileInputStream fis = new FileInputStream(serverFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) > 0) {
                  fileContent.put(buffer, 0, bytesRead);
                  fileContent.flip();
                  serveChannel.write(fileContent);
                  fileContent.clear();
                }
              }
            } else {
              responseCode.put((byte) 'F'); // Failure
              responseCode.flip();
              serveChannel.write(responseCode);
            }

            serveChannel.close();
            break;
          }
        ///////////////////////////////////////////////////////////////
        case 'U':
          {
            byte[] fileNameBytes = new byte[request.remaining()];
            request.get(fileNameBytes);
            String uploadedFileName = new String(fileNameBytes);

            // Read the file content
            ByteBuffer fileContentBuffer = ByteBuffer.allocate(2500);
            serveChannel.read(fileContentBuffer);
            fileContentBuffer.flip();
            byte[] fileContent = new byte[fileContentBuffer.remaining()];
            fileContentBuffer.get(fileContent);

            // Save the file on the server
            try (
              FileOutputStream fos = new FileOutputStream(
                "ServerFiles/" + uploadedFileName
              )
            ) {
              fos.write(fileContent);
            }

            // Send success response to the client
            ByteBuffer code = ByteBuffer.wrap("S".getBytes());
            serveChannel.write(code);
            serveChannel.close();
            break;
          }
        ///////////////////////////////////////////////////////////////
      }
    }
  }
}

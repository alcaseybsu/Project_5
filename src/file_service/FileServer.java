package file_service;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileServer {

  private static final String BASE_PATH =
    System.getProperty("user.dir") + "\\TestFiles\\";

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
        // delete a file from the server
        case 'D':
          {
            byte[] d = new byte[request.remaining()];
            request.get(d);
            String fileToDelete = new String(d);
            System.out.println("file to delete: " + fileToDelete);

            // Construct the absolute path to the file
            String filePath = BASE_PATH + fileToDelete;

            File file = new File(filePath);
            boolean success = false;

            // check if file exists before attempting to delete
            if (file.exists()) {
              // Check if the file is empty
              if (file.length() == 0) {
                success = file.delete();
              } else {
                // File is not empty, but still considered a success if deleted
                success = file.delete();
              }
            }

            if (success) {
              ByteBuffer dCode = ByteBuffer.wrap("S".getBytes());
              serveChannel.write(dCode);
            } else {
              ByteBuffer dCode = ByteBuffer.wrap("F".getBytes());
              serveChannel.write(dCode);
            }

            serveChannel.close();
            break;
          }
        case 'L':
          String basePath =
            System.getProperty("user.dir") + File.separator + "TestFiles";
          File directory = new File(basePath);

          File[] files = directory.listFiles();

          if (files != null) {
            StringBuilder fileList = new StringBuilder();
            for (File fileInDirectory : files) {
              fileList.append(fileInDirectory.getName()).append("\n");
            }

            ByteBuffer fileListBuffer = ByteBuffer.wrap(
              fileList.toString().getBytes()
            );
            serveChannel.write(fileListBuffer);
          } else {
            ByteBuffer listCode = ByteBuffer.wrap("F".getBytes());
            serveChannel.write(listCode);
          }

          serveChannel.close();
          break;
        case 'R':
          {
            // Extract current file name
            byte[] currentFileNameBytes = new byte[request.getInt()];
            request.get(currentFileNameBytes);
            String currentFileName = new String(
              currentFileNameBytes,
              StandardCharsets.UTF_8
            );

            // Extract new file name
            byte[] newFileNameBytes = new byte[request.getInt()];
            request.get(newFileNameBytes);
            String newFileName = new String(
              newFileNameBytes,
              StandardCharsets.UTF_8
            );

            // Print added for debugging
            System.out.println(
              "Server: Renaming file '" +
              currentFileName +
              "' to '" +
              newFileName +
              "'..."
            );

            // Construct the absolute path to the current file
            String currentFilePath = BASE_PATH + currentFileName;

            // Construct the absolute path to the new file
            String newFilePath = BASE_PATH + newFileName;

            File currentFile = new File(currentFilePath);

            if (currentFile.exists()) {
              // Rename the file
              File newFile = new File(newFilePath);
              if (currentFile.renameTo(newFile)) {
                ByteBuffer renameCode = ByteBuffer.wrap("S".getBytes());
                serveChannel.write(renameCode);
              } else {
                ByteBuffer renameCode = ByteBuffer.wrap("F".getBytes());
                serveChannel.write(renameCode);
              }
            } else {
              // File to rename not found
              ByteBuffer renameCode = ByteBuffer.wrap("F".getBytes());
              serveChannel.write(renameCode);
            }

            serveChannel.close();
            break;
          }
        // download a file from the server
        case 'G':
          {
            byte[] g = new byte[request.remaining()];
            request.get(g);
            String fileToDownload = new String(g);
            System.out.println("file to download: " + fileToDownload);

            // Construct the absolute path to the file on the server
            String filePath = BASE_PATH + fileToDownload;

            File file = new File(filePath);

            if (file.exists()) {
              if (file.length() == 0) {
                // File is empty, send an 'F' code
                ByteBuffer gCode = ByteBuffer.wrap("F".getBytes());
                serveChannel.write(gCode);
              } else {
                // Send the file content to the client
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                ByteBuffer fileContent = ByteBuffer.wrap(fileBytes);
                serveChannel.write(fileContent);
              }
            } else {
              // Notify the client that the file is not found
              ByteBuffer gCode = ByteBuffer.wrap("F".getBytes());
              serveChannel.write(gCode);
            }

            serveChannel.close();
            break;
          }
        // Existing code...

        case 'U':
          {
            // Extract file path
            byte[] filePathBytes = new byte[request.getInt()];
            request.get(filePathBytes);
            String filePath = new String(filePathBytes, StandardCharsets.UTF_8);

            // Read file content
            byte[] fileContent = new byte[request.remaining()];
            request.get(fileContent);

            // Construct the absolute path to save the file on the server
            String savePath = BASE_PATH + filePath;

            try {
              Files.write(Paths.get(savePath), fileContent);

              // Send success code to the client
              ByteBuffer uploadCode = ByteBuffer.wrap("S".getBytes());
              serveChannel.write(uploadCode);
            } catch (IOException e) {
              // Send failure code to the client
              ByteBuffer uploadCode = ByteBuffer.wrap("F".getBytes());
              serveChannel.write(uploadCode);
            }

            serveChannel.close();
            break;
          }
      }
    }
  }
}

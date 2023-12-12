package file_service;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileClient {

  private static final ExecutorService executorService = Executors.newFixedThreadPool(
    3
  );
  private static final String SERVER_HOST = "localhost";
  private static final int SERVER_PORT = 3000;

  public static void main(String[] args) throws Exception {
    // start new thread that waits for user commands
    new Thread(() -> {
      try (Scanner scanner = new Scanner(System.in)) {
        while (true) {
          System.out.println("Please enter a command (Q to quit):");
          String command = scanner.nextLine();
          if ("Q".equals(command)) {
            executorService.shutdown();
            System.exit(0);
          } else if ("D".equals(command)) {
            System.out.println("Please enter the name of the file to delete:");
            String fileToDelete = scanner.nextLine();
            final String deleteCommand = command + " " + fileToDelete;
            executorService.submit(() -> {
              try {
                handleCommand(deleteCommand);
              } catch (IOException e) {
                e.printStackTrace();
              }
            });
          } else if ("R".equals(command)) {
            System.out.println(
              "Please enter the current name of the file to rename:"
            );
            String currentFileName = scanner.nextLine();
            System.out.println("Please enter the new name for the file:");
            String newFileName = scanner.nextLine();
            final String renameCommand =
              command + " " + currentFileName + " " + newFileName;
            executorService.submit(() -> {
              try {
                handleCommand(renameCommand);
              } catch (IOException e) {
                e.printStackTrace();
              }
            });
          } else if ("G".equals(command)) {
            System.out.println("Please enter the name of the file to get:");
            String fileToGet = scanner.nextLine();
            final String getCommand = command + " " + fileToGet;
            executorService.submit(() -> {
              try {
                handleCommand(getCommand);
              } catch (IOException e) {
                e.printStackTrace();
              }
            });
          } else if ("U".equals(command)) {
            System.out.println("Please enter the name of the file to upload:");
            String fileToUpload = scanner.nextLine();
            final String uploadCommand = command + " " + fileToUpload;
            executorService.submit(() -> {
              try {
                handleCommand(uploadCommand);
              } catch (IOException e) {
                e.printStackTrace();
              }
            });
          }
        }
      }
    })
      .start();
  }

  private static void handleCommand(String command) throws IOException {
    try (
      SocketChannel clientChannel = SocketChannel.open(
        new InetSocketAddress(SERVER_HOST, SERVER_PORT)
      )
    ) {
      ByteBuffer request = ByteBuffer.allocate(1024);
      request.put((byte) command.charAt(0));

      String[] commandParts = command.split(" ");
      String fileName = commandParts[1];
      request.putInt(fileName.length());
      request.put(fileName.getBytes(StandardCharsets.UTF_8));

      if (command.charAt(0) == 'R') {
        String newFileName = commandParts[2];
        request.putInt(newFileName.length());
        request.put(newFileName.getBytes(StandardCharsets.UTF_8));
      } else if (command.charAt(0) == 'U') {
        String desktopPath =
          System.getProperty("user.home") + File.separator + "Desktop";
        byte[] fileContent = Files.readAllBytes(
          Paths.get(desktopPath, fileName)
        );
        request.putInt(fileContent.length);
        request.put(fileContent);
      }

      request.flip();
      clientChannel.write(request);

      ByteBuffer response = ByteBuffer.allocate(1024);
      while (clientChannel.read(response) > 0) {
        // Keep reading until there's no more data
      }
      response.flip();

      switch (command.charAt(0)) {
        case 'D':
        case 'R':
        case 'U':
          char deleteRenameUploadResult = (char) response.get();
          if (deleteRenameUploadResult == 'S') {
            System.out.println("Operation successful.");
          } else {
            System.out.println("Operation failed.");
          }
          break;
        case 'G':
          byte[] fileContent = new byte[response.remaining()];
          response.get(fileContent);

          // Write the file content to a file on the user's desktop
          String desktopPath =
            System.getProperty("user.home") + File.separator + "Desktop";
          Path filePath = Paths.get(desktopPath, fileName);
          Files.write(filePath, fileContent);

          System.out.println("File downloaded to desktop: " + fileName);
          break;
        case 'L':
          int fileListLength = response.getInt(); // Read the length of the file list
          byte[] fileListBytes = new byte[fileListLength];
          response.get(fileListBytes);
          System.out.println(
            "File list: " + new String(fileListBytes, StandardCharsets.UTF_8)
          );
          break;
        default:
          System.out.println("Unknown command: " + command.charAt(0));
          break;
      }
    }
  }
}

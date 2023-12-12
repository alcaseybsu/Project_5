package file_service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

      switch (command.charAt(0)) {
        case 'D':
        case 'R':
        case 'G':
        case 'U':
          String fileName = command.substring(2);
          request.putInt(fileName.length());
          request.put(fileName.getBytes(StandardCharsets.UTF_8));

          // If the command is 'U', read the file from the user's desktop
          if (command.charAt(0) == 'U') {
            String desktopPath =
              System.getProperty("user.home") + File.separator + "Desktop";
            byte[] fileContent = Files.readAllBytes(
              Paths.get(desktopPath, fileName)
            );
            request.putInt(fileContent.length);
            request.put(fileContent);
          }
          break;
        case 'L':
          // No additional data needed for 'L' command
          break;
        default:
          System.out.println("Unknown command: " + command.charAt(0));
          return;
      }

      request.flip();
      clientChannel.write(request);

      ByteBuffer response = ByteBuffer.allocate(1024);
      clientChannel.read(response);
      response.flip();

      String fileName = command.substring(2);

      switch (command.charAt(0)) {
        case 'D':
        case 'R':
        case 'U':
          byte deleteRenameUploadResult = response.get();
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
          String desktopPath = System.getProperty("user.home") + File.separator + "Desktop";
          Path filePath = Paths.get(desktopPath, fileName);
          Files.write(filePath, fileContent);

          System.out.println("File downloaded to desktop: " + fileName);
          break;
        case 'L':
          byte[] fileListBytes = new byte[response.remaining()];
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
